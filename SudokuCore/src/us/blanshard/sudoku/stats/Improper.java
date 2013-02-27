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

import static java.lang.System.out;

import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.gen.GenerationStrategy;
import us.blanshard.sudoku.gen.Symmetry;

import java.util.Random;

/**
 * Generates improper puzzles with particular parameters, spits out a tsv file
 * with their characteristics.
 *
 * @author Luke Blanshard
 */
public class Improper {
  public static void main(String[] args) {
    if (args.length < 3 || args.length > 4) exitWithUsage();
    int maxSolutions, maxHoles, count;
    long seed;
    try {
      maxSolutions = Integer.decode(args[0]);
      maxHoles = Integer.decode(args[1]);
      count = Integer.decode(args[2]);
      seed = args.length > 3 ? Slow.decode(args[3]) : System.currentTimeMillis();
    } catch (NumberFormatException e) {
      exitWithUsage();
      return;  // Convince the compiler.
    }

    out.printf("Generating %d improper puzzles (%d, %d) from seed %#x%n",
        count, maxSolutions, maxHoles, seed);

    generate(maxSolutions, maxHoles, count, seed);
  }

  private static void exitWithUsage() {
    System.err.println("Usage: Improper <maxSolutions> <maxHoles> <count> [<seed>]");
    System.exit(1);
  }

  private static void generate(int maxSolutions, int maxHoles, int count, long seed) {
    out.println("Symmetry\tNum Solutions\tNum Holes");
    Random random = new Random(seed);
    while (count-- > 0) {
      Symmetry sym = Symmetry.choose(random);
      Solver.Result result = GenerationStrategy.SUBTRACTIVE_RANDOM.generate(
          random, sym, maxSolutions, maxHoles);
      out.printf("%s\t%d\t%d%n", sym.getName(), result.numSolutions,
                 Location.COUNT - result.intersection.size());
    }
  }
}
