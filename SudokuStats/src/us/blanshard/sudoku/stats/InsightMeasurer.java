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
import us.blanshard.sudoku.insight.Evaluator;
import us.blanshard.sudoku.insight.Evaluator.MoveKind;
import us.blanshard.sudoku.insight.ForcedLoc;
import us.blanshard.sudoku.insight.ForcedNum;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Implication;
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.Insight.Type;
import us.blanshard.sudoku.insight.LockedSet;
import us.blanshard.sudoku.insight.Overlap;
import us.blanshard.sudoku.stats.Pattern.Coll;
import us.blanshard.sudoku.stats.Pattern.UnitCategory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Analyzes sudoku game histories to measure the time taken to make use of
 * different insights' patterns.
 *
 * @author Luke Blanshard
 */
public class InsightMeasurer implements Runnable {

  public static void main(String[] args) throws Exception {
    PrintWriter out = new PrintWriter("measurer.txt");
    int npuzzles = 0;

    for (AttemptInfo attempt : Attempts.phone2013()) {
      ++npuzzles;
      new InsightMeasurer(attempt.clues, attempt.history, out).run();
      System.out.print('.');
      if (npuzzles % 100 == 0) System.out.println();
      out.flush();
    }

    out.close();
  }

  private static final UnitNumSet blockUnitNums;
  static {
    blockUnitNums = new UnitNumSet();
    for (Block block : Block.all())
      for (Numeral num : Numeral.all())
        blockUnitNums.add(UnitNumeral.of(block, num));
  }

  private final Grid solution;
  private final Sudoku game;
  private final List<Move> history;
  private final UndoDetector undoDetector;
  private final Multimap<Integer, Location> mistakes = HashMultimap.create();
  private final Set<Integer> trails = Sets.newHashSet();
  private final PrintWriter out;

  private int moveNumber = 0;
  private int numSkippedMoves = 0;
  private long prevTime = 0;
  private long skippedTime = 0;
  private Numeral prevNumeral = null;
  private int minOpen;

  private InsightMeasurer(Grid puzzle, List<Move> history, PrintWriter out) {
    Solver.Result result = Solver.solve(puzzle, 10, new Random());
    this.solution = checkNotNull(result.intersection);
    this.game = new Sudoku(puzzle).resume();
    this.history = history;
    this.undoDetector = new UndoDetector(game);
    this.out = out;
    this.minOpen = puzzle.getNumOpenLocations();
  }

  @Override public void run() {
    for (Move move : history) {
      applyMove(move);
      ++moveNumber;
      prevTime = move.timestamp;
      prevNumeral = move.getNumeral();
    }
  }

