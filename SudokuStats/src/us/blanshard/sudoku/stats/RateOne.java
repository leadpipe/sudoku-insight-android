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

import java.io.IOException;

/**
 * Evaluates one sudoku, and prints the result.
 */
public class RateOne {
  public static void main(String[] args) throws IOException {
    System.in.read();
    JsonObject props = Generator.regeneratePuzzle("1:1:2013-11:191");
    Grid puzzle = Grid.fromString(props.get(Generator.PUZZLE_KEY).getAsString());
    Rating rating = Evaluator.evaluate(puzzle, null);
    System.out.println(rating);
  }
}
