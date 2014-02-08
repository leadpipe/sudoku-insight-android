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

import us.blanshard.sudoku.insight.Evaluator.MoveKind;
import us.blanshard.sudoku.insight.Insight.Realm;

import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;

import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Analyzes the output from {@link InsightMeasurer} to look for Poisson
 * processes in Sudoku solving.
 */
public class ScanPoints {
  public static void main(String[] args) throws IOException {
    BufferedReader in = new BufferedReader(new FileReader("measurer.txt"));
    Splitter splitter = Splitter.on('\t');
    for (String line; (line = in.readLine()) != null; ) {
      Iterator<String> iter = splitter.split(line).iterator();
      boolean isTrailhead = Boolean.parseBoolean(iter.next());
      long ms = Long.parseLong(iter.next());
      int openCount = Integer.parseInt(iter.next());
      int minOpen = Integer.parseInt(iter.next());
      int numTrails = Integer.parseInt(iter.next());
      long timestamp = Long.parseLong(iter.next());
      long effectiveTimestamp = Long.parseLong(iter.next());
      int moveNumber = Integer.parseInt(iter.next());
      int effectiveMoveNumber = Integer.parseInt(iter.next());
      Set<MoveKind> minima = parseKinds(iter.next());

      Reporter reporter = getReporter(
          minOpen, numTrails, timestamp, effectiveTimestamp, moveNumber, effectiveMoveNumber);
      double seconds = ms / 1000.0;
      if (isTrailhead) {
        reporter.takeTrailhead(seconds, openCount, minima);
      } else {
        int numMoves = Integer.parseInt(iter.next());
        Set<MoveKind> maxOfMins = parseKinds(iter.next());
        reporter.takeBatch(seconds, openCount, minima, numMoves, maxOfMins, new Universe(iter));
      }
      if (iter.hasNext()) {
        in.close();
        throw new IOException("Mismatch in line consumption");
      }
    }
    in.close();
    reportSummaries(System.out);
  }

  private static Set<MoveKind> parseKinds(String string) {
    Set<MoveKind> answer = EnumSet.noneOf(MoveKind.class);
    if (!string.isEmpty())
      for (String s : Splitter.on(';').split(string))
        answer.add(MoveKind.valueOf(s));
    return answer;
  }

  static class Universe {
    final int numerator;
    final int denominator;
    final int realmVector;
    Universe(Iterator<String> iter) {
      numerator = Integer.parseInt(iter.next());
      denominator = Integer.parseInt(iter.next());
      realmVector = Integer.parseInt(iter.next());
    }
  }

  static class Reporter {
    private final LoadingCache<Set<MoveKind>, BatchProcess> processes;
    private final ProcessCounter trailheadCounter = new ProcessCounter();

    public Reporter() {
      processes = CacheBuilder.newBuilder().build(new CacheLoader<Set<MoveKind>, BatchProcess>() {
        @Override public BatchProcess load(Set<MoveKind> minima) {
          return new BatchProcess(minima);
        }
      });
    }

    public void reportSummaries(PrintStream out) {
      for (Set<MoveKind> min : kindSets)
        if (processes.asMap().containsKey(min))
          processes.asMap().get(min).report(out);
      out.println();
      out.println();
      out.printf("Trailheads (%d):%n", trailheadCounter.count);
      trailheadCounter.report(out);
    }

    public void takeTrailhead(double seconds, int openCount, Set<MoveKind> minima) {
      trailheadCounter.count(seconds, 1);
      if (!minima.isEmpty())
        processes.getUnchecked(minima).takeTrailhead();
    }

    public void takeBatch(double seconds, int openCount, Set<MoveKind> minima,
        int numMoves, Set<MoveKind> maxOfMins, Universe universe) {
      processes.getUnchecked(minima).take(seconds, openCount, numMoves, maxOfMins, universe);
    }
  }

  static class BatchProcess {
    private final Set<MoveKind> minima;
    private final LoadingCache<Integer, ProcessCounter> realmCounters;
    private final LoadingCache<Set<MoveKind>, ProcessCounter> maxCounters;
    private final ProcessCounter overallCounter = new ProcessCounter();
    private int trailheadsIncluded;
    private int batchesIncluded;
    private int movesIncluded;
    private final boolean reportByRealm = false;

