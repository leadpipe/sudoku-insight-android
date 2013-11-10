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

import static us.blanshard.sudoku.game.GameJson.JOINER;
import static us.blanshard.sudoku.game.GameJson.SPLITTER;

import java.util.Iterator;

/** The object returned by the {@link Evaluator}. */
public class Rating {
  /**
   * Intrinsic degrees of difficulty for Sudokus. "No disproofs" means that the
   * basic solution rules are sufficient to solve the puzzle. "Simple disproofs"
   * means that disproving possible assignments using the basic solution rules
   * is sufficient. "Recursive disproofs" means that there aren't enough simple
   * disproofs available to solve the puzzle, so you must disprove subsequent
   * assignments while trying to disprove initial ones.
   */
  public enum Difficulty {
    NO_DISPROOFS,
    SIMPLE_DISPROOFS,
    RECURSIVE_DISPROOFS;  // Note ordinals are serialized
  }

  /** The version of the estimation algorithm that was used for this result. */
  public final int algorithmVersion;
  /** How long the puzzle is estimated to take. */
  public final double estimatedAverageSolutionSeconds;
  /** Whether the estimate was complete; if false, the estimate is probably
      less than it would have been if allowed to finish. */
  public final boolean estimateComplete;
  /** The intrinsic difficulty of the puzzle. */
  public final Difficulty difficulty;
  /** Whether the puzzle has more than one solution. */
  public final boolean improper;

  public Rating(int algorithmVersion, double estimatedAverageSolutionSeconds,
      boolean estimateComplete, Difficulty difficulty, boolean improper) {
    this.algorithmVersion = algorithmVersion;
    this.estimatedAverageSolutionSeconds = estimatedAverageSolutionSeconds;
    this.estimateComplete = estimateComplete;
    this.difficulty = difficulty;
    this.improper = improper;
  }

  /** Renders this result in a string form that can be reversed by {@link #deserialize}. */
  public String serialize() {
    return JOINER.join(algorithmVersion, estimatedAverageSolutionSeconds,
        estimateComplete, difficulty.ordinal(), improper);
  }

  @Override public String toString() {
    return "Rating:" + serialize();
  }

  /** Restores a result previously {@link #serialize}d. */
  public static Rating deserialize(String s) {
    Iterator<String> it = SPLITTER.split(s).iterator();
    int algorithmVersion = Integer.parseInt(it.next());
    double estimatedAverageSolutionSeconds = Double.parseDouble(it.next());
    boolean estimateComplete = Boolean.parseBoolean(it.next());
    Difficulty difficulty = Difficulty.values()[Integer.parseInt(it.next())];
    boolean improper = Boolean.parseBoolean(it.next());
    return new Rating(algorithmVersion, estimatedAverageSolutionSeconds, estimateComplete,
        difficulty, improper);
  }
}