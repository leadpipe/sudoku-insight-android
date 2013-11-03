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

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.gen.Generator;
import us.blanshard.sudoku.insight.Evaluator;
import us.blanshard.sudoku.insight.Rating;
import us.blanshard.sudoku.insight.Rating.Difficulty;

import com.google.gson.JsonObject;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.Assert;

import java.io.PrintStream;

/**
 * Generates a bunch of puzzles that require guessing to evaluate,
 * looks at how many runs it requires to converge on a rating.
 * Emits a csv file with the data.
 */
public class RatingStats2 {
  public static void main(String[] args) {
    PrintStream out = System.out;
    out.println("Puzzle,#Runs,Fraction");
    int count = 0;
    for (int n = 1; count < 100; ++n) {
      JsonObject props = Generator.generateBasicPuzzle(1, 2014, 1, n);
      Grid puzzle = Grid.fromString(props.get(Generator.PUZZLE_KEY).getAsString());
      Evaluator evaluator = new Evaluator(puzzle);
      Rating rating = evaluator.evaluate(null, 1);
      if (rating.difficulty != Difficulty.NO_DISPROOFS) {
        ++count;
        run(props.get(Generator.NAME_KEY).getAsString(), evaluator, rating, out);
      }
    }
  }

  /** How many evaluation runs we do on each puzzle. */
  private static final int NUM_RUNS = 100;

  /**
   * Runs the evaluator many times, then emits data about its ratings.
   */
  private static void run(String name, Evaluator evaluator, Rating first, PrintStream out) {
    double[] cumulativeAverages = new double[NUM_RUNS];
    cumulativeAverages[0] = first.estimatedAverageSolutionSeconds;
    SummaryStatistics stat = new SummaryStatistics();
    stat.addValue(first.estimatedAverageSolutionSeconds);
    for (int n = 1; n < NUM_RUNS; ++n) {
      Rating rating = evaluator.evaluate(null, 1);
      stat.addValue(rating.estimatedAverageSolutionSeconds);
      cumulativeAverages[n] = stat.getMean();
    }
    Assert.assertEquals(NUM_RUNS, stat.getN());
    double last = stat.getMean();
    for (int n = 0; n < NUM_RUNS; ++n)
      out.printf("%s,%d,%g%n", name, n + 1, cumulativeAverages[n] / last);
  }
}
