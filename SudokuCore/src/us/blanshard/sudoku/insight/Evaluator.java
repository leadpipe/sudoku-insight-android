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
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.insight.Analyzer.StopException;
import us.blanshard.sudoku.insight.Rating.Difficulty;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Methods to evaluate the difficulty of a Sudoku.
 */
public class Evaluator {

  /** The current version of the time estimation algorithm. */
  public static final int CURRENT_VERSION = 6;

  /** How many times we run the evaluator to come up with our estimate. */
  public static final int NUM_TRIALS = 10;

  /**
   * Called back periodically from {@link #evaluate} with updates to the
   * estimated score.
   */
  public interface Callback {
    /** Returns a monotonically increasing score during the evaluation. */
    void updateEstimate(double minScore);
  }

  /**
   * Evaluates the difficulty of a Sudoku puzzle, which must be solvable and
   * have no more than a few solutions (or the method will throw).  The provided
   * callback may be given updates during the evaluation; these will be
   * monotonically increasing.  If the thread is interrupted, the evaluation may
   * stop early, and in that case the result object will be marked as
   * incomplete.
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
    double[] scores = new double[trialCount];
    GridMarks gridMarks = new GridMarks(puzzle);
    int numOpen = puzzle.getNumOpenLocations();
    boolean uninterrupted = true;
    double totalScore = 0;
    int[] difficultyCounts = new int[Difficulty.values().length];
    int numEvaluations = 0;
    while (uninterrupted && numEvaluations++ < trialCount) {
      Run run = new Run(gridMarks, numOpen);
      run.run(new InnerCallback(callback, totalScore / trialCount, trialCount));
      uninterrupted = run.uninterrupted();
      scores[numEvaluations - 1] = run.score;
      totalScore += run.score;
      ++difficultyCounts[run.difficulty.ordinal()];
    }
    double score = totalScore / numEvaluations;
    double variance = 0;
    for (double s : scores) {
      double error = s - score;
      variance += error * error;
    }
    variance /= trialCount;
    int maxDifficultyCount = 0;
    int maxDifficultyIndex = -1;
    for (int i = 0; i < difficultyCounts.length; ++i) {
      if (difficultyCounts[i] > maxDifficultyCount) {
        maxDifficultyCount = difficultyCounts[i];
        maxDifficultyIndex = i;
      }
    }
    Difficulty difficulty = Difficulty.values()[maxDifficultyIndex];
    return new Rating(CURRENT_VERSION, score, Math.sqrt(variance),
                      uninterrupted, difficulty, improper);
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
  }

  private enum RunStatus { INTERRUPTED, COMPLETE, ERROR, INCONCLUSIVE };
  private static final Analyzer.Options OPTS = new Analyzer.Options(false, false);

  private class Run {
    GridMarks gridMarks;
    int minOpen;
    int numOpen;
    double score;
    RunStatus status;
    boolean foundSolution;
    Difficulty difficulty = Difficulty.NO_DISPROOFS;

    Run(GridMarks gridMarks, int numOpen) {
      this.gridMarks = gridMarks;
      this.minOpen = numOpen;
      this.numOpen = numOpen;
    }

    void run(@Nullable Callback callback) {
      runStraightShot(callback);
      while (status == RunStatus.INCONCLUSIVE) {
        eliminateOne(callback);
        runStraightShot(callback);
      }
      if (status == RunStatus.ERROR) throw new IllegalStateException();
    }

    private void runStraightShot(@Nullable Callback callback) {
      if (status == RunStatus.INTERRUPTED) return;
      status = null;
      do {
        Collector collector = new Collector(gridMarks, minOpen, numOpen, random);
        if (Analyzer.analyze(gridMarks, collector, OPTS)) {
          score += collector.getElapsedMinutes();
          if (callback != null) callback.updateEstimate(score);
          if (collector.assignmentsWon()) {
            GridMarks.Builder builder = gridMarks.toBuilder();
            for (Assignment a : collector.getAssignments())
              builder.assign(a);
            setGridMarks(builder);
            if (gridMarks.grid.isSolved()) status = RunStatus.COMPLETE;
          } else if (collector.errorsWon()) {
            status = RunStatus.ERROR;
          } else if (collector.noPlaysPossible() && onlyAmbiguousAssignmentsRemaining()) {
            setGridMarks(gridMarks.toBuilder().assign(randomAssignment()));
          } else {
            status = RunStatus.INCONCLUSIVE;
          }
        } else {
          status = RunStatus.INTERRUPTED;
        }
      } while (status == null);
    }

    private void setGridMarks(GridMarks.Builder builder) {
      gridMarks = builder.build();
      numOpen = gridMarks.grid.getNumOpenLocations();
      if (numOpen < minOpen) minOpen = numOpen;
    }

    private void eliminateOne(@Nullable Callback callback) {
      GridMarks start = gridMarks;
      switch (difficulty) {
        case NO_DISPROOFS:
          difficulty = Difficulty.SIMPLE_DISPROOFS;
          // Fall through:
        case SIMPLE_DISPROOFS:
          List<Assignment> remaining = shuffledRemainingAssignments();
          if (remaining.isEmpty()) {
            // This means that there are errors or forced numerals on the board.
            // Just go back and retry a straight shot.
            return;
          }
          for (Assignment a : remaining) {
            if (status == RunStatus.INTERRUPTED)
              return;
            if (foundSolution && solutionIntersection.get(a.location) == a.numeral)
              continue;
            setGridMarks(start.toBuilder().assign(a));
            runStraightShot(callback);
            if (status == RunStatus.ERROR) {
              setGridMarks(start.toBuilder().eliminate(a));
              return;
            } else if (status == RunStatus.COMPLETE) {
              foundSolution = true;
            }
          }
          // If we make it here, we've exhausted the simple disproofs.
          difficulty = Difficulty.RECURSIVE_DISPROOFS;
          setGridMarks(start.toBuilder());
          // Fall through:
        case RECURSIVE_DISPROOFS:
          Assignment impossible = randomErroneousAssignment();
          if (impossible == null) return;
          setGridMarks(start.toBuilder().assign(impossible));
          runErrorSearch(callback);
          setGridMarks(start.toBuilder().eliminate(impossible));
      }
    }

    private void runErrorSearch(@Nullable Callback callback) {
      runStraightShot(callback);
      if (status != RunStatus.INCONCLUSIVE) return;

      Location loc = randomUnsetLocation(false);
      NumSet nums = gridMarks.marks.get(loc);
      GridMarks start = gridMarks;
      for (Numeral num : nums) {
        setGridMarks(start.toBuilder().assign(Assignment.of(loc, num)));
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

    @Nullable Assignment randomErroneousAssignment() {
      Location loc = randomUnsetLocation(true);
      if (loc == null) return null;
      NumSet nums = gridMarks.marks.get(loc).without(solutionIntersection.get(loc));
      return randomAssignment(loc, nums);
    }

    @Nullable Location randomUnsetLocation(boolean inIntersectionOnly) {
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
      List<Assignment> assignments = Lists.newArrayList();
      for (Location loc : Location.all()) {
        if (gridMarks.grid.containsKey(loc)) continue;
        NumSet nums = gridMarks.marks.get(loc);
        if (nums.size() < 2) return assignments;  // it's empty at this point
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
   * A classification of the atomic insights based on empirical study of the
   * likelihood of playing different insights.
   */
  public enum Pattern {
    CONFLICT_B(0.078, 0.049, 0.14),
    CONFLICT_L(0.15, 0.16, 0.16),
    BARRED_LOC_5(0.20, 0.28, 0.40),
    BARRED_LOC_4(0.20, 0.28, 0.28),
    BARRED_LOC_3(0.20, 0.22, 0.12),
    BARRED_LOC_2(0.20, 0.19, 0.18),
    BARRED_LOC_1(0.24, 0.11, 0.16),
    BARRED_LOC_0(0.20, 0.13, 0.094),
    BARRED_NUM_B(0.26, 0.26, 0.23),
    BARRED_NUM_L(0.24, 0.22, 0.21),
    FORCED_LOC_B(0.41, 0.41, 0.50),
    FORCED_LOC_L(0.32, 0.30, 0.40),
    FORCED_NUM_6(0.24, 0.47, 0.86),
    FORCED_NUM_5(0.24, 0.47, 0.71),
    FORCED_NUM_4(0.24, 0.47, 0.38),
    FORCED_NUM_3(0.24, 0.31, 0.10),
    FORCED_NUM_2(0.24, 0.18, 0.086),
    FORCED_NUM_1(0.22, 0.050, 0.040),
    FORCED_NUM_0(0.22, 0.039, 0.029),
    OVERLAP_B(0.37, 0.88, 0.90),
    OVERLAP_L(0.54, 0.91, 0.90),
    HIDDEN_SET_B_2_OVERLAP(0.26, 0.37, 0.53),
    HIDDEN_SET_L_2_OVERLAP(0.28, 0.32, 0.36),
    HIDDEN_SET_B_2_DIFFUSE(0.16, 0.30, 0.38),
    HIDDEN_SET_L_2_DIFFUSE(0.13, 0.18, 0.19),
    HIDDEN_SET_B_3_OVERLAP(0.28, 0.28, 0.31),
    HIDDEN_SET_L_3_OVERLAP(0.30, 0.41, 0.39),
    HIDDEN_SET_B_3_DIFFUSE(0.11, 0.11, 0.19),
    HIDDEN_SET_L_3_DIFFUSE(0.15, 0.16, 0.13),
    HIDDEN_SET_B_4_OVERLAP(0.28, 0.44, 0.33),
    HIDDEN_SET_L_4_OVERLAP(0.30, 0.53, 0.53),
    HIDDEN_SET_B_4_DIFFUSE(0.11, 0.058, 0.10),
    HIDDEN_SET_L_4_DIFFUSE(0.20, 0.21, 0.13),
    NAKED_SET_B_2(0.75, 2.1, 1.9),
    NAKED_SET_L_2(0.45, 1.6, 1.6),
    NAKED_SET_B_3(0.84, 3.1, 2.9),
    NAKED_SET_L_3(0.43, 2.1, 2.5),
    NAKED_SET_B_4(0.84, 3.6, 4.6),
    NAKED_SET_L_4(0.77, 2.4, 3.4),
    ;

    /**
     * The "weight" of an insight matching this pattern with the given number of
     * open squares in the grid.  For assignments, this is the probability of
     * playing the move; for errors, it's the probability of seeing the error;
     * and for eliminations it's a multiplier used to calculate the probability
     * of an implication in which the elimination is an antecedent.
     */
    public double getWeight(int numOpen) {
      return weights[Math.min(2, numOpen / 20)];
    }

    /**
     * Returns the pattern corresponding to the given insight within the given
     * grid.  Throws for non-atomic insights.
     */
    public static Pattern forInsight(Insight insight, Grid grid) {
      switch (insight.type) {
        case CONFLICT: {
          Conflict i = (Conflict) insight;
          return isBlock(i.getLocations().unit) ? CONFLICT_B : CONFLICT_L;
        }
        case BARRED_LOCATION: {
          BarredLoc i = (BarredLoc) insight;
          switch (getMaxDeltaAboveAverage(i.getLocation(), grid)) {
            default:
            case 5: return BARRED_LOC_5;
            case 4: return BARRED_LOC_4;
            case 3: return BARRED_LOC_3;
            case 2: return BARRED_LOC_2;
            case 1: return BARRED_LOC_1;
            case 0: return BARRED_LOC_0;
          }
        }
        case BARRED_NUMERAL: {
          BarredNum i = (BarredNum) insight;
          return isBlock(i.getUnit()) ? BARRED_NUM_B : BARRED_NUM_L;
        }
        case FORCED_LOCATION: {
          ForcedLoc i = (ForcedLoc) insight;
          return isBlock(i.getUnit()) ? FORCED_LOC_B : FORCED_LOC_L;
        }
        case FORCED_NUMERAL: {
          ForcedNum i = (ForcedNum) insight;
          switch (getMaxDeltaAboveAverage(i.getLocation(), grid)) {
            default:
            case 6: return FORCED_NUM_6;
            case 5: return FORCED_NUM_5;
            case 4: return FORCED_NUM_4;
            case 3: return FORCED_NUM_3;
            case 2: return FORCED_NUM_2;
            case 1: return FORCED_NUM_1;
            case 0: return FORCED_NUM_0;
          }
        }
        case OVERLAP: {
          Overlap i = (Overlap) insight;
          return isBlock(i.getUnit()) ? OVERLAP_B : OVERLAP_L;
        }
        case LOCKED_SET: {
          LockedSet i = (LockedSet) insight;
          boolean block = isBlock(i.getLocations().unit);
          if (i.isHiddenSet()) {
            boolean overlap = isOverlapped(i, grid);
            switch (i.getNumerals().size()) {
              case 2: return overlap
                  ? block ? HIDDEN_SET_B_2_OVERLAP : HIDDEN_SET_L_2_OVERLAP
                  : block ? HIDDEN_SET_B_2_DIFFUSE : HIDDEN_SET_L_2_DIFFUSE;
              case 3: return overlap
                  ? block ? HIDDEN_SET_B_3_OVERLAP : HIDDEN_SET_L_3_OVERLAP
                  : block ? HIDDEN_SET_B_3_DIFFUSE : HIDDEN_SET_L_3_DIFFUSE;
              default:
              case 4: return overlap
                  ? block ? HIDDEN_SET_B_4_OVERLAP : HIDDEN_SET_L_4_OVERLAP
                  : block ? HIDDEN_SET_B_4_DIFFUSE : HIDDEN_SET_L_4_DIFFUSE;
            }
          } else {
            switch (i.getNumerals().size()) {
              case 2: return block ? NAKED_SET_B_2 : NAKED_SET_L_2;
              case 3: return block ? NAKED_SET_B_3 : NAKED_SET_L_3;
              default:
              case 4: return block ? NAKED_SET_B_4 : NAKED_SET_L_4;
            }
          }
        }
        default:
          throw new IllegalArgumentException("Atomic insights only, got " + insight.type);
      }
    }