    public BatchProcess(Set<MoveKind> minima) {
      this.minima = minima;
      realmCounters = CacheBuilder.newBuilder().build(new CacheLoader<Integer, ProcessCounter>() {
        @Override public ProcessCounter load(Integer realmVector) {
          return new ProcessCounter();
        }
      });
      maxCounters = CacheBuilder.newBuilder().build(new CacheLoader<Set<MoveKind>, ProcessCounter>() {
        @Override public ProcessCounter load(Set<MoveKind> maxOfMins) {
          return new ProcessCounter();
        }
      });
    }

    public void takeTrailhead() {
      ++trailheadsIncluded;
    }

    public void take(double seconds, int openCount, int numMoves,
        Set<MoveKind> maxOfMins, Universe universe) {
      realmCounters.getUnchecked(universe.realmVector)
          .count(seconds, pointsScanned(openCount, universe));
      maxCounters.getUnchecked(maxOfMins)
          .count(seconds, pointsScanned(openCount, 4, universe));
      overallCounter.count(seconds, pointsScanned(openCount, 4, universe));
      ++batchesIncluded;
      movesIncluded += numMoves;
    }

    private double pointsScanned(int openCount, int factor, Universe u) {
      // Factor is the number of "scan points" per open location.  So the first
      // product yields the total number of scan points in the grid.  We divide
      // by the total number of "scan targets" in the batch, to get points per
      // target.  Then we multiply by the number of targets in the insights
      // included in the batch, to get the number of points scanned in the
      // batch.
      return 1.0 * openCount * factor / u.denominator * u.numerator;
    }

    private double pointsScanned(int openCount, Universe u) {
      return pointsScanned(openCount, factor(u.realmVector), u);
    }

    private int factor(int realmVector) {
      // Explanation: bits 0 and 1 have weights 1 and 2 respectively (they
      // correspond to blocks and lines).  Bit 2 is back to a weight of 1 (it
      // corresponds to locations).  The vector is > 3 when bit 2 is on, so we
      // subtract away the excess over the weight.
      return realmVector > 3 ? realmVector - 3 : realmVector;
    }

    public void report(PrintStream out) {
      out.printf("%s: %,d batches (%,d moves); %,d trailheads\n",
          minima, batchesIncluded, movesIncluded, trailheadsIncluded);

      out.println(" - Overall:");
      overallCounter.report(out);

      out.println("\n - By max of minima:");
      int count = maxCounters.asMap().size();
      for (Set<MoveKind> max : kindSets)
        if (maxCounters.asMap().containsKey(max)) {
          --count;
          ProcessCounter c = maxCounters.asMap().get(max);
          out.printf("   %s (%,d):\n", max, c.count);
          c.report(out);
        }
      if (count > 0) throw new IllegalStateException();

      if (reportByRealm) {
        out.println("\n - By realm vector:");
        for (int v = 0; v < vectorToSet.size(); ++v) {
          if (realmCounters.asMap().containsKey(v)) {
            ProcessCounter c = realmCounters.getUnchecked(v);
            out.printf("   %s (%,d):\n", vectorToSet.get(v), c.count);
            c.report(out);
          }
        }
      }

      out.println();
    }

    private static final List<Set<Realm>> vectorToSet;
    static {
      Realm[] all = Realm.values();
      ImmutableList.Builder<Set<Realm>> builder = ImmutableList.builder();
      for (int v = 0; v < 8; ++v) {
        Set<Realm> set = EnumSet.noneOf(Realm.class);
        for (Realm r : all)
          if (r.isIn(v))
            set.add(r);
        builder.add(set);
      }
      vectorToSet = builder.build();
    }
  }