  private void applyMove(Move move) {
    noteMistakes(move);
    long elapsed = move.timestamp - prevTime;
    if (move instanceof Move.Set
        && !undoDetector.isUndoOrRedo(move)
        && !isApparentCorrection(move)) {
      Grid grid = game.getState(move.trailId).getGrid();
      if (grid.containsKey(move.getLocation()))
        grid = grid.toBuilder().remove(move.getLocation()).build();
      int numOpen = grid.getNumOpenLocations();
      if (numOpen < minOpen)
        minOpen = numOpen;
      GridMarks gridMarks = new GridMarks(grid);
      Collector collector = new Collector(gridMarks, move.getAssignment(), prevNumeral);
      Analyzer.analyze(gridMarks, collector, true);
      boolean isTrailhead = move.trailId >= 0 && game.getTrail(move.trailId).getSetCount() == 0;
      List<Coll> matched = Lists.newArrayList();
      List<Coll> missed = Lists.newArrayList();
      collector.makeColls(matched, missed);
      try {
        Pattern.appendAllTo(out, matched)
            .append('\t');
        Pattern.appendAllTo(out, missed)
            .append('\t')
            .append(collector.firstKind == null ? "" : collector.firstKind.toString())
            .append('\t')
            .append(String.valueOf(moveNumber))
            .append('\t')
            .append(String.valueOf(moveNumber - numSkippedMoves))
            .append('\t')
            .append(String.valueOf(move.timestamp))
            .append('\t')
            .append(String.valueOf(move.timestamp - skippedTime))
            .append('\t')
            .append(String.valueOf(minOpen))
            .append('\t')
            .append(String.valueOf(elapsed))
            .append('\t')
            .append(String.valueOf(numOpen))
            .append('\t')
            .append(String.valueOf(collector.isBlockNumeralMove()))
            .append('\t')
            .append(String.valueOf(collector.getNumBlockNumeralMoves()))
            .append('\t')
            .append(String.valueOf(collector.getNumOpenBlockNumerals()))
            .append('\t')
            .append(String.valueOf(isTrailhead))
            .append('\t')
            .append(String.valueOf(trails.size()))
            ;
        out.println();
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    } else {
      ++numSkippedMoves;
      skippedTime += elapsed;
    }
    game.move(move);
    if (move.trailId >= 0) trails.add(move.trailId);
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
    final ListMultimap<MoveKind, Insight> insights = ArrayListMultimap.create();
    final ListMultimap<MoveKind, Pattern> patterns = ArrayListMultimap.create();
    final Set<MoveKind> found = EnumSet.noneOf(MoveKind.class);
    MoveKind firstKind;
    int numBlockNumeralMoves;
    boolean isBlockNumeralMove;

    Collector(GridMarks gridMarks, Assignment assignment, @Nullable Numeral prevNumeral) {
      this.gridMarks = gridMarks;
      this.assignment = assignment;
      this.prevNumeral = prevNumeral;
    }

    @Override public void take(Insight insight) throws StopException {
      Assignment a = insight.getImpliedAssignment();
      boolean isError = insight.isError();
      if (isError || a != null) {
        insight = minimize(gridMarks, insight);
        MoveKind kind = Evaluator.kindForInsight(gridMarks, insight, null);
        insights.put(kind, insight);
        if (firstKind == null) firstKind = kind;

        Pattern pattern = getPattern(insight);
        if (assignment.equals(a)) {
          int index = found.add(kind) ? 0 : 1;
          patterns.get(kind).add(index, pattern);  // Put the first one first.
        } else {
          patterns.put(kind, pattern);
        }
      }
      if (a != null && prevNumeral != null && insight.type == Type.FORCED_LOCATION) {
        ForcedLoc fl = (ForcedLoc) insight;
        if (fl.getUnit().getType() == Unit.Type.BLOCK && fl.getNumeral() == prevNumeral) {
          ++numBlockNumeralMoves;
          if (assignment.equals(a)) isBlockNumeralMove = true;
        }
      }
    }

    int getNumBlockNumeralMoves() {
      return numBlockNumeralMoves;
    }

    boolean isBlockNumeralMove() {
      return isBlockNumeralMove;
    }

    int getNumOpenBlockNumerals() {
      if (prevNumeral == null) return 0;
      int count = 0;
      for (Block block : Block.all()) {
        UnitSubset locs = gridMarks.marks.get(UnitNumeral.of(block, prevNumeral));
        if (locs.size() > 1 || locs.size() == 1 && !gridMarks.grid.containsKey(locs.get(0)))
          ++count;
      }
      return count;
    }

    void makeColls(List<Coll> matched, List<Coll> missed) {
      for (MoveKind kind : insights.keySet()) {
        LocSet locTargets = new LocSet();
        UnitNumSet unitNumTargets = new UnitNumSet();
        int realmVector = 0;
        for (Insight insight : insights.get(kind)) {
          insight.addScanTargets(locTargets, unitNumTargets);
          realmVector |= insight.getRealmVector();
        }
        Coll coll = new Coll(patterns.get(kind), kind, realmVector,
            unitNumTargets.size() + locTargets.size());
        (found.contains(kind) ? matched : missed).add(coll);
      }
    }

    private Insight minimize(GridMarks gridMarks, Insight insight) {
      if (insight.type != Type.IMPLICATION) return insight;
      Implication i = (Implication) insight;
      i = Evaluator.minimizeForSimplicityTest(gridMarks, i);
      return Analyzer.minimize(gridMarks, i == null ? insight : i);
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
