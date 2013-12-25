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
package us.blanshard.sudoku.insight;

import static com.google.common.base.Preconditions.checkArgument;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumSet;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.insight.Analyzer.StopException;
import us.blanshard.sudoku.insight.Rating.Difficulty;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

/**
 * Methods to evaluate the difficulty of a Sudoku.
 */
public class Evaluator {

  /** The current version of the time estimation algorithm. */
  public static final int CURRENT_VERSION = 2;

  /**
   * How many times we run the evaluator when we must make choices that could
   * make the result vary by a lot.
   */
  public static final int NUM_TRIALS = 10;

  /**
   * Called back periodically from {@link #evaluate} with updates to the
   * estimated score.
   */
  public interface Callback {
    void updateEstimate(double minScore);
    void disproofsRequired();
  }

  /**
   * Evaluates the difficulty of a Sudoku puzzle, which must be solvable and
   * have no more than a few solutions (or the method will throw). The provided
   * callback may be given updates during the evaluation; these will be
   * monotonically increasing. If the thread is interrupted, the evaluation may
   * stop early, and in that case the result object will be marked as incomplete.
   */
  public static Rating evaluate(Grid puzzle, Callback callback) {
    return new Evaluator(puzzle).evaluate(callback);
  }

  private final Grid puzzle;
  private final boolean improper;
  private final Grid solutionIntersection;
  private final Random random;

  public Evaluator(Grid puzzle) {
    this(puzzle, new Random(0));
  }

  public Evaluator(Grid puzzle, Random random) {
    Solver.Result solverResult = Solver.solve(puzzle, 10);
    if (solverResult.intersection == null)
      throw new IllegalArgumentException("Puzzle has no solution");
    this.puzzle = puzzle;
    this.improper = solverResult.numSolutions > 1;
    this.solutionIntersection = solverResult.intersection;
    this.random = random;
  }

  public Rating evaluate(@Nullable Callback callback) {
    return evaluate(callback, NUM_TRIALS);
  }

  public Rating evaluate(@Nullable Callback callback, int trialCount) {
    checkArgument(trialCount >= 1);
    Run outer = new Run(new GridMarks(puzzle), false);
    outer.runStraightShot(callback);
    double score = outer.score;
    boolean uninterrupted = outer.uninterrupted();
    Difficulty difficulty = Difficulty.NO_DISPROOFS;
    if (outer.status == RunStatus.INCONCLUSIVE) {
      difficulty = Difficulty.SIMPLE_DISPROOFS;
      if (callback != null) callback.disproofsRequired();
      double totalScore = 0;
      int numEvaluations = 0;
      while (uninterrupted && numEvaluations++ < trialCount) {
        Run inner = new Run(outer.gridMarks, true);
        inner.runDisproof(
            new InnerCallback(callback, score + totalScore / trialCount, trialCount));
        uninterrupted = inner.uninterrupted();
        totalScore += inner.score;
        if (inner.recursiveDisproofs) difficulty = Difficulty.RECURSIVE_DISPROOFS;
      }
      score += totalScore / numEvaluations;
    }
    return new Rating(CURRENT_VERSION, score, uninterrupted, difficulty, improper);
  }

  private static class InnerCallback implements Callback {
    @Nullable private final Callback callback;
    private final double baseScore;
    private final int trialCount;

    InnerCallback(@Nullable Callback callback, double baseScore, int trialCount) {
      this.callback = callback;
      this.baseScore = baseScore;
      this.trialCount = trialCount;
    }

    @Override public void updateEstimate(double minScore) {
      if (callback != null)
        callback.updateEstimate(baseScore + minScore / trialCount);
    }

    @Override public void disproofsRequired() {}
  }

  private enum RunStatus { INTERRUPTED, COMPLETE, ERROR, INCONCLUSIVE };

  private class Run {
    final boolean trails;
    GridMarks gridMarks;
    double score;
    RunStatus status;
    boolean foundSolution;
    boolean recursiveDisproofs;

