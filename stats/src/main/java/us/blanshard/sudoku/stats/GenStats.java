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

import static java.util.concurrent.TimeUnit.MICROSECONDS;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.gen.GenerationStrategy;
import us.blanshard.sudoku.gen.Symmetry;

import com.google.common.base.Stopwatch;

import java.util.Random;

/**
 * Generates random Sudoku grids, and spits out statistics about the various
 * generator strategies.
 *
 * @author Luke Blanshard
 */
public class GenStats {
  public static void main(String[] args) {
    if (args.length < 1 || args.length > 2) exitWithUsage();
    int count;
    long seed;
    try {
      count = Integer.decode(args[0]);
      seed = args.length > 1 ? Slow.decode(args[1]) : System.currentTimeMillis();
    } catch (NumberFormatException e) {
      exitWithUsage();
      return;  // Convince the compiler.
    }

    System.out.printf("Generating %d puzzles from seed %#x%n", count, seed);

    // Start with a few rounds with a fixed seed and no printing, to get all the
    // machinery warmed up.
    generate(5, 0, false);

    // Then do the real work.
    generate(count, seed, true);
  }

  private static void exitWithUsage() {
    System.err.println("Usage: GenStats <count> [<seed>]");
    System.exit(1);
  }

  private static void generate(int count, long seed, boolean print) {
    if (print) {
      System.out.print("Symmetry\tSeed");
      for (GenerationStrategy generator : GenerationStrategy.values())
        System.out.printf("\t%s:Num Clues\tMicros", generator);
      System.out.println();
    }
    Random random = new Random(seed);
    while (count-- > 0) {
      Symmetry symmetry = Symmetry.choose(random);
      long genSeed = random.nextLong();
      if (print) System.out.printf("%s\t%#x", symmetry, genSeed);

      for (GenerationStrategy generator : GenerationStrategy.values()) {
        Stopwatch stopwatch = new Stopwatch().start();
        Grid grid = generator.generate(new Random(genSeed), symmetry);
        stopwatch.stop();

        long micros = stopwatch.elapsed(MICROSECONDS);
        if (print) System.out.printf("\t%d\t%d", grid.size(), micros);
      }

      if (print) System.out.println();
    }
  }
}
