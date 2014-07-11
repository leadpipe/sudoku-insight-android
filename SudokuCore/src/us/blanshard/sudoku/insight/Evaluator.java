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

import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    Difficulty difficulty = Difficulty.NO_DISPROOFS;
    int numEvaluations = 0;
    while (uninterrupted && numEvaluations++ < trialCount) {
      Run run = new Run(gridMarks, numOpen);
      run.run(new InnerCallback(callback, totalScore / trialCount, trialCount));
      uninterrupted = run.uninterrupted();
      scores[numEvaluations - 1] = run.score;
      totalScore += run.score;
      if (run.difficulty.ordinal() > difficulty.ordinal())
        difficulty = run.difficulty;
    }
    double score = totalScore / numEvaluations;
    double variance = 0;
    for (double s : scores) {
      double error = s - score;
      variance += error * error;
    }
    variance /= trialCount;
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
  private static final Analyzer.Options OPTS = new Analyzer.Options(false, true);

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
          } else if (onlyAmbiguousAssignmentsRemaining()) {
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
          for (Assignment a : shuffledRemainingAssignments()) {
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
          // Fall through:
        case RECURSIVE_DISPROOFS:
          Assignment impossible = randomErroneousAssignment();
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
   * A classification of insights based on empirical study of degrees of
   * difficulty of different insights.  They are ordered from easiest to
   * hardest.
   */
  public enum MoveKind {
    DIRECT_EASY(0, 0, 0.32, 0.34, 0.41, 0.44, 0.45, 0.41),
    SIMPLY_IMPLIED_EASY(0, 1, 0.51, 0.67, 0.56, 0.84, 0.94, 1.05),
    COMPLEXLY_IMPLIED_EASY(0, 2),
    DIRECT_HARD(1, 0),
    SIMPLY_IMPLIED_HARD(1, 1),
    COMPLEXLY_IMPLIED_HARD(1, 2),
    ;

    public static final MoveKind MIN = DIRECT_EASY;
    public static final MoveKind MAX = COMPLEXLY_IMPLIED_HARD;

    /**
     * How many minutes it takes on average to scan one "point" for this kind of
     * move, for the given minimum number of open locations.
     *
     * <p> The number of "points" at any given grid position is defined as the
     * number of potential scan targets divided by the number of targets covered
     * by one or more insights.  The number of potential scan targets is 4 times
     * the number of open locations.
     *
     * <p> This method throws if {@link #shouldPlay} returns false.
     */
    public double minutesPerScanPoint(int minOpen) {
      return toMinutes(secondsPerScanPoint, minOpen);
    }

    /**
     * Tells whether an insight of this kind should be played.  If not, the evaluator
     * should move on to a disproof.
     */
    public boolean isPlayable() {
      return secondsPerScanPoint != null;
    }

    /**
     * When there are no moves implied, the best model is simply to pause a
     * fixed amount of time before looking for disproofs.
     */
    public static double minutesBeforeDisproof(int minOpen) {
      return toMinutes(secondsBeforeTrailhead, minOpen);
    }

    public Integer partialCompare(MoveKind that) {
      if (this.difficulty < that.difficulty)
        return this.complexity <= that.complexity ? -1 : null;
      if (this.difficulty > that.difficulty)
        return this.complexity >= that.complexity ? 1 : null;
      return this.complexity - that.complexity;
    }

    private final int difficulty;
    private final int complexity;
    private final double[] secondsPerScanPoint;

    private static final double[] secondsBeforeTrailhead = {
      32.0, 44.9, 55.8, 74.3, 97.0, 130.7
    };

    private static double toMinutes(double[] seconds, int minOpen) {
      int index = Math.min(minOpen / 10, seconds.length - 1);
      return seconds[index] / 60.0;
    }

    private MoveKind(int difficulty, int complexity, double... secondsPerScanPoint) {
      this.difficulty = difficulty;
      this.complexity = complexity;
      this.secondsPerScanPoint = secondsPerScanPoint.length > 0 ? secondsPerScanPoint : null;
    }
  }

  public static final PartialComparator<MoveKind> KIND_COMPARE = new PartialComparator<MoveKind>() {
    @Override public Integer partialCompare(MoveKind a, MoveKind b) {
      return a.partialCompare(b);
    }
  };
  public static final PartialComparator<MoveKind> KIND_REVERSE = PartialComparators.reverse(KIND_COMPARE);

  public static class Collector implements Analyzer.Callback {
    private final GridMarks gridMarks;
    private final int numOpen;
    private final int minOpen;
    private final Random random;
    private final LocSet locTargets = new LocSet();
    private final UnitNumSet unitNumTargets = new UnitNumSet();
    private final Map<Assignment, MoveKind> kinds = Maps.newHashMap();
    private final Multimap<Assignment, Insight> moves = ArrayListMultimap.create();
    private final Set<Assignment> assignments = Sets.newLinkedHashSet();
    private MoveKind bestMoveKind;
    private int realmWeight;
    private int[] numeralCounts;
    private MoveKind bestErrorKind;
    private int numMoveInsights;
    private int numErrors;

    public Collector(GridMarks gridMarks, int numOpen, int minOpen, Random random) {
      this.gridMarks = gridMarks;
      this.numOpen = numOpen;
      this.minOpen = minOpen;
      this.random = random;
    }

    @Override public void take(Insight insight) throws StopException {
      insight = Analyzer.minimize(gridMarks, insight);
      insight.addScanTargets(locTargets, unitNumTargets);
      Assignment a = insight.getImpliedAssignment();
      if (a != null) {
        moves.put(a, insight);
        MoveKind kind = kindForInsight(gridMarks.grid, insight, kinds.get(a));
        if (kind.isPlayable()) {
          kinds.put(a, kind);
          if (bestMoveKind == null || kind.compareTo(bestMoveKind) < 0) {
            bestMoveKind = kind;
            numMoveInsights = 0;
            realmWeight = 0;
          }
          if (kind == bestMoveKind) {
            ++numMoveInsights;
            int w = realmWeights[insight.getNub().getRealmVector()];
            if (w > realmWeight) {
              realmWeight = w;
              numeralCounts = new int[Numeral.COUNT];
              assignments.clear();
            }
            if (w == realmWeight) {
              if (assignments.add(a))
                ++numeralCounts[a.numeral.index];
            }
          }
        }
      } else if (insight.isError()) {
        MoveKind kind = kindForInsight(gridMarks.grid, insight);
        if (kind.isPlayable()) {
          if (bestErrorKind == null || kind.compareTo(bestErrorKind) < 0) {
            bestErrorKind = kind;
            numErrors = 0;
          }
          if (kind == bestErrorKind)
            ++numErrors;
        }
      }
    }

    public double getElapsedMinutes() {
      if (assignmentsWon()) {
        // We have one or more assignments.  The elapsed minutes is calculated as:
        //  - the total number of scan points (4 * #open locations)
        //  - times the number of scan targets in the assignments' insights
        //  - divided by the total number of scan targets in all insights
        //  - times the number of minutes per scan point
        LocSet locs = new LocSet();
        UnitNumSet unitNums = new UnitNumSet();
        for (Assignment a : assignments)
          for (Insight i : moves.get(a))
            i.addScanTargets(locs, unitNums);
        return 4 * numOpen
            * (locs.size() + unitNums.size())
            / (locTargets.size() + unitNumTargets.size())
            * bestMoveKind.minutesPerScanPoint(minOpen);
      }

      if (errorsWon()) {
        // Errors.  Calculate elapsed time in a similar way as above, except
        // the number of scan targets is taken as 1 (because if errors won
        // they are direct).
        return 4 * numOpen
            / (locTargets.size() + unitNumTargets.size())
            * bestErrorKind.minutesPerScanPoint(minOpen);
      }

      // Otherwise, we start a trail.
      return MoveKind.minutesBeforeDisproof(minOpen);
    }

    /**
     * True when there were playable assignments found, and any errors aren't
     * too easy or too numerous.
     */
    public boolean assignmentsWon() {
      return bestMoveKind != null
          && (bestErrorKind != MoveKind.DIRECT_EASY || numMoveInsights >= numErrors);
    }

    public Iterable<Assignment> getAssignments() {
      List<Numeral> numerals = Lists.newArrayList(Numeral.all());
      Collections.sort(numerals, new Comparator<Numeral>() {
        @Override public int compare(Numeral o1, Numeral o2) {
          return numeralCounts[o2.index] - numeralCounts[o1.index];
        }
      });
      NumSet n = NumSet.NONE;
      int total = 0;
      for (Numeral num : numerals) {
        n = n.with(num);
        int c = numeralCounts[num.index];
        total += c;
        if (c == 0 || total > 5) break;
      }
      final NumSet include = n;
      return Iterables.filter(assignments, new Predicate<Assignment>() {
        @Override public boolean apply(@Nullable Assignment input) {
          return include.contains(input.numeral);
        }
      });
    }

    /**
     * True when there were direct easy errors found, and there are more of them
     * than there are move insights.
     */
    public boolean errorsWon() {
      return bestErrorKind == MoveKind.DIRECT_EASY && numErrors > numMoveInsights;
    }

    private static int[] realmWeights = new int[8];
    static {
      // Larger weights count more, so happen first.
      realmWeights[Insight.Realm.LINE.bit] = 1;
      realmWeights[Insight.Realm.LOCATION.bit] = 2;
      realmWeights[Insight.Realm.BLOCK.bit] = 3;
    }
  }

  public static MoveKind kindForInsight(Grid grid, Insight insight) {
    return kindForInsight(grid, insight, null);
  }

  /**
   * Returns the appropriate MoveKind for the given insight within the given
   * grid.  If max is given, it is the worst (most expensive) kind that should
   * be returned.
   */
  public static MoveKind kindForInsight(Grid grid, Insight insight, @Nullable MoveKind max) {
    if (max == MoveKind.MIN) return max;
    if (max == null) max = MoveKind.MAX;
    switch (insight.type) {
      case CONFLICT:
      case BARRED_LOCATION:
      case BARRED_NUMERAL:
      case FORCED_LOCATION:
      case FORCED_NUMERAL:
        return isEasy(grid, insight) ? MoveKind.DIRECT_EASY : MoveKind.DIRECT_HARD;
      case IMPLICATION: {
        if (max.compareTo(MoveKind.DIRECT_HARD) <= 0) return max;
        Implication imp = insight.asImplication();
        boolean easy = isEasy(grid, imp);
        MoveKind kind = isSimple(imp)
          ? easy ? MoveKind.SIMPLY_IMPLIED_EASY : MoveKind.SIMPLY_IMPLIED_HARD
          : easy ? MoveKind.COMPLEXLY_IMPLIED_EASY : MoveKind.COMPLEXLY_IMPLIED_HARD;
        return Ordering.<MoveKind>natural().min(kind, max);
      }
      case OVERLAP:
      case LOCKED_SET:
      case DISPROVED_ASSIGNMENT:
      case UNFOUNDED_ASSIGNMENT:
        return max;
    }
    throw new AssertionError();
  }

  /**
   * Tells whether an insight that relies on the set of numerals in a location's
   * peers is an easy one.
   */
  private static boolean isEasy(Grid grid, Location target) {
    // Our current definition of easy: there are at least 6 assignments in any
    // unit (not counting the target location); or at most two of the units are
    // required to force the target numeral.
    return isAnyUnitFull(grid, target)
        || areTwoUnitsSufficient(inUnits(grid, target));
  }

  private static int numAssigned(Grid grid, Unit unit, Location target) {
    int answer = 0;
    for (Location loc : unit)
      if (loc != target && grid.containsKey(loc))
        ++answer;
    return answer;
  }

  private static boolean isAnyUnitFull(Grid grid, Location target) {
    return numAssigned(grid, target.block, target) >= 6
        || numAssigned(grid, target.row, target) >= 6
        || numAssigned(grid, target.column, target) >= 6;
  }

  private static NumSet inUnit(Grid grid, Unit unit, Location target) {
    NumSet answer = NumSet.NONE;
    for (Location loc : unit)
      if (loc != target && grid.containsKey(loc))
        answer = answer.with(grid.get(loc));
    return answer;
  }

  private static NumSet[] inUnits(Grid grid, Location target) {
    NumSet[] answer = new NumSet[3];
    answer[Unit.Type.BLOCK.ordinal()] = inUnit(grid, target.block, target);
    answer[Unit.Type.ROW.ordinal()] = inUnit(grid, target.row, target);
    answer[Unit.Type.COLUMN.ordinal()] = inUnit(grid, target.column, target);
    return answer;
  }

  private static boolean areTwoUnitsSufficient(NumSet[] sets) {
    NumSet a = sets[0], b = sets[1], c = sets[2];
    return a.isSubsetOf(b.or(c))
        || b.isSubsetOf(c.or(a))
        || c.isSubsetOf(a.or(b));
  }

  /**
   * Tells whether the given insight is an easy assignment or error.
   */
  private static boolean isEasy(Grid grid, Insight insight) {
    switch (insight.type) {
    case FORCED_LOCATION:
    case BARRED_NUMERAL:
    case CONFLICT:
      return true;

    case FORCED_NUMERAL:
      return isEasy(grid, ((ForcedNum) insight).getLocation());

    case BARRED_LOCATION:
      return isEasy(grid, ((BarredLoc) insight).getLocation());

    default:
      return false;
    }
  }

  /**
   * Tells whether the given insight is an implication that leads to an easy
   * assignment or error, and all antecedents are also easy.
   */
  private static boolean isEasy(Grid grid, Implication imp) {
    if (areAnyAntecedentsHard(imp)) return false;

    Location target;
    Insight nub = imp.getNub();
    switch (nub.type) {
      case FORCED_LOCATION:
      case BARRED_NUMERAL:
      case CONFLICT:
        return true;

      case FORCED_NUMERAL:
        target = ((ForcedNum) nub).getLocation();
        break;

      case BARRED_LOCATION:
        target = ((BarredLoc) nub).getLocation();
        break;

      default:
        return false;
    }

    if (isAnyUnitFull(grid, target)) return true;

    NumSet[] sets = inUnits(grid, target);

    do {
      for (Insight a : imp.getAntecedents()) {
        switch (a.type) {
          case OVERLAP: {
            Overlap o = (Overlap) a;
            Unit u = o.getUnit();
            if (u.contains(target)) {
              int i = u.getType().ordinal();
              sets[i] = sets[i].with(o.getNumeral());
            }
            break;
          }
          case LOCKED_SET: {
            LockedSet s = (LockedSet) a;
            Unit u = s.getLocations().unit;
            if (u.contains(target)) {
              int i = u.getType().ordinal();
              sets[i] = sets[i].or(s.getNumerals());
            }
            break;
          }
          default:
            break;
        }
      }
      imp = imp.getConsequent().asImplication();
    } while (imp != null);

    return areTwoUnitsSufficient(sets);
  }

  private static boolean areAnyAntecedentsHard(Implication imp) {
    do {
      for (Insight a : imp.getAntecedents()) {
        switch (a.type) {
          case LOCKED_SET:
            // At present, we count any naked set as hard, and that's it.
            LockedSet s = (LockedSet) a;
            if (s.isNakedSet())
              return true;
            break;
          case FORCED_NUMERAL:
            return true;
          default:
            break;
        }
      }
      imp = imp.getConsequent().asImplication();
    } while (imp != null);

    return false;
  }

  /**
   * Tells whether the given implication is a simple one.
   */
  private static boolean isSimple(Implication imp) {
    boolean outermost = true;
    while (imp != null) {
      for (Insight a : imp.getAntecedents())
        if (!isSimpleAntecedent(a, outermost)) return false;
      outermost = false;
      imp = imp.getConsequent().asImplication();
    }
    return true;
  }

  private static boolean isSimpleAntecedent(Insight insight, boolean outermost) {
    switch (insight.type) {
      case OVERLAP:
        break;
      case LOCKED_SET: {
        if (!outermost) return false;
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
}