    Run(GridMarks gridMarks, boolean trails) {
      this.gridMarks = gridMarks;
      this.trails = trails;
    }

    void runStraightShot(@Nullable Callback callback) {
      if (status == RunStatus.INTERRUPTED) return;
      status = null;
      Numeral prevNumeral = null;
      do {
        Collector collector = new Collector(gridMarks, prevNumeral, trails);
        prevNumeral = null;
        if (Analyzer.analyze(gridMarks, collector, true)) {
          score += collector.getElapsedMinutes();
          if (callback != null) callback.updateEstimate(score);
          if (collector.hasMove()) {
            Insight move = collector.getMove();
            gridMarks = gridMarks.toBuilder().apply(move).build();
            prevNumeral = move.getImpliedAssignment().numeral;
            if (gridMarks.grid.isSolved()) status = RunStatus.COMPLETE;
          } else if (collector.hasVisibleErrors()) {
            status = RunStatus.ERROR;
          } else if (onlyAmbiguousAssignmentsRemaining()) {
            gridMarks = gridMarks.toBuilder().assign(randomAssignment()).build();
          } else {
            status = RunStatus.INCONCLUSIVE;
          }
        } else {
          status = RunStatus.INTERRUPTED;
        }
      } while (status == null);
    }

    void runDisproof(@Nullable Callback callback) {
      do {
        eliminateOne(callback);
        runStraightShot(callback);
      } while (status == RunStatus.INCONCLUSIVE);
    }

    void eliminateOne(@Nullable Callback callback) {
      GridMarks start = gridMarks;
      if (!recursiveDisproofs) {
        for (Assignment a : shuffledRemainingAssignments()) {
          if (status == RunStatus.INTERRUPTED)
            return;
          if (foundSolution && solutionIntersection.get(a.location) == a.numeral)
            continue;
          gridMarks = start.toBuilder().assign(a).build();
          runStraightShot(callback);
          if (status == RunStatus.ERROR) {
            gridMarks = start.toBuilder().eliminate(a).build();
            return;
          } else if (status == RunStatus.COMPLETE) {
            foundSolution = true;
          }
        }
      }
      recursiveDisproofs = true;
      Assignment impossible = randomErroneousAssignment();
      gridMarks = start.toBuilder().assign(impossible).build();
      runErrorSearch(callback);
      gridMarks = start.toBuilder().eliminate(impossible).build();
    }

    void runErrorSearch(@Nullable Callback callback) {
      runStraightShot(callback);
      if (status != RunStatus.INCONCLUSIVE) return;

      Location loc = randomUnsetLocation(false);
      NumSet nums = gridMarks.marks.get(loc);
      GridMarks start = gridMarks;
      for (Numeral num : nums) {
        gridMarks = start.toBuilder().assign(Assignment.of(loc, num)).build();
        runErrorSearch(callback);
      }
    }

    boolean uninterrupted() {
      return status != RunStatus.INTERRUPTED;
    }

    private boolean onlyAmbiguousAssignmentsRemaining() {
      for (Location loc : Location.all()) {
        if (!gridMarks.grid.containsKey(loc) && solutionIntersection.containsKey(loc))
          return false;
      }
      return true;
    }

    Assignment randomAssignment(Location loc, NumSet nums) {
      return Assignment.of(loc, nums.get(random.nextInt(nums.size())));
    }

    Assignment randomAssignment() {
      Location loc = randomUnsetLocation(false);
      NumSet nums = gridMarks.marks.get(loc);
      return randomAssignment(loc, nums);
    }

    Assignment randomErroneousAssignment() {
      Location loc = randomUnsetLocation(true);
      NumSet nums = gridMarks.marks.get(loc).without(solutionIntersection.get(loc));
      return randomAssignment(loc, nums);
    }

