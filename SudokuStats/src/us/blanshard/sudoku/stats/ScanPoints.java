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
import us.blanshard.sudoku.stats.Pattern.Coll;

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
      List<Coll> matched = Pattern.collsFromString(iter.next());
      List<Coll> missed = Pattern.collsFromString(iter.next());
      String firstKindString = iter.next();
      MoveKind firstKind = firstKindString.isEmpty() ? null : MoveKind.valueOf(firstKindString);
      int moveNumber = Integer.parseInt(iter.next());
      int effectiveMoveNumber = Integer.parseInt(iter.next());
      long timestamp = Long.parseLong(iter.next());
      long effectiveTimestamp = Long.parseLong(iter.next());
      int minOpen = Integer.parseInt(iter.next());
      long ms = Long.parseLong(iter.next());
      int openCount = Integer.parseInt(iter.next());
      boolean isBlockNumeralMove = Boolean.parseBoolean(iter.next());
      int numBlockNumeralMoves = Integer.parseInt(iter.next());
      int numOpenBlockNumerals = Integer.parseInt(iter.next());
      boolean isTrailhead = Boolean.parseBoolean(iter.next());
      int numTrails = Integer.parseInt(iter.next());
      getReporter(numTrails, moveNumber, effectiveMoveNumber, timestamp, effectiveTimestamp, minOpen)
          .take(matched, missed, firstKind, isTrailhead, ms, openCount,
                isBlockNumeralMove, numBlockNumeralMoves, numOpenBlockNumerals);
    }
    in.close();
    reportSummaries(System.out);
  }

  static class Reporter {
    private final LoadingCache<MoveKind, KindProcess> processes;
    private final ProcessCounter blockNumeralCounter = new ProcessCounter();
    private final ProcessCounter trailheadCounter = new ProcessCounter();

    public Reporter() {
      processes = CacheBuilder.newBuilder().build(new CacheLoader<MoveKind, KindProcess>() {
        @Override public KindProcess load(MoveKind kind) {
          return new KindProcess(kind);
        }
      });
    }

    public void reportSummaries(PrintStream out) {
      for (MoveKind kind : MoveKind.values())
        if (processes.asMap().containsKey(kind))
          processes.getUnchecked(kind).report(out);
      out.println();
      out.printf("Consecutive block-numeral moves (%d):%n", blockNumeralCounter.count);
      blockNumeralCounter.report(out);
      out.println();
      out.printf("Trailheads (%d):%n", trailheadCounter.count);
      trailheadCounter.report(out);
    }

    public void take(List<Coll> matched, List<Coll> missed, MoveKind firstKind, boolean isTrailhead,
                     long ms, int openCount, boolean isBlockNumeralMove, int numBlockNumeralMoves,
                     int numOpenBlockNumerals) {
      double seconds = ms / 1000.0;
      if (isBlockNumeralMove)
        blockNumeralCounter.count(seconds, numOpenBlockNumerals / (double) numBlockNumeralMoves);
      if (isTrailhead) {
        trailheadCounter.count(seconds, 1);
      } else {
        for (Coll m : matched)
          processes.getUnchecked(m.kind).take(m, missed, m.kind == firstKind, seconds, openCount);
      }
    }
  }

  static class KindProcess {
    private final MoveKind kind;
    private final LoadingCache<Integer, ProcessCounter> firstBucketCounters;
    private final LoadingCache<Integer, ProcessCounter> otherBucketCounters;
    private final LoadingCache<Integer, ProcessCounter> eitherCounters;
    private final ProcessCounter firstBucketCounter = new ProcessCounter();
    private final ProcessCounter otherBucketCounter = new ProcessCounter();
    private final ProcessCounter eitherCounter = new ProcessCounter();
    private final ProcessCounter flatFirstBucketCounter = new ProcessCounter();
    private final ProcessCounter flatOtherBucketCounter = new ProcessCounter();
    private final ProcessCounter flatEitherCounter = new ProcessCounter();
    private int movesIncluded;
    private int movesFirstBucket;

    public KindProcess(MoveKind kind) {
      this.kind = kind;
      CacheLoader<Integer, ProcessCounter> loader = new CacheLoader<Integer, ProcessCounter>() {
        @Override public ProcessCounter load(Integer realmVector) {
          return new ProcessCounter();
        }
      };
      this.firstBucketCounters = CacheBuilder.newBuilder().build(loader);
      this.otherBucketCounters = CacheBuilder.newBuilder().build(loader);
      this.eitherCounters = CacheBuilder.newBuilder().build(loader);
    }

    public void take(Coll bucket, List<Coll> missed, boolean firstBucket, double seconds, int openCount) {
      double realmPoints = pointsScanned(openCount, bucket);
      double flatPoints = pointsScanned(openCount, 4, bucket);
      (firstBucket ? firstBucketCounters : otherBucketCounters).getUnchecked(bucket.realmVector)
          .count(seconds, realmPoints);
      (firstBucket ? firstBucketCounter : otherBucketCounter).count(seconds, realmPoints);
      (firstBucket ? flatFirstBucketCounter : flatOtherBucketCounter).count(seconds, flatPoints);
      eitherCounters.getUnchecked(bucket.realmVector).count(seconds, realmPoints);
      eitherCounter.count(seconds, realmPoints);
      flatEitherCounter.count(seconds, flatPoints);
      ++movesIncluded;
      if (firstBucket) ++movesFirstBucket;
    }

    private double pointsScanned(int openCount, int factor, Coll bucket) {
      // Factor is the number of "scan points" per open location.  So the first
      // product yields the total number of scan points in the grid.  We divide
      // by the total number of "scan targets" in the bucket, to get points per
      // target.  Then we multiply by the number of targets in the first insight
      // that matched the move made, to get the number of points scanned in
      // finding the insight.
      return openCount * factor / bucket.numScanTargets * bucket.patterns.get(0).getScanTargetCount();
    }

    private double pointsScanned(int openCount, Coll bucket) {
      return pointsScanned(openCount, factor(bucket.realmVector), bucket);
    }

    private int factor(int realmVector) {
      // Explanation: bits 0 and 1 have weights 1 and 2 respectively (they
      // correspond to blocks and lines).  Bit 2 is back to a weight of 1 (it
      // corresponds to locations).  The vector is > 3 when bit 2 is on, so we
      // subtract away the excess over the weight.
      return realmVector > 3 ? realmVector - 3 : realmVector;
    }

    public void report(PrintStream out) {
      out.printf("%s: %,d moves included, %,d the first bucket\n", kind, movesIncluded, movesFirstBucket);
      printCounters(out, flatFirstBucketCounter, firstBucketCounter,
                    firstBucketCounters, "1. When it was the first bucket");
//      printCounters(out, flatOtherBucketCounter, otherBucketCounter,
//                    otherBucketCounters, "2. When it was a later bucket");
//      printCounters(out, flatEitherCounter, eitherCounter, eitherCounters, "3. All moves");
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

    private void printCounters(PrintStream out, ProcessCounter flatCounter, ProcessCounter realmCounter,
                               LoadingCache<Integer, ProcessCounter> realmCounters,
                               String headline) {
      out.printf("%s:%n", headline);
      // out.printf(" Disregarding realms (%,d moves):%n", flatCounter.count);
      // flatCounter.report(out);
      out.printf(" All realms (%,d moves):%n", realmCounter.count);
      realmCounter.report(out);
      // for (int v = 0; v < vectorToSet.size(); ++v) {
      //   if (realmCounters.asMap().containsKey(v)) {
      //     ProcessCounter counter = realmCounters.getUnchecked(v);
      //     out.printf(" Realms: %s (%,d moves)%n", vectorToSet.get(v), counter.count);
      //     counter.report(out);
      //   }
      // }
    }
  }

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

  private static Reporter getReporter(
      int numTrails, int moveNumber, int effectiveMoveNumber,
      long timestamp, long effectiveTimestamp, int minOpen) {
    int index = Math.min(COUNT - 1, minOpen / 10);
    Reporter answer = reporters[index];
    if (answer == null)
      reporters[index] = answer = new Reporter();
    return answer;
  }

  private static void reportSummaries(PrintStream out) {
    for (int i = COUNT - 1; i >= 0; --i) {
      Reporter r = reporters[i];
      if (r != null)
        reportSummaries(out, r, headline(i));
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
