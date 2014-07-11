/*
Copyright 2014 Luke Blanshard

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

import static us.blanshard.sudoku.insight.Evaluator.kindForInsight;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.UnitNumSet;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.UndoDetector;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Analyzer.StopException;
import us.blanshard.sudoku.insight.Evaluator.MoveKind;
import us.blanshard.sudoku.insight.ForcedLoc;
import us.blanshard.sudoku.insight.ForcedNum;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Insight;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Analyzes sudoku game histories and extracts the details of various insights
 * for further study.
 *
 * @author Luke Blanshard
 */
public class DetailGrabber implements Runnable {

  public static void main(String[] args) throws Exception {
    PrintWriter out = new PrintWriter("details.txt");
    int npuzzles = 0;

    Iterable<AttemptInfo> attempts = Iterables.concat(
        Attempts.phone2013(), Attempts.tablet2014() /*, Attempts.datastoreBackup()*/);
    for (AttemptInfo attempt : attempts) {
      ++npuzzles;
      new DetailGrabber(attempt.clues, attempt.history, out).run();
      System.out.print('.');
      if (npuzzles % 100 == 0) System.out.println();
      out.flush();
    }

    out.close();
  }

  private final Sudoku game;
  private final List<Move> history;
  private final UndoDetector undoDetector;
  private final Set<Integer> trails = Sets.newHashSet();
  private final PrintWriter out;

  private long prevTime = 0;
  private int minOpen;

  private DetailGrabber(Grid puzzle, List<Move> history, PrintWriter out) {
    this.game = new Sudoku(puzzle).resume();
    this.history = history;
    this.undoDetector = new UndoDetector(game);
    this.out = out;
    this.minOpen = puzzle.getNumOpenLocations();
  }

  @Override public void run() {
    for (Move move : history) {
      applyMove(move);
      prevTime = move.timestamp;
    }
  }

  private static final Analyzer.Options OPTS = new Analyzer.Options(false, true);

  private void applyMove(Move move) {
    long elapsed = move.timestamp - prevTime;
    if (move instanceof Move.Set && !undoDetector.isUndoOrRedo(move)) {
      Grid grid = game.getState(move.trailId).getGrid();
      if (grid.containsKey(move.getLocation()))
        grid = grid.toBuilder().remove(move.getLocation()).build();
      int numOpen = grid.getNumOpenLocations();
      if (numOpen < minOpen)
        minOpen = numOpen;
      GridMarks gridMarks = new GridMarks(grid);
      Collector collector = new Collector(gridMarks);
      Analyzer.analyze(gridMarks, collector, OPTS);
      collector.extendElims();
      boolean isTrailhead = move.trailId >= 0
          && (game.getTrail(move.trailId).getTrailhead() == null
              || game.getTrail(move.trailId).getTrailhead() == move.getLocation());
      if (isTrailhead) {
        for (Assignment a : collector.moves.keySet()) {
          optionallyEmit(collector, a, true, elapsed, numOpen);
        }
      } else {
        optionallyEmit(collector, move.getAssignment(), false, elapsed, numOpen);
      }
    }
    game.move(move);
    if (move.trailId >= 0) trails.add(move.trailId);
  }

  private void optionallyEmit(
      Collector collector, Assignment assignment, boolean isTrailhead, long elapsed, int numOpen) {
    Pattern p = collector.getPatternIfRelevant(assignment);
    if (p != null) {
      emitLine(isTrailhead, elapsed, numOpen, p, collector.universeForAssignment(assignment));
    }
  }

  private void emitLine(boolean isTrailhead, long elapsed, int numOpen, Pattern pattern, Universe universe) {
    out.print(isTrailhead);
    emit(elapsed);
    emit(numOpen);
    emit(minOpen);
    emit(trails.size());
    emit(pattern.toString());
    emitUniverse(universe);
    out.println();
  }

  private void emit(String s) {
    out.print('\t');
    out.print(s);
  }

  private void emit(int i) {
    emit(String.valueOf(i));
  }

  private void emit(long i) {
    emit(String.valueOf(i));
  }

  private void emitUniverse(Universe u) {
    emit(u.numerator());
    emit(u.denominator());
    emit(u.realmVector());
  }

