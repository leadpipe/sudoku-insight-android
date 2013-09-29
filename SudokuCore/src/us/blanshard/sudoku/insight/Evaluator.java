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

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.insight.Analyzer.StopException;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Methods to evaluate the difficulty of a Sudoku.
 */
public class Evaluator {

  /**
   * The current version of the time estimation algorithm.
   */
  public static final int CURRENT_VERSION = 1;

  /**
   * The object returned by the evaluator.
   */
  public static class Result {
    /** The version of the estimation algorithm that was used for this result. */
    public final int algorithmVersion;
    /** How long the puzzle is estimated to take. */
    public final double estimatedAverageSolutionSeconds;
    /** Whether the estimate was complete; if false, the estimate is probably
        less than it would have been if allowed to finish. */
    public final boolean estimateComplete;

    public Result(int algorithmVersion, double estimatedAverageSolutionSeconds,
        boolean estimateComplete) {
      this.algorithmVersion = algorithmVersion;
      this.estimatedAverageSolutionSeconds = estimatedAverageSolutionSeconds;
      this.estimateComplete = estimateComplete;
    }
  }

  /**
   * Called back periodically from {@link #evaluate} with updates to the
   * estimate.
   */
  public interface Callback {
    void updateEstimate(double minSeconds);
  }

  /**
   * Evaluates the difficulty of a Sudoku puzzle, which must be solvable and
   * have no more than a few solutions (or the method will throw). The provided
   * callback may be given updates during the evaluation; these will be
   * monotonically increasing. If the thread is interrupted, the evaluation may
   * stop early, and in that case the result object will be marked as incomplete.
   */
  public static Result evaluate(Grid puzzle, Callback callback) {
    double seconds = 0;
    GridMarks gridMarks = new GridMarks(puzzle);
    while (!gridMarks.grid.isSolved()) {
      Collector collector = new Collector(gridMarks);
      boolean complete = Analyzer.analyze(gridMarks, collector, true);
      if (!complete) return result(seconds, false);
      seconds += collector.getElapsedSeconds();
    }
    return result(seconds, true);
  }

  /**
   * A classification of assignment insights based on empirical study of degrees
   * of difficulty of different insights. They are ordered from easiest to
   * hardest.
   */
  public enum MoveKind {
    EASY_DIRECT(1.1),
    DIRECT(1.2),
    SIMPLY_IMPLIED_EASY(1.4),
    SIMPLY_IMPLIED(1.5),
    IMPLIED_EASY(2.7),
    IMPLIED(3.2),  // catch-all, including errors
    ;

    /**
     * How many seconds it takes on average to scan one "point" for this kind of
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
    public final double secondsPerScanPoint;

    public static final MoveKind MIN = EASY_DIRECT;
    public static final MoveKind MAX = IMPLIED;

    private MoveKind(double secondsPerScanPoint) {
      this.secondsPerScanPoint = secondsPerScanPoint;
    }
  }

  private static class Collector implements Analyzer.Callback {
    private final GridMarks gridMarks;
    private final LocSet locTargets = new LocSet();
    private final Set<UnitNumeral> unitNumTargets = Sets.newHashSet();
    private final Map<Location, MoveKind> kinds = Maps.newHashMap();
    private boolean errors;

    Collector(GridMarks gridMarks) {
      this.gridMarks = gridMarks;
    }

    @Override public void take(Insight insight) throws StopException {
      insight.addScanTargets(locTargets, unitNumTargets);
      Assignment a = insight.getImpliedAssignment();
      if (a != null) {
        kinds.put(a.location, kindForInsight(insight, kinds.get(a.location)));
      } else if (insight.isError()) {
        errors = true;
      }
    }

    double getElapsedSeconds() {
      MoveKind kind = errors || kinds.isEmpty()
          ? MoveKind.MAX : Ordering.<MoveKind>natural().max(kinds.values());
      int openLocs = Location.COUNT - gridMarks.grid.size();
      double totalScanPoints = 4.0 * openLocs;
      int scanTargets = locTargets.size() + unitNumTargets.size();
      double scanPoints = scanTargets == 0 ? 2 * totalScanPoints : totalScanPoints / scanTargets;
      return kind.secondsPerScanPoint * scanPoints;
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
      // TODO Auto-generated method stub
      return false;
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

  private static Result result(double seconds, boolean complete) {
    return new Result(CURRENT_VERSION, seconds, complete);
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