    Location randomUnsetLocation(boolean inIntersectionOnly) {
      int size = 9;
      int count = 0;
      Location currentLoc = null;
      for (Location loc : Location.all()) {
        if (inIntersectionOnly && !solutionIntersection.containsKey(loc)) continue;
        NumSet possible = gridMarks.marks.get(loc);
        if (possible.size() < 2 || possible.size() > size) continue;
        if (possible.size() < size) {
          count = 0;
          size = possible.size();
        }
        // Choose uniformly from locations with the smallest size seen so far.
        if (random.nextInt(++count) == 0)
          currentLoc = loc;
      }
      return currentLoc;
    }

    List<Assignment> shuffledRemainingAssignments() {
      Multimap<Integer, Assignment> byRank = ArrayListMultimap.create();
      for (Location loc : Location.all()) {
        if (gridMarks.grid.containsKey(loc)) continue;
        NumSet nums = gridMarks.marks.get(loc);
        for (Numeral num : nums) {
          int rank = nums.size();
          for (Unit.Type unitType : Unit.Type.values()) {
            UnitSubset locs = gridMarks.marks.get(UnitNumeral.of(loc.unit(unitType), num));
            rank = Math.min(rank, locs.size());
          }
          byRank.put(rank, Assignment.of(loc, num));
        }
      }
      ArrayList<Integer> ranks = Lists.newArrayList(byRank.keySet());
      Collections.sort(ranks);
      List<Assignment> assignments = Lists.newArrayList();
      for (Integer rank : ranks) {
        int start = assignments.size();
        assignments.addAll(byRank.get(rank));
        // Shuffle each rank separately.
        Collections.shuffle(assignments.subList(start, assignments.size()), random);
      }
      return assignments;
    }
  }

  /**
   * A classification of assignment insights based on empirical study of degrees
   * of difficulty of different insights. They are ordered from easiest to
   * hardest.
   */
  private enum MoveKind {
    EASY_DIRECT(1.3, 1.3),
    DIRECT(1.4, 1.4),
    SIMPLY_IMPLIED_EASY(1.7, 1.7),
    SIMPLY_IMPLIED(1.8, 1.8),
    IMPLIED_EASY(3.4, 3.4),
    IMPLIED(4.0, 4.0),  // catch-all, including errors
    ;

    /**
     * How many minutes it takes on average to scan one "point" for this kind of
     * move.
     *
     * <p> The number of "points" at any given grid position is defined as the
     * number of potential scan targets divided by the number of targets covered
     * by one or more insights.  The number of potential scan targets is 4 times
     * the number of open locations.
     *
     * <p> The move kind chosen for a given grid is the maximum move kind for
     * all the locations that have assignment insights. And the move kind for a
     * given location is the minimum move kind for all the insights that assign
     * a numeral to that location.
     */
    public final double minutesPerScanPoint;
    public final double minutesPerScanPointWithTrails;

    public static final MoveKind MIN = EASY_DIRECT;
    public static final MoveKind MAX = IMPLIED;

    /**
     * When there is a direct block-numeral move using the same numeral as the
     * previous move, we count the scan points as the open block-numeral moves
     * remaining for that numeral, and this is the scan rate for them.
     */
    public static final double BLOCK_NUMERAL_MINUTES_PER_SCAN_POINT = 0.75 / 60;

    /**
     * When there are no moves implied, the best model is simply to pause a
     * fixed amount of time before looking for disproofs.
     */
    public static final double MINUTES_BEFORE_DISPROOF = 83.2 / 60;
    public static final double MINUTES_BEFORE_DISPROOF_WITH_TRAILS = 83.2 / 60;

    private MoveKind(double secondsPerScanPoint, double secondsPerScanPointWithTrails) {
      this.minutesPerScanPoint = secondsPerScanPoint / 60;
      this.minutesPerScanPointWithTrails = secondsPerScanPointWithTrails / 60;
    }
  }

  public static class Collector implements Analyzer.Callback {
    private final GridMarks gridMarks;
    @Nullable private final Numeral prevNumeral;
    private final boolean trails;
    private final LocSet locTargets = new LocSet();
    private final UnitNumSet unitNumTargets = new UnitNumSet();
    private final Map<Location, MoveKind> kinds = Maps.newHashMap();
    private MoveKind best = null;
    @Nullable private Insight move;
    @Nullable private ForcedLoc blockNumeralMove;
    private boolean errors;
    private int numDirectMoves;
    private int numDirectErrors;
    private int numBlockNumeralMoves;

    public Collector(GridMarks gridMarks, @Nullable Numeral prevNumeral, boolean trails) {
      this.gridMarks = gridMarks;
      this.prevNumeral = prevNumeral;
      this.trails = trails;
    }

    @Override public void take(Insight insight) throws StopException {
      Assignment a = insight.getImpliedAssignment();
      if (a != null) {
        insight.addScanTargets(locTargets, unitNumTargets);
        MoveKind kind = kindForInsight(insight, kinds.get(a.location));
        kinds.put(a.location, kind);
        if (best == null || kind.compareTo(best) < 0) {
          best = kind;
          move = insight;
        }
        if (insight.type.isAssignment())
          ++numDirectMoves;
        if (insight.type == Insight.Type.FORCED_LOCATION) {
          ForcedLoc fl = (ForcedLoc) insight;
          if (fl.getUnit().getType() == Unit.Type.BLOCK) {
            ++numBlockNumeralMoves;
            if (fl.getNumeral() == prevNumeral)
              blockNumeralMove = fl;
          }
        }
      } else if (insight.isError()) {
        insight.addScanTargets(locTargets, unitNumTargets);
        errors = true;
        if (insight.type.isError())
          ++numDirectErrors;
      }
    }

    public double getElapsedMinutes() {
      if (blockNumeralMove != null) {
        int openMoves = 0;
        for (Block b : Block.all()) {
          UnitSubset locs = gridMarks.marks.get(UnitNumeral.of(b, prevNumeral));
          if (locs.size() > 1 || locs.size() == 1 && !gridMarks.grid.containsKey(locs.get(0)))
            ++openMoves;
        }
        return MoveKind.BLOCK_NUMERAL_MINUTES_PER_SCAN_POINT * openMoves / numBlockNumeralMoves;
      }
      if (move == null)
        return trails ? MoveKind.MINUTES_BEFORE_DISPROOF_WITH_TRAILS : MoveKind.MINUTES_BEFORE_DISPROOF;
      MoveKind kind = errors || kinds.isEmpty()
          ? MoveKind.MAX : Ordering.<MoveKind>natural().max(kinds.values());
      int openLocs = Location.COUNT - gridMarks.grid.size();
      double totalScanPoints = 4.0 * openLocs;
      int scanTargets = locTargets.size() + unitNumTargets.size();
      double scanPoints = totalScanPoints / scanTargets;
      double minutesPerScanPoint = trails ? kind.minutesPerScanPointWithTrails : kind.minutesPerScanPoint;
      return minutesPerScanPoint * scanPoints * move.getScanTargetCount();
    }

    public boolean hasMove() {
      return move != null;
    }

    public Insight getMove() {
      return blockNumeralMove == null ? move : blockNumeralMove;
    }

    public boolean hasVisibleErrors() {
      return numDirectErrors > numDirectMoves;
    }

