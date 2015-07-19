/*
 * Copyright 2013 Luke Blanshard Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package us.blanshard.sudoku.stats;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.UndoDetector;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Analyzer.StopException;
import us.blanshard.sudoku.insight.BarredLoc;
import us.blanshard.sudoku.insight.BarredNum;
import us.blanshard.sudoku.insight.Conflict;
import us.blanshard.sudoku.insight.Evaluator;
import us.blanshard.sudoku.insight.ForcedLoc;
import us.blanshard.sudoku.insight.ForcedNum;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Implication;
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.LockedSet;
import us.blanshard.sudoku.insight.Overlap;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Analyzes sudoku game histories to measure the time taken to make use of
 * different insights' patterns.
 *
 * @author Luke Blanshard
 */
public class InsightMeasurer {

  public static void main(String[] args) throws Exception {
    int sampleCount = 0;
    int sampleSkip = 10;
    boolean printSamples = sampleCount > 0;
    PrintWriter out = new PrintWriter("measurer.txt");
    int npuzzles = 0;

    Iterable<AttemptInfo> attempts =
        Iterables.concat(Attempts.phone2013(), Attempts.tablet2014()/*, Attempts.datastoreBackup()*/);
    for (AttemptInfo attempt : attempts) {
      ++npuzzles;
      int numSamples = new InsightMeasurer(attempt.clues, attempt.history, out)
          .run(sampleCount, sampleSkip);
      if (sampleSkip >= numSamples) {
        sampleSkip -= numSamples;
      } else {
        sampleCount -= (numSamples - sampleSkip);
        sampleSkip = 0;
      }
      if (printSamples) {
        if (sampleCount <= 0) break;
      } else {
        System.out.print('.');
        if (npuzzles % 100 == 0)
          System.out.println();
        out.flush();
      }
    }

    out.close();
  }

  private final Sudoku game;
  private final List<Move> history;
  private final UndoDetector undoDetector;
  private final Set<Integer> trails = Sets.newHashSet();
  private final PrintWriter out;
  private final Map<Integer, TrailState> trailFinals = Maps.newHashMap();

  private Batch openBatch = null;

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

  public int run(int sampleCount, int sampleSkip) {
    int numSamples = 0;
    for (Move move : history) {
      boolean shouldEmitSample = numSamples >= sampleSkip && sampleCount > numSamples - sampleSkip;
      numSamples += applyMove(move, sampleCount > 0, shouldEmitSample) ? 1 : 0;
      ++moveNumber;
      prevTime = move.timestamp;
    }
    if (openBatch != null) {
      emitBatch(openBatch);
    }
    // If the puzzle was solved, go back to all trails that currently have
    // errors and emit those errors as having been seen.
    if (game.getState().getGrid().isSolved()) {
      emitAbandonedErrors();
    }
    return numSamples;
  }

  private static final Analyzer.Options OPTS = new Analyzer.Options(false, true);