  private class Collector implements Analyzer.Callback {
    final GridMarks gridMarks;
    final Multimap<Assignment, WrappedInsight> moves = ArrayListMultimap.create();
    final Collection<WrappedInsight> errors = Lists.newArrayList();
    final Set<Insight> elims = Sets.newLinkedHashSet();

    Collector(GridMarks gridMarks) {
      this.gridMarks = gridMarks;
    }

    @Override public void take(Insight insight) throws StopException {
      insight = Analyzer.minimize(gridMarks, insight);
      Assignment a = insight.getImpliedAssignment();
      if (a != null)
        moves.put(a, new WrappedInsight(gridMarks, insight));
      else if (insight.isError())
        errors.add(new WrappedInsight(gridMarks, insight));
      else
        elims.add(insight);
    }

    /**
     * If the given assignment has either a set of ForcedLoc insights, or a
     * ForcedNum insight, but not both, and no other playable insight, returns
     * the corresponding pattern; otherwise returns null.
     */
    @Nullable public Pattern getPatternIfRelevant(Assignment a) {
      ForcedLoc forcedLoc = null;
      ForcedNum forcedNum = null;
      for (WrappedInsight w : moves.get(a)) {
        switch (w.insight.type) {
          case FORCED_LOCATION:
            if (forcedLoc == null) forcedLoc = (ForcedLoc) w.insight;
            break;
          case FORCED_NUMERAL:
            forcedNum = (ForcedNum) w.insight;
            break;
          default:
            if (w.insight.isAssignment() && w.kind().isPlayable()) return null;
            break;
        }
      }

      if (forcedLoc != null) {
        if (forcedNum == null) return Pattern.forcedLocation(forcedLoc.getUnit());
        return null;
      }

      if (forcedNum != null) {
        return Pattern.forcedNumeral(gridMarks.grid, forcedNum.getLocation());
      }

      return null;
    }

    public void extendElims() {
      GridMarks.Builder builder = gridMarks.toBuilder().apply(elims);
      int count = elims.size();
      while (count > 0) {
        final Collection<Insight> e = Lists.newArrayList();
        Analyzer.findOverlapsAndSets(builder.build(), new Analyzer.Callback() {
          @Override public void take(Insight insight) throws StopException {
            if (elims.add(insight))
              e.add(insight);
          }
        });
        count = e.size();
        builder.apply(e);
      }
    }

    public Universe universeForAssignment(Assignment assignment) {
      Universe universe = new Universe();
      for (Assignment a : moves.keySet())
        for (WrappedInsight w : moves.get(a))
          universe.add(w.insight, a.equals(assignment));
      for (WrappedInsight w : errors)
        universe.add(w.insight, false);
      for (Insight i : elims)
        universe.add(i, false);
      return universe;
    }
  }

  private static class WrappedInsight {
    final Grid grid;
    final Insight insight;
    private MoveKind kind;
    WrappedInsight(GridMarks gridMarks, Insight insight) {
      this.grid = gridMarks.grid;
      this.insight = insight;
    }
    MoveKind kind() {
      if (kind == null)
        kind = kindForInsight(grid, insight);
      return kind;
    }
  }

  /**
   * Gathers the scan targets (numerator and denominator) and realm vector
   * for one collection of insights in a batch.
   */
  private static class Universe {
    private final LocSet locTargetsNum = new LocSet();
    private final UnitNumSet unitNumTargetsNum = new UnitNumSet();
    private final LocSet locTargetsDenom = new LocSet();
    private final UnitNumSet unitNumTargetsDenom = new UnitNumSet();
    private int realmVector;

    void add(Insight insight, boolean numerator) {
      if (numerator) {
        insight.addScanTargets(locTargetsNum, unitNumTargetsNum);
        realmVector |= insight.getNub().getRealmVector();
      }
      insight.addScanTargets(locTargetsDenom, unitNumTargetsDenom);
    }

    int numerator() {
      return locTargetsNum.size() + unitNumTargetsNum.size();
    }

    int denominator() {
      return locTargetsDenom.size() + unitNumTargetsDenom.size();
    }

    int realmVector() {
      return realmVector;
    }
  }
}
