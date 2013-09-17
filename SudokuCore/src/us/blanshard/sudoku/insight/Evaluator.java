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

import us.blanshard.sudoku.core.Grid;

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
    return null;
  }

  /**
   * A classification of assignment insights based on empirical study of degrees
   * of difficulty of different insights. They are ordered from easiest to
   * hardest.
   */
  public enum MoveKind {
    FORCED_LOCATION_BLOCK(1.8),
    FORCED_LOCATION_LINE(2),
    EASY_DIRECT(2.1),
    DIRECT(2.2),
    SIMPLY_IMPLIED(2.5),
    IMPLIED_EASY(3.8),
    IMPLIED(4.6),
    ;

    /**
     * How many seconds it takes on average to scan one "point" for this kind of
     * move.
     *
     * <p> The number of "points" at any given grid position is defined as the
     * number of open locations divided by the total number of assignment
     * insights.
     *
     * <p> The move kind chosen for a given grid is the maximum move kind for
     * all the locations that have assignment insights. And the move kind for a
     * given location is the minimum move kind for all the insights that assign
     * a numeral to that location.
     */
    public final double secondsPerScanPoint;

    private MoveKind(double secondsPerScanPoint) {
      this.secondsPerScanPoint = secondsPerScanPoint;
    }
  }


}
