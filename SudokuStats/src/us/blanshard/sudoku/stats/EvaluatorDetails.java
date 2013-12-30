/*
Copyright 2013 Luke Blanshard

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package us.blanshard.sudoku.stats;

import static com.google.common.base.Preconditions.checkNotNull;
import static us.blanshard.sudoku.insight.Evaluator.kindForInsight;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumSet;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.UndoDetector;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Analyzer.StopException;
import us.blanshard.sudoku.insight.BarredLoc;
import us.blanshard.sudoku.insight.BarredNum;
import us.blanshard.sudoku.insight.Conflict;
import us.blanshard.sudoku.insight.Evaluator.MoveKind;
import us.blanshard.sudoku.insight.ForcedLoc;
import us.blanshard.sudoku.insight.ForcedNum;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Implication;
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.Insight.Type;
import us.blanshard.sudoku.insight.LockedSet;
import us.blanshard.sudoku.insight.Overlap;
import us.blanshard.sudoku.stats.Pattern.UnitCategory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

/**
 * Compares sudoku game histories against the {@link Evaluator}, produces a TSV
 * file for analysis.
 */
public class EvaluatorDetails implements Runnable {

  public static void main(String[] args) throws Exception {
    PrintWriter out = new PrintWriter("details.tsv");
    out.println("Timestamp\tMove number\tActual minutes\tPredicted minutes\tPattern");
    int npuzzles = 0;

    for (AttemptInfo attempt : Attempts.datastoreBackup()) {
      ++npuzzles;
      new EvaluatorDetails(attempt.clues, attempt.history, out).run();
      System.err.print('.');
      if (npuzzles % 100 == 0) System.err.println();
      out.flush();
    }

    out.close();
  }

  private final Grid solution;
  private final Sudoku game;
  private final List<Move> history;
  private final UndoDetector undoDetector;
  private final Multimap<Integer, Location> mistakes = HashMultimap.create();
  private final PrintWriter out;

  private EvaluatorDetails(Grid puzzle, List<Move> history, PrintWriter out) {
    Solver.Result result = Solver.solve(puzzle, 10, new Random());
    this.solution = checkNotNull(result.intersection);
    this.game = new Sudoku(puzzle).resume();
    this.history = history;
    this.undoDetector = new UndoDetector(game);
    this.out = out;
  }

  @Override public void run() {
    long prevTime = 0;
    Numeral prevNumeral = null;
    for (int moveNumber = 0; moveNumber < history.size(); ++moveNumber) {
      Move move = history.get(moveNumber);
      applyMove(move, moveNumber, prevTime, prevNumeral);
      prevTime = move.timestamp;
      prevNumeral = move.getNumeral();
    }
  }