    private final double[] weights;

    private Pattern(double... weights) {
      this.weights = weights;
    }

    private static boolean isBlock(Unit unit) {
      return unit.getType() == Unit.Type.BLOCK;
    }

    /**
     * Finds the unit connected to the given location with the largest number of
     * assignments, returns the difference between that number and the average
     * number of assignments per unit.  The larger that delta, the likelier the
     * given insight is to be played.
     */
    private static int getMaxDeltaAboveAverage(Location loc, Grid grid) {
      int maxSet = 0;
      for (Unit.Type unitType : Unit.Type.values()) {
        maxSet = Math.max(maxSet, numSetInUnit(loc.unit(unitType), grid));
      }
      int averageSetPerUnit = grid.size() / 9;
      return Math.max(0, maxSet - averageSetPerUnit);
    }

    private static int numSetInUnit(Unit unit, Grid grid) {
      int answer = 0;
      for (Location loc : unit)
        if (grid.containsKey(loc))
          ++answer;
      return answer;
    }

    /**
     * For a hidden set, overlapped means that all open squares in the set's
     * unit but not in the set lie in an overlapping unit, and all numerals in
     * the set appear in this other unit.  These sets are typically much easier
     * to find than other sets.
     */
    private static boolean isOverlapped(LockedSet set, Grid grid) {
      boolean isOverlapped = false;
      UnitSubset taken = set.getLocations();
      for (Location loc : taken.unit) {
        if (grid.containsKey(loc))
          taken = taken.with(loc);
      }
      UnitSubset open = taken.not();
      Unit overlap = Analyzer.findOverlappingUnit(open);
      if (overlap == null && open.size() == 1) {
        Location loc = open.get(0);
        if (isBlock(open.unit)) {
          isOverlapped = minusAllInUnit(set.getNumerals(), loc.row, grid).isEmpty()
              || minusAllInUnit(set.getNumerals(), loc.column, grid).isEmpty();
        } else {
          overlap = loc.block;
        }
      }
      if (overlap != null) {
        isOverlapped = minusAllInUnit(set.getNumerals(), overlap, grid).isEmpty();
      }
      return isOverlapped;
    }

