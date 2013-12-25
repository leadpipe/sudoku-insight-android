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

import com.google.gson.JsonObject;

import java.io.PrintStream;

/**
 * Generates a bunch of puzzles and evaluates them, emitting a data file
 * for analysis.
 */
public class RatingStats1 {
  public static void main(String[] args) {
    PrintStream out = System.out;
    out.println("Stream,Year,Month,Counter,Symmetry,#Solutions,Minutes,Difficulty");
    for (int stream = 1; stream <= 5; ++stream)
      for (int month = 1; month <= 12; ++month)
        for (int counter = 1; counter <= 100; ++counter) {
          int year = 2014;
          JsonObject props = Generator.generateBasicPuzzle(stream, year, month, counter);
          String symmetry;
          if (props.has(Generator.SYMMETRY_KEY))
            symmetry = props.get(Generator.SYMMETRY_KEY).getAsString();
          else
            symmetry = props.get(Generator.BROKEN_SYMMETRY_KEY).getAsString() + "-broken";
          int numSolutions = props.get(Generator.NUM_SOLUTIONS_KEY).getAsInt();
          Grid puzzle = Grid.fromString(props.get(Generator.PUZZLE_KEY).getAsString());
          Rating rating = Evaluator.evaluate(puzzle, null);
          int difficulty = rating.difficulty.ordinal();
          out.printf("%d,%d,%d,%d,%s,%d,%f,%d%n", stream, year, month, counter,
              symmetry, numSolutions, rating.score, difficulty);
        }
  }
}