  private static final List<Set<MoveKind>> kindSets = ImmutableList.<Set<MoveKind>>builder()
      .add(EnumSet.of(MoveKind.DIRECT_EASY))
      .add(EnumSet.of(MoveKind.SIMPLY_IMPLIED_EASY))
      .add(EnumSet.of(MoveKind.COMPLEXLY_IMPLIED_EASY))
      .add(EnumSet.of(MoveKind.DIRECT_HARD, MoveKind.SIMPLY_IMPLIED_EASY))
      .add(EnumSet.of(MoveKind.DIRECT_HARD, MoveKind.COMPLEXLY_IMPLIED_EASY))
      .add(EnumSet.of(MoveKind.SIMPLY_IMPLIED_HARD, MoveKind.COMPLEXLY_IMPLIED_EASY))
      .add(EnumSet.of(MoveKind.DIRECT_HARD))
      .add(EnumSet.of(MoveKind.SIMPLY_IMPLIED_HARD))
      .add(EnumSet.of(MoveKind.COMPLEXLY_IMPLIED_HARD))
      .build();

  static class ProcessCounter {
    private final boolean reportHistogram;
    private final Multiset<Integer> pointsPerSecond = HashMultiset.create();
    private double pendingSeconds;
    private double pendingPoints;
    int count;

    public ProcessCounter() {
      this(false);
    }

    public ProcessCounter(boolean reportHistogram) {
      this.reportHistogram = reportHistogram;
    }

    public void count(double seconds, double pointsScanned) {
      ++count;
      double secondsPerPoint = seconds / pointsScanned;
      int pointsScannedFloor = (int) pointsScanned;
      for (int i = 0; i < pointsScannedFloor; ++i) {
        pendingSeconds += secondsPerPoint;
        addPendingPointsPerSecond();
        pendingPoints += 1.0;
      }
      pendingSeconds += (seconds - secondsPerPoint * pointsScannedFloor);
      addPendingPointsPerSecond();
      pendingPoints += (pointsScanned - pointsScannedFloor);
    }

    private void addPendingPointsPerSecond() {
      while (pendingSeconds >= 1.0) {
        int pendingPointsFloor = (int) pendingPoints;
        pointsPerSecond.add(pendingPointsFloor);
        pendingPoints -= pendingPointsFloor;
        pendingSeconds -= 1.0;
      }
    }

    public void report(PrintStream out) {
      SummaryStatistics stats = new SummaryStatistics();
      for (int points : pointsPerSecond)
        stats.addValue(points);
      out.printf("Scan-points/second: %.2f; var: %.2f (%.2f%%)\n", stats.getMean(),
          stats.getVariance(), 100 * stats.getVariance() / stats.getMean());
      out.printf("Seconds/scan-point: %.3f\n", 1.0 / stats.getMean());
      if (reportHistogram) {
        PoissonDistribution dist = new PoissonDistribution(stats.getMean());
        out.println("Histogram, actual vs predicted:");
        int max =
            pointsPerSecond.isEmpty() ? 0 : Ordering.natural().max(pointsPerSecond.elementSet());
        for (int bucket = 0; bucket <= max || dist.probability(bucket) > 1e-3; ++bucket)
          out.printf("  %2d:%6d  |%9.2f\n", bucket, pointsPerSecond.count(bucket),
              dist.probability(bucket) * stats.getN());
      }
    }
  }

  // ============================

  private static final int COUNT = 6;
  private static final Reporter[] reporters = new Reporter[COUNT];
  private static final Reporter reporter = new Reporter();
  private static boolean single = false;

  private static Reporter getReporter(
      int minOpen, int numTrails, long timestamp,
      long effectiveTimestamp, int moveNumber, int effectiveMoveNumber) {
    if (single) return reporter;
    int index = Math.min(COUNT - 1, minOpen / 10);
    Reporter answer = reporters[index];
    if (answer == null)
      reporters[index] = answer = new Reporter();
    return answer;
  }

  private static void reportSummaries(PrintStream out) {
    if (single) {
      reportSummaries(out, reporter, "Single");
    } else {
      for (int i = COUNT - 1; i >= 0; --i) {
        Reporter r = reporters[i];
        if (r != null)
          reportSummaries(out, r, headline(i));
      }
    }
  }

  private static String headline(int i) {
    int min = i * 10;
    int max = min + 9;
    if (i == COUNT - 1)
      return String.format("Minimum open: %d or more", min);
    return String.format("Minimum open: %d - %d", min, max);
  }

  private static void reportSummaries(PrintStream out, Reporter reporter, String desc) {
    out.printf("%n======================%n%s%n======================%n%n", desc);
    reporter.reportSummaries(out);
  }
}