    /**
     * Returns the difference between the given set and all numerals assigned to
     * locations within the given unit, in the given grid.
     */
    private static NumSet minusAllInUnit(NumSet nums, Unit unit, Grid grid) {
      for (Location loc : unit)
        if (grid.containsKey(loc))
          nums = nums.without(grid.get(loc));
      return nums;
    }
  }

  /**
   * Calculates the probability of playing/seeing the given insight within the
   * given grid.  Only works for atomic insights and implications.
   */
  public static double getProbability(Insight insight, Grid grid) {
    return getProbability(insight, grid, grid.getNumOpenLocations());
  }

  private static double getProbability(Insight insight, Grid grid, int numOpen) {
    if (insight.type != Insight.Type.IMPLICATION) {
      return Pattern.forInsight(insight, grid).getWeight(numOpen);
    }
    Implication imp = (Implication) insight;
    double answer = getProbability(imp.getConsequent(), grid, numOpen);
    for (Insight a : imp.getAntecedents())
      answer *= Pattern.forInsight(a, grid).getWeight(numOpen);
    return answer;
  }

  /** A simple mutable double class. */
  private static class Weight {
    double weight;
  }

  public static class Collector implements Analyzer.Callback {
    private final GridMarks gridMarks;
    private final int numOpen;
    private final int minOpen;
    private final Random random;
    // Keys of 'weights', and members of 'played', are Assignments and the ERRORS object.
    private final Map<Object, Weight> weights = Maps.newHashMap();
    private final Set<Object> played = Sets.newLinkedHashSet();
    private static final Object ERRORS = new Object();
    private double totalWeight;
    private static final double[] TRAILHEAD_SECONDS = {0.821, 1.225, 1.528, 1.839, 2.080, 2.480};
    private static final double[] PLAYED_SECONDS    = {0.819, 0.785, 0.889, 0.967, 1.165, 1.355};

