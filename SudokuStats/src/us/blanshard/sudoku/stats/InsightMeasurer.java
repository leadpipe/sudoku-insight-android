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

import static us.blanshard.sudoku.insight.Evaluator.kindForInsight;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.UnitNumSet;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.UndoDetector;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Analyzer.StopException;
import us.blanshard.sudoku.insight.Evaluator.MoveKind;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Insight;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

    Iterable<AttemptInfo> attempts = Iterables.concat(
        Attempts.phone2013(), Attempts.tablet2014() /*, Attempts.datastoreBackup()*/);
    for (AttemptInfo attempt : attempts) {
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

  private final Sudoku game;
  private final List<Move> history;
  private final UndoDetector undoDetector;
  private final Set<Integer> trails = Sets.newHashSet();
  private final PrintWriter out;

  private final Collection<Batch> openBatches = Sets.newLinkedHashSet();

  private int moveNumber = 0;
  private int numSkippedMoves = 0;
  private long prevTime = 0;
  private long skippedTime = 0;
  private int minOpen;

  private InsightMeasurer(Grid puzzle, List<Move> history, PrintWriter out) {
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
    }
  }

  private static final Analyzer.Options OPTS = new Analyzer.Options(false, true);

  private void applyMove(Move move) {
    long elapsed = move.timestamp - prevTime;
    Collection<Batch> finishedBatches = Lists.newArrayList();
    Batch newBatch = null;
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
        MinKindAndCount min = getMinAndCount(collector.moves.values());
        Universe universe = getPlausibleUniverse(collector, min);
        emitTrailheadLine(elapsed, numOpen, move.timestamp, min, universe);
      } else if (collector.moves.containsKey(move.getAssignment())) {
        newBatch = new Batch(collector, move, elapsed, numOpen);
        for (Batch b : openBatches) {
          if (!b.extendWith(move, elapsed, newBatch))
            finishedBatches.add(b);
        }
        openBatches.add(newBatch);
      }
    }
    if (newBatch == null) {
      finishedBatches.addAll(openBatches);
    }
    for (Batch b : finishedBatches) {
      emitBatch(b);
      openBatches.remove(b);
    }
    game.move(move);
    if (move.trailId >= 0) trails.add(move.trailId);
    if (newBatch == null) {
      ++numSkippedMoves;
      skippedTime += elapsed;
    }
  }

  static MinKindAndCount getMinAndCount(Collection<WrappedInsight> wrappedInsights) {
    MinKindAndCount answer = new MinKindAndCount();
    for (WrappedInsight w : wrappedInsights) {
      answer.add(w.kind());
    }
    return answer;
  }

  @Nullable static Universe getPlausibleUniverse(Collector collector, MinKindAndCount min) {
    if (min.count == 0) return null;
    Universe universe = null;
    Assignment assignment = null;
    for (Assignment a : collector.moves.keySet()) {
      for (WrappedInsight w : collector.moves.get(a))
        if (w.kind == min.kind) {
          Universe u = collector.universeForAssignment(a);
          if (universe == null || u.numerator() < universe.numerator()) {
            universe = u;
            assignment = a;
          }
          break;
        }
    }
    collector.fillDenominator(universe, Collections.singleton(assignment));
    return universe;
  }

  private void emitTrailheadLine(long elapsed, int numOpen, long timestamp, MinKindAndCount min,
                                 @Nullable Universe universe) {
    startLine(true, elapsed, numOpen, timestamp, min);
    if (universe != null) emitUniverse(universe);
    endLine();
  }

  private void emitBatch(Batch b) {
    startLine(false, b.totalElapsed, b.numOpen, b.firstMove.timestamp, b.minPlayable);
    emit(b.assignments.size());
    emit(b.maxOfFirstMoveMins);
    emit(b.maxOfOriginalMins);
    emit(getMinAndCount(b.collector.errors));
    b.emitUniverse();
    endLine();
  }

  private void startLine(boolean isTrailhead, long elapsed, int numOpen,
      long timestamp, MinKindAndCount min) {
    out.print(isTrailhead);
    emit(elapsed);
    emit(numOpen);
    emit(minOpen);
    emit(trails.size());
    emit(timestamp);
    emit(timestamp - skippedTime);
    emit(moveNumber);
    emit(moveNumber - numSkippedMoves);
    emit(min);
  }

  private void endLine() {
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

  private void emit(MoveKind kind) {
    emit(kind == null ? "" : kind.toString());
  }

  private void emit(MinKindAndCount min) {
    emit(min.kind);
    emit(min.count);
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

    public Universe universeForAssignment(Assignment a) {
      Universe universe = new Universe();
      for (WrappedInsight w : moves.get(a)) {
        universe.add(w.insight, true);
      }
      return universe;
    }

    public void fillDenominator(Universe universe, Set<Assignment> except) {
      for (Assignment a : moves.keySet())
        if (!except.contains(a))
          for (WrappedInsight w : moves.get(a))
            universe.add(w.insight, false);
      for (WrappedInsight w : errors)
        universe.add(w.insight, false);
      for (Insight i : elims)
        universe.add(i, false);
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

  static Ordering<MoveKind> ORD = Ordering.natural().nullsFirst();

  private static class MinKindAndCount {
    MoveKind kind;
    int count;
    void add(MoveKind k) {
      if (kind == null || ORD.compare(k, kind) < 0) {
        kind = k;
        count = 1;
      } else if (k == kind) {
        ++count;
      }
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

  private class Batch {
    final Collector collector;
    final Move firstMove;
    final int numOpen;
    final MinKindAndCount minPlayable;
    final MinKindAndCount firstMoveMin;
    MoveKind maxOfFirstMoveMins;
    final MoveKind maxOfOriginalMins;
    final Set<Assignment> assignments = Sets.newHashSet();
    final Universe universe;
    long totalElapsed;

    Batch(Collector collector, Move firstMove, long elapsed, int numOpen) {
      this.collector = collector;
      this.firstMove = firstMove;
      this.numOpen = numOpen;
      this.minPlayable = getMinAndCount(collector.moves.values());
      Assignment assignment = firstMove.getAssignment();
      this.firstMoveMin = getMinAndCount(collector.moves.get(assignment));
      this.maxOfFirstMoveMins = firstMoveMin.kind;
      MoveKind max = firstMoveMin.kind;
      for (Assignment a : collector.moves.keySet()) {
        max = ORD.max(max, getMinAndCount(collector.moves.get(a)).kind);
      }
      this.maxOfOriginalMins = max;
      this.universe = collector.universeForAssignment(assignment);
      assignments.add(assignment);
      totalElapsed = elapsed;
    }

    /**
     * Attempts to add the given move to this batch, returns true if it fits.
     */
    boolean extendWith(Move move, long elapsed, Batch newBatch) {
      if (move.trailId >= 0 && move.trailId != firstMove.trailId)
        return false;
      Assignment a = move.getAssignment();
      if (collector.moves.containsKey(a)) {
        assignments.add(a);
        totalElapsed += elapsed;
        for (WrappedInsight w : collector.moves.get(a)) {
          universe.add(w.insight, true);
        }
        maxOfFirstMoveMins = ORD.max(maxOfFirstMoveMins, newBatch.firstMoveMin.kind);
      }
      return false;
    }

    /**
     * The universe consists of all insights: the numerator is the number of
     * scan targets in all insights for the moves we've made, and the
     * denominator is the number of scan targets in all insights in the batch.
     */
    public void emitUniverse() {
      collector.fillDenominator(universe, assignments);
      InsightMeasurer.this.emitUniverse(universe);
    }
  }
}