    /**
     * Returns the appropriate MoveKind for the given insight within the given
     * grid.  If max is given, it is the worst (most expensive) kind that
     * should be returned.
     */
    private MoveKind kindForInsight(Insight insight, @Nullable MoveKind max) {
      if (max == MoveKind.MIN) return max;
      if (max == null) max = MoveKind.MAX;
      switch (insight.type) {
        case FORCED_LOCATION:
          return MoveKind.EASY_DIRECT;
        case FORCED_NUMERAL: {
          ForcedNum fn = (ForcedNum) insight;
          return isEasy(fn) ? MoveKind.EASY_DIRECT : MoveKind.DIRECT;
        }
        case IMPLICATION: {
          if (max.compareTo(MoveKind.DIRECT) <= 0) return max;
          Implication i = (Implication) insight;
          boolean easy = isEasy(i.getNub());
          MoveKind kind = isSimple(i, max)
              ? easy ? MoveKind.SIMPLY_IMPLIED_EASY : MoveKind.SIMPLY_IMPLIED
              : easy ? MoveKind.IMPLIED_EASY : MoveKind.IMPLIED;
          return Ordering.<MoveKind>natural().min(kind, max);
        }
        default:
          return max;
      }
    }

    /**
     * Tells whether the given forced numeral is an easy one.
     */
    private boolean isEasy(ForcedNum fn) {
      // Our current definition of easy: there are at most 2 open locations
      // in the block (not counting the target location), and the numerals
      // assigned to at most one of the row or column are required to force the
      // target numeral.
      Location target = fn.getLocation();
      int openInBlock = 0;
      NumSet inBlock = NumSet.NONE;
      for (Location loc : target.block) {
        if (loc == target) continue;
        if (gridMarks.grid.containsKey(loc))
          inBlock = inBlock.with(gridMarks.grid.get(loc));
        else
          ++openInBlock;
      }
      if (openInBlock > 2) return false;
      NumSet inRow = inLine(target.row, target).minus(inBlock);
      NumSet inCol = inLine(target.column, target).minus(inBlock);
      return inRow.minus(inCol).isEmpty() || inCol.minus(inRow).isEmpty();
    }

    private NumSet inLine(Unit unit, Location target) {
      NumSet answer = NumSet.NONE;
      for (Location loc : unit)
        if (loc.block != target.block && gridMarks.grid.containsKey(loc))
          answer = answer.with(gridMarks.grid.get(loc));
      return answer;
    }

    /**
     * Tells whether the given insight is an easy assignment.
     */
    private boolean isEasy(Insight insight) {
      switch (insight.type) {
        case FORCED_LOCATION: return true;
        case FORCED_NUMERAL: return isEasy((ForcedNum) insight);
        default: return false;
      }
    }

    /**
     * Tells whether the given implication is a simple one.  Skips the test
     * if max is already in the simple range.  May minimize the implication
     * as part of its work.
     */
    private boolean isSimple(Implication i, MoveKind max) {
      if (max.compareTo(MoveKind.SIMPLY_IMPLIED) <= 0) return true;
      return minimizeForSimplicityTest(gridMarks, i) != null;
    }
  }

  private static boolean isSimpleAntecedent(Insight insight) {
    switch (insight.type) {
      case OVERLAP:
        break;
      case LOCKED_SET: {
        LockedSet s = (LockedSet) insight;
        if (s.isNakedSet() || s.getLocations().unit.getType() != Unit.Type.BLOCK
            || s.getLocations().size() > 3)
          return false;
        break;
      }
      default: return false;
    }
    return true;
  }

  @Nullable private static Insight minimizeForSimplicityTest(GridMarks gridMarks, Implication implication) {
    Insight consequent = implication.getConsequent();
    if (consequent instanceof Implication) {
      consequent = minimizeForSimplicityTest(
          gridMarks.toBuilder().apply(implication.getAntecedents()).build(),
          (Implication) consequent);
      if (consequent == null) return null;
    }

    List<Insight> simpleAntecedents = Lists.newArrayList();
    for (Insight a : implication.getAntecedents())
      if (isSimpleAntecedent(a))
        simpleAntecedents.add(a);
    GridMarks simpleOnly = gridMarks.toBuilder().apply(simpleAntecedents).build();
    if (consequent.isImpliedBy(simpleOnly))
      return new Implication(simpleAntecedents, consequent);
    return null;
  }
}