  private boolean applyMove(Move move, boolean findSample, boolean emitSample) {
    long elapsed = move.timestamp - prevTime;
    Batch newBatch = null;
    Batch finishedBatch = null;
    boolean isSample = false;
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
      boolean isTrailhead =
          move.trailId >= 0
              && (game.getTrail(move.trailId).getTrailhead() == null || game.getTrail(move.trailId)
                  .getTrailhead() == move.getLocation());
      NumSet prevNums = openBatch == null ? NumSet.NONE : openBatch.nums;
      if (isTrailhead) {
        emitTrailheadLine(elapsed, numOpen, move.timestamp, collector, prevNums);
      } else {
        Assignment a = move.getAssignment();
        if (collector.moves.containsKey(a)) {
          newBatch = new Batch(collector, move, elapsed, numOpen, prevNums);
          if (openBatch == null) {
            openBatch = newBatch;
          } else if (!openBatch.extendWith(move, elapsed, newBatch)) {
            finishedBatch = openBatch;
            openBatch = newBatch;
          }
          isSample = findSample && !hasSimpleInsight(collector, a);
        } else {
          isSample = findSample && !isErroneousMoveOrFirstMoveAfterTrail(grid, a);
        }
        if (isSample && emitSample) {
          System.out.printf("Move %s on grid\n%s", a, grid);
          for (WrappedInsight w : collector.moves.get(a)) {
            System.out.println(w.insight);
          }
          System.out.println();
        }
      }
    }
    if (newBatch == null) {
      finishedBatch = openBatch;
      openBatch = null;
    }
    if (finishedBatch != null) {
      emitBatch(finishedBatch);
    }
    game.move(move);
    if (move.trailId >= 0) {
      trails.add(move.trailId);
      NumSet set = move.getNumeral() == null ? NumSet.NONE : move.getNumeral().asSet();
      trailFinals.put(move.trailId,
          new TrailState(game.getTrail(move.trailId).getGrid(), minOpen, set));
    }
    if (newBatch == null) {
      ++numSkippedMoves;
      skippedTime += elapsed;
    }
    return isSample;
  }

  private boolean isErroneousMoveOrFirstMoveAfterTrail(Grid grid, Assignment a) {
    if (moveNumber > 0 && history.get(moveNumber).trailId < 0 && history.get(moveNumber - 1).trailId >= 0) {
      return true;
    }
    grid = grid.toBuilder().assign(a).build();
    class ErrorSeer implements Analyzer.Callback {
      boolean errorSeen = false;
      @Override public void take(Insight insight) throws StopException {
        if (insight.isError()) errorSeen = true;
      }
    }
    ErrorSeer callback = new ErrorSeer();
    Analyzer.findErrors(new GridMarks(grid), callback);
    return callback.errorSeen;
  }

  private boolean hasSimpleInsight(Collector collector, Assignment a) {
    boolean simpleInsight = false;
    MOVES: for (WrappedInsight w : collector.moves.get(a)) {
      Insight i = w.insight;
      while (i.type == Insight.Type.IMPLICATION) {
        Implication imp = (Implication) i;
        for (Insight ant : imp.getAntecedents()) {
          if (ant.type == Insight.Type.LOCKED_SET) {
            LockedSet set = (LockedSet) ant;
            if (set.isNakedSet()) continue MOVES;
          }
        }
        i = imp.getConsequent();
      }
      simpleInsight = true;
      break;
    }
    return simpleInsight;
  }

  private void emitTrailheadLine(long elapsed, int numOpen, long timestamp, Collector collector, NumSet prevNums) {
    startLine(true, elapsed, numOpen, timestamp);
    collector.emitColls(collector.moves.keySet(), prevNums, Collections.<WrappedInsight> emptySet());
    endLine();
  }

  private void emitBatch(Batch b) {
    startLine(false, b.totalElapsed, b.numOpen, b.firstMove.timestamp);
    b.emitColls();
    endLine();
  }

  private void emitAbandonedErrors() {
    for (Map.Entry<Integer, TrailState> entry : trailFinals.entrySet()) {
      GridMarks gridMarks = new GridMarks(entry.getValue().grid);
      if (gridMarks.hasErrors) {
        Collector collector = new Collector(gridMarks);
        Analyzer.analyze(gridMarks, collector, OPTS);
        long timestamp = 0;
        long elapsed = 0;
        int trailId = entry.getKey();
        for (int index = history.size(); index-- > 0;) {
          Move move = history.get(index);
          if (move.trailId == trailId) {
            timestamp = history.get(index + 1).timestamp;
            elapsed = timestamp - move.timestamp;
            break;
          }
        }
        minOpen = entry.getValue().minOpen;
        startLine(false, elapsed, gridMarks.grid.getNumOpenLocations(), timestamp);
        StringBuilder sb = new StringBuilder();
        try {
          Pattern.appendTo(sb, toColl(collector.errors, entry.getValue().prevNums));
        } catch (IOException e) {
          throw new AssertionError(e);
        }
        emit(sb.toString());
        emit("");
        endLine();
      }
    }
  }

  private static Pattern.Coll toColl(Iterable<WrappedInsight> insights, NumSet prevNums) {
    List<Pattern> ps = Lists.newArrayList();
    for (WrappedInsight w : insights)
      ps.add(w.getPattern(prevNums));
    return new Pattern.Coll(ps);
  }

  private void startLine(boolean isTrailhead, long elapsed, int numOpen, long timestamp) {
    out.print(isTrailhead);
    emit(elapsed);
    emit(numOpen);
    emit(minOpen);
    emit(trails.size());
    emit(timestamp);
    emit(timestamp - skippedTime);
    emit(moveNumber);
    emit(moveNumber - numSkippedMoves);
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

  private static class TrailState {
    final Grid grid;
    final int minOpen;
    final NumSet prevNums;

    TrailState(Grid grid, int minOpen, NumSet prevNums) {
      this.grid = grid;
      this.minOpen = minOpen;
      this.prevNums = prevNums;
    }
  }

  private class Collector implements Analyzer.Callback {
    final GridMarks gridMarks;
    final Multimap<Assignment, WrappedInsight> moves = ArrayListMultimap.create();
    final List<WrappedInsight> errors = Lists.newArrayList();

    Collector(GridMarks gridMarks) {
      this.gridMarks = gridMarks;
    }

    @Override public void take(Insight insight) throws StopException {
      insight = Analyzer.minimize(gridMarks, insight);
      Assignment a = insight.getImpliedAssignment();
      if (a != null) {
        moves.put(a, new WrappedInsight(gridMarks, insight));
      } else if (insight.isError()) {
        errors.add(new WrappedInsight(gridMarks, insight));
      }
    }

    public void emitColls(Iterable<Assignment> assignments, NumSet prevNums, Collection<WrappedInsight> errors) {
      StringBuilder sb = new StringBuilder();
      try {
        List<Pattern.Coll> colls = Lists.newArrayList();
        NumSet nums = prevNums;
        for (Assignment a : assignments) {
          colls.add(toColl(moves.get(a), nums));
          nums = nums.with(a.numeral);
        }
        if (!errors.isEmpty()) {
          colls.add(toColl(errors, prevNums));
        }
        Pattern.appendAllTo(sb, colls);
      } catch (IOException impossible) {
        throw new AssertionError(null, impossible);
      }
      emit(sb.toString());
    }
  }

  private static NumSet getNumerals(Insight insight, NumSet prevNums) {
    Numeral numeral = getNumeral(insight);
    return numeral == null ? prevNums : prevNums.with(numeral);
  }

  @Nullable private static Numeral getNumeral(Insight insight) {
    switch (insight.type) {
      case CONFLICT: {
        Conflict i = (Conflict) insight;
        return i.getNumeral();
      }
      case BARRED_LOCATION:
        return null;
      case BARRED_NUMERAL: {
        BarredNum i = (BarredNum) insight;
        return i.getNumeral();
      }
      case FORCED_LOCATION: {
        ForcedLoc i = (ForcedLoc) insight;
        return i.getNumeral();
      }
      case FORCED_NUMERAL: {
        ForcedNum i = (ForcedNum) insight;
        return i.getNumeral();
      }
      case OVERLAP: {
        Overlap i = (Overlap) insight;
        return i.getNumeral();
      }
      case LOCKED_SET:
        return null;
      case IMPLICATION: {
        Implication i = (Implication) insight;
        return getNumeral(i.getConsequent());
      }
      default:
        throw new IllegalArgumentException(insight.toShortString());
    }
  }

  private static class WrappedInsight {
    final Grid grid;
    final Insight insight;

    WrappedInsight(GridMarks gridMarks, Insight insight) {
      this.grid = gridMarks.grid;
      this.insight = insight;
    }

    Pattern getPattern(NumSet prevNums) {
      return getPattern(insight, prevNums);
    }

    private Pattern getPattern(Insight insight, NumSet prevNums) {
      switch (insight.type) {
        case CONFLICT: {
          final Conflict i = (Conflict) insight;
          return Pattern.conflict(prevNums.contains(i.getNumeral()), i.getLocations().unit);
        }
        case BARRED_LOCATION:
          return Pattern.barredLocation(Evaluator.Pattern.forInsight(insight, grid),
              ((BarredLoc) insight).getLocation(), grid);
        case BARRED_NUMERAL: {
          final BarredNum i = (BarredNum) insight;
          return Pattern.barredNumeral(prevNums.contains(i.getNumeral()), i.getUnit());
        }
        case FORCED_LOCATION: {
          ForcedLoc i = (ForcedLoc) insight;
          return Pattern.forcedLocation(prevNums.contains(i.getNumeral()), i.getUnit());
        }
        case FORCED_NUMERAL: {
          ForcedNum i = (ForcedNum) insight;
          return Pattern.forcedNumeral(prevNums.contains(i.getNumeral()),
              Evaluator.Pattern.forInsight(insight, grid), grid, i.getLocation());
        }
        case OVERLAP: {
          Overlap i = (Overlap) insight;
          return Pattern.overlap(prevNums.contains(i.getNumeral()), i.getUnit());
        }
        case LOCKED_SET: {
          LockedSet i = (LockedSet) insight;
          return Pattern.lockedSet(!prevNums.and(i.getNumerals()).isEmpty(), i, grid);
        }
        case IMPLICATION: {
          Implication imp = (Implication) insight;
          List<Pattern> antecedents = Lists.newArrayList();
          NumSet consequentNumeral = getNumerals(imp.getConsequent(), NumSet.NONE);
          for (Insight a : imp.getAntecedents())
            antecedents.add(getPattern(a, consequentNumeral));
          return Pattern.implication(antecedents, getPattern(imp.getConsequent(), prevNums));
        }
        default:
          throw new IllegalArgumentException(insight.toShortString());
      }
    }
  }

  private class Batch {
    final Collector collector;
    final Move firstMove;
    final int numOpen;
    final Set<Assignment> assignments = Sets.newLinkedHashSet();
    final NumSet prevNums;
    NumSet nums;
    long totalElapsed;

    Batch(Collector collector, Move firstMove, long elapsed, int numOpen, NumSet prevNums) {
      this.collector = collector;
      this.firstMove = firstMove;
      this.numOpen = numOpen;
      Assignment assignment = firstMove.getAssignment();
      assignments.add(assignment);
      this.prevNums = prevNums;
      nums = assignment.numeral.asSet();
      totalElapsed = elapsed;
    }

    /**
     * Attempts to add the given move to this batch, returns true if it fits.
     */
    boolean extendWith(Move move, long elapsed, Batch newBatch) {
      if (move.trailId >= 0 && move.trailId != firstMove.trailId) {
        return false;
      }

      Assignment a = move.getAssignment();
      if (!collector.moves.containsKey(a))
        return false;
      List<WrappedInsight> wa = (List<WrappedInsight>) collector.moves.get(a);
      List<WrappedInsight> wb = (List<WrappedInsight>) newBatch.collector.moves.get(a);
      if (wa.size() != wb.size())
        return false;
      for (int i = 0; i < wa.size(); ++i)
        if (!wa.get(i).insight.equals(wb.get(i).insight))
          return false;

      assignments.add(a);
      nums = nums.with(a.numeral);
      totalElapsed += elapsed;
      return true;
    }

    public void emitColls() {
      collector.emitColls(assignments, prevNums, Collections.<WrappedInsight> emptySet());
      Set<Assignment> missed = Sets.newLinkedHashSet(collector.moves.keySet());
      missed.removeAll(assignments);
      collector.emitColls(missed, prevNums, collector.errors);
    }
  }
}
