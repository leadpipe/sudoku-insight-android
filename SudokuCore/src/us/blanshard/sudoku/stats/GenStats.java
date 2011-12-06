/*
Copyright 2011 Google Inc.

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

import java.util.Random;

import us.blanshard.sudoku.core.Generator;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Solver;

import com.google.common.base.Stopwatch;

/**
 * Generates random Sudoku grids, solves them, and spits out statistics about
 * the solvers.
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
      seed = args.length > 1 ? Long.decode(args[1]) : System.currentTimeMillis();
    } catch (NumberFormatException e) {
      exitWithUsage();
      return;  // Convince the compiler.
    }

    System.err.printf("Generating %d puzzles from seed %#x%n", count, seed);

    // Start with a few rounds with a fixed seed and no printing, to get all the
    // machinery warmed up.  Empirically it takes around 35 rounds to get the
    // fully optimized code paths.
    generate(40, 0, false);

    // Then do the real work.
    generate(count, seed, true);
  }

  private static void exitWithUsage() {
    System.err.println("Usage: GenStats <count> [<seed>]");
    System.exit(1);
  }

  private static void generate(int count, long seed, boolean print) {
    if (print) {
      System.err.print("Start\tGenerator\tGen Micros\tSeed");
      for (Solver.Strategy strategy : Solver.Strategy.values()) {
        System.err.printf("\t%s:Num Solutions\tNum Steps\tMicros", strategy);
      }
      System.err.println();
    }
    Random random = new Random(seed);
    while (count-- > 0) {
      Generator generator = Generator.choose(random);

      Stopwatch stopwatch = new Stopwatch().start();
      Grid start = generator.generate(random);
      stopwatch.stop();

      long genMicros = stopwatch.elapsedTime(MICROSECONDS);
      long solverSeed = random.nextLong();

      if (print)
        System.out.printf("%s\t%s\t%d\t%#x",
                          start.toFlatString(), generator, genMicros, solverSeed);

      for (Solver.Strategy strategy : Solver.Strategy.values()) {
        stopwatch.reset().start();
        Solver.Result result = Solver.solve(start, new Random(solverSeed), strategy);
        stopwatch.stop();

        long micros = stopwatch.elapsedTime(MICROSECONDS);
        if (print)
          System.out.printf("\t%d\t%d\t%d", result.numSolutions, result.numSteps, micros);
      }
      if (print)
        System.out.println();
    }
  }
}