  private void applyMove(Move move, int moveNumber, long prevTime, @Nullable Numeral prevNumeral) {
    noteMistakes(move);
    if (move instanceof Move.Set
        && !undoDetector.isUndoOrRedo(move)
        && !isApparentCorrection(move)) {
      Grid grid = game.getState(move.trailId).getGrid();
      if (grid.containsKey(move.getLocation()))
        grid = grid.toBuilder().remove(move.getLocation()).build();
      GridMarks gridMarks = new GridMarks(grid);
      Collector collector = new Collector(gridMarks, move.getAssignment(), prevNumeral);
      Analyzer.analyze(gridMarks, collector, true);
      long elapsedMs = move.timestamp - prevTime;
      try {
        out.append(String.valueOf(move.timestamp))
            .append('\t')
            .append(String.valueOf(moveNumber))
            .append('\t')
            .append(String.valueOf(elapsedMs / 60000.0))
            .append('\t')
            .append(String.valueOf(collector.getPredictedMinutes()))
            .append('\t')
            ;
        if (collector.pattern != null)
          collector.pattern.appendTo(out);
        out.println();
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
    game.move(move);
  }

  private void noteMistakes(Move move) {
    if (solution.containsKey(move.getLocation())
        && solution.get(move.getLocation()) != move.getNumeral()) {
      mistakes.put(move.trailId, move.getLocation());
    }
  }

  private boolean isApparentCorrection(Move move) {
    return mistakes.containsEntry(move.trailId, move.getLocation());
  }

  private class Collector implements Analyzer.Callback {
    final GridMarks gridMarks;
    final Assignment assignment;
    final Numeral prevNumeral;
    Insight move = null;
    Pattern pattern = null;
    final LocSet locTargets = new LocSet();
    final UnitNumSet unitNumTargets = new UnitNumSet();
    final Map<Location, MoveKind> kinds = Maps.newHashMap();
    int numBlockNumeralMoves;
    boolean isBlockNumeralMove;
    boolean errors;

    Collector(GridMarks gridMarks, Assignment assignment, @Nullable Numeral prevNumeral) {
      this.gridMarks = gridMarks;
      this.assignment = assignment;
      this.prevNumeral = prevNumeral;
    }

    @Override public void take(Insight insight) throws StopException {
      Assignment a = insight.getImpliedAssignment();
      boolean isError = insight.isError();
      if (isError)
        errors = true;
      else if (a != null)
        kinds.put(a.location, kindForInsight(gridMarks, insight, kinds.get(a.location)));
      if (isError || a != null) {
        if (move == null && assignment.equals(a)) {
          move = Analyzer.minimize(gridMarks, insight);
          pattern = getPattern(move);
        }
        insight.addScanTargets(locTargets, unitNumTargets);
      }
      if (a != null && prevNumeral != null && insight.type == Type.FORCED_LOCATION) {
        ForcedLoc fl = (ForcedLoc) insight;
        if (fl.getUnit().getType() == Unit.Type.BLOCK && fl.getNumeral() == prevNumeral) {
          ++numBlockNumeralMoves;
          if (assignment.equals(a)) isBlockNumeralMove = true;
        }
      }
    }

    public double getPredictedMinutes() {
      if (isBlockNumeralMove)
        return MoveKind.calcBlockNumeralMinutes(getNumOpenBlockNumerals(), numBlockNumeralMoves, false);

      if (move == null)
        return MoveKind.calcPause(false);

      MoveKind kind = MoveKind.maxKind(kinds, errors);
      return kind.calcMinutes(gridMarks.grid, locTargets, unitNumTargets, false, move);
    }

    private int getNumOpenBlockNumerals() {
      if (prevNumeral == null) return 0;
      int count = 0;
      for (Block block : Block.all()) {
        UnitSubset locs = gridMarks.marks.get(UnitNumeral.of(block, prevNumeral));
        if (locs.size() > 1 || locs.size() == 1 && !gridMarks.grid.containsKey(locs.get(0)))
          ++count;
      }
      return count;
    }

    private Pattern getPattern(Insight insight) {
      switch (insight.type) {
        case CONFLICT:
          return Pattern.conflict(((Conflict) insight).getLocations().unit);
        case BARRED_LOCATION:
          return Pattern.barredLocation(gridMarks.grid, ((BarredLoc) insight).getLocation());
        case BARRED_NUMERAL:
          return Pattern.barredNumeral(((BarredNum) insight).getUnit());
        case FORCED_LOCATION:
          return Pattern.forcedLocation(((ForcedLoc) insight).getUnit());
        case FORCED_NUMERAL:
          return Pattern.forcedNumeral(gridMarks.grid, ((ForcedNum) insight).getLocation());
        case OVERLAP:
          return Pattern.overlap(((Overlap) insight).getUnit());
        case LOCKED_SET: {
          LockedSet ls = (LockedSet) insight;
          UnitSubset locs = ls.getLocations();
          return Pattern.lockedSet(UnitCategory.forUnit(locs.unit), locs.size(), ls.isNakedSet());
        }
        case IMPLICATION: {
          Implication imp = (Implication) insight;
          List<Pattern> antecedents = Lists.newArrayList();
          for (Insight a : imp.getAntecedents())
            antecedents.add(getPattern(a));
          return Pattern.implication(antecedents, getPattern(imp.getConsequent()), imp.getScanTargetCount());
        }
        default:
          throw new IllegalArgumentException(insight.toShortString());
      }
    }
  }
}
