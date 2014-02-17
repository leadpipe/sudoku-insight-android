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

import us.blanshard.sudoku.insight.Evaluator;
import us.blanshard.sudoku.insight.Rating;
import us.blanshard.sudoku.insight.Rating.Difficulty;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

/**
 * Evaluates the Evaluator class by running it over a series of puzzles that
 * have historical information and emitting a tsv for further analysis.
 */
public class EvaluatorEvaluator {

  public static void main(String[] args) throws Exception {
    PrintWriter out = new PrintWriter(args[0]);
    out.println("Puzzle\tElapsed Minutes\tEstimated Minutes\tDifficulty\tImproper");
    int npuzzles = 0;
    int nwon = 0;
    int nsingle = 0;
    int nrecursive = 0;
    double totalAbsPercentError = 0;
    Stats undershotSingle = new Stats();
    Stats undershotMultiple = new Stats();
    Stats overshotSingle = new Stats();
    Stats overshotMultiple = new Stats();

    Iterable<AttemptInfo> attempts = Iterables.concat(
        Attempts.phone2013(), Attempts.tablet2014());
    for (AttemptInfo attempt : attempts) {
      ++npuzzles;
      System.err.print('.');
      if (npuzzles % 100 == 0) System.err.println();
      if (!attempt.won) continue;

      ++nwon;
      Stopwatch stopwatch = new Stopwatch().start();
      Rating result = Evaluator.evaluate(attempt.clues, null);
      long micros = stopwatch.elapsed(TimeUnit.MICROSECONDS);
      boolean singlePass = result.difficulty == Difficulty.NO_DISPROOFS;
      if (singlePass) ++nsingle;
      else if (result.difficulty == Difficulty.RECURSIVE_DISPROOFS) ++nrecursive;
      Info info = new Info(attempt.elapsedMinutes, result, micros);
      totalAbsPercentError += info.getAbsPercentError();
      Stats stats = info.isOvershot()
        ? singlePass ? overshotSingle : overshotMultiple
        : singlePass ? undershotSingle : undershotMultiple;
      info.addTo(stats);
      out.printf("%s\t%.2f\t%.2f\t%d\t%d%n",
          attempt.clues.toFlatString(), attempt.elapsedMinutes, result.score,
          result.difficulty.ordinal(), result.improper ? 1 : 0);
    }

    System.err.println();
    System.err.printf("# puzzles: %d; # won: %d; # recursive: %d%n", npuzzles, nwon, nrecursive);
    System.err.printf("Proportion solved in one pass: %.2f%%%n", 100.0 * nsingle / nwon);
    System.err.printf("MAPE: %.2f%%%n", totalAbsPercentError / nwon);

    PrintWriter err = new PrintWriter(System.err);
    printStats(err, "Single-pass", undershotSingle, overshotSingle);
    printStats(err, "Multi-pass", undershotMultiple, overshotMultiple);

    err.close();
    out.close();
  }

  private static void printStats(PrintWriter out, String category, Stats undershot, Stats overshot) {
    double count = undershot.apeStats.getN() + overshot.apeStats.getN();
    printStats(out, category, true, count, undershot);
    printStats(out, category, false, count, overshot);
  }

  private static void printStats(PrintWriter out, String category, boolean under, double count, Stats stats) {
    out.printf("%n%s %sshot stats (estimate %s than actual; %.2f%%):%n%s",
        category, under ? "under" : "over", under ? "less" : "greater",
        stats.apeStats.getN() * 100.0 / count, stats);
  }

  static class Stats {
    final DescriptiveStatistics apeStats = new DescriptiveStatistics();
    final DescriptiveStatistics timeStats = new DescriptiveStatistics();

    @Override public String toString() {
      return "Ave Percent Errors = " + apeStats + "Evaluator Millis = " + timeStats;
    }
  }

  static class Info {
    final double elapsedMinutes;
    final Rating result;
    final long micros;
    final double ape;

    Info(double elapsedMinutes, Rating result, long micros) {
      this.elapsedMinutes = elapsedMinutes;
      this.result = result;
      this.micros = micros;
      this.ape = calcAbsPercentError(result.score, elapsedMinutes);
    }

    boolean isOvershot() {
      return result.score > elapsedMinutes;
    }

    double getAbsPercentError() {
      return ape;
    }

    private static double calcAbsPercentError(double a, double b) {
      return 100 * (a < b ? (b - a) / a : (a - b) / b);
    }

    void addTo(Stats stats) {
      stats.apeStats.addValue(getAbsPercentError());
      stats.timeStats.addValue(micros / 1000.0);
    }
  }
}