    public Collector(GridMarks gridMarks, int numOpen, int minOpen, Random random) {
      this.gridMarks = gridMarks;
      this.numOpen = numOpen;
      this.minOpen = minOpen;
      this.random = random;
    }

    @Override public void take(Insight insight) throws StopException {
      Assignment a = insight.getImpliedAssignment();
      if (a == null && !insight.isError()) return;
      insight = Analyzer.minimize(gridMarks, insight);
      double p = getProbability(insight, gridMarks.grid, numOpen);
      totalWeight += p;
      Object key = a == null ? ERRORS : a;
      Weight w = weights.get(key);
      if (w == null) weights.put(key, w = new Weight());
      w.weight += p;
      if (random.nextDouble() < p) {
        played.add(key);
      }
    }

    public double getElapsedMinutes() {
      int index = Math.min(5, minOpen / 10);
      if (played.isEmpty()) {
        // No move nor error was played.  We'll start a trail.
        return TRAILHEAD_SECONDS[index] * numOpen;
      }

      double playedWeight = 0;
      for (Object key : played)
        playedWeight += weights.get(key).weight;
      return PLAYED_SECONDS[index] * numOpen * playedWeight / totalWeight;
    }

    /**
     * True when there were assignments played, and no errors.
     */
    public boolean assignmentsWon() {
      return played.size() > 0 && !played.contains(ERRORS);
    }

    public Iterable<Assignment> getAssignments() {
      return Iterables.filter(played, Assignment.class);
    }

    /**
     * True when there were any errors played.
     */
    public boolean errorsWon() {
      return played.contains(ERRORS);
    }

    /** True when no assignments nor errors were found. */
    public boolean noPlaysPossible() {
      return totalWeight == 0;
    }
  }
}
