/*
  Copyright 2014 Luke Blanshard

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

import static java.lang.Math.min;

import us.blanshard.sudoku.stats.Pattern.Coll;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;

import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;

/**
 * Analyzes the output from {@link InsightMeasurer} to look for timing
 * distributions in Sudoku solving.
 */
public class ScanCost {
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

      Reporter reporter = getReporter(
          minOpen, numTrails, timestamp, effectiveTimestamp, moveNumber, effectiveMoveNumber);
      double seconds = ms / 1000.0;
      if (isTrailhead) {
        reporter.takeTrailhead(seconds, openCount, Pattern.collsFromString(iter.next()));
      } else {
        reporter.takeBatch(seconds, openCount, new Universe(iter), Pattern.collsFromString(iter.next()),
                           Pattern.collsFromString(iter.next()));
      }
      if (iter.hasNext()) {
        in.close();
        throw new IOException("Mismatch in line consumption");
      }
    }
    in.close();
    reportSummaries(System.out);
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
    private final ProcessCounter trailheadCounter = new ProcessCounter();
    private final ProcessCounter combinedCounter = new ProcessCounter();

    public Reporter() {}

    public void reportSummaries(PrintStream out) {
      out.printf("Combined moves & errors (%,d):%n", combinedCounter.count);
      combinedCounter.report(out);
      out.println();
      out.println();
      out.printf("Trailheads (%,d):%n", trailheadCounter.count);
      trailheadCounter.report(out);
      out.println();
      out.println();
    }

    public void takeTrailhead(double seconds, int openCount, List<Coll> missed) {
      if (missed.isEmpty()) trailheadCounter.count(seconds, openCount);
    }

    public void takeBatch(double seconds, int openCount, Universe universe,
                          List<Coll> played, List<Coll> missed) {
      double playedWeight = scanWeight(openCount, played);
      double missedWeight = scanWeight(openCount, missed);
      double totalWeight = playedWeight + missedWeight;
      combinedCounter.count(seconds, openCount * playedWeight / totalWeight);
    }

    private double scanWeight(int openCount, Iterable<Coll> colls) {
      double weight = 0;
      for (Coll coll : colls) {
        for (Pattern p : coll.patterns)
          weight += Sp.fromPattern(p, openCount).getProbabilityOfPlaying(openCount);
      }
      return weight;
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
  private static final int SIZE = 60 / COUNT;
  private static final Reporter[] reporters = new Reporter[COUNT];
  private static final Reporter reporter = new Reporter();
  private static final boolean singleReporter = false;

  private static Reporter getReporter(
      int minOpen, int numTrails, long timestamp,
      long effectiveTimestamp, int moveNumber, int effectiveMoveNumber) {
    if (singleReporter) return reporter;
    int index = min(COUNT - 1, minOpen / SIZE);
    Reporter answer = reporters[index];
    if (answer == null)
      reporters[index] = answer = new Reporter();
    return answer;
  }

  private static void reportSummaries(PrintStream out) {
    if (singleReporter) {
      reportSummaries(out, reporter, "Everything together in one bunch");
    } else {
      for (int i = 0; i < COUNT; ++i) {
        Reporter r = reporters[i];
        if (r != null)
          reportSummaries(out, r, headline(i));
      }
    }
  }

  private static String headline(int i) {
    int min = i * SIZE;
    int max = min + SIZE - 1;
    if (i == COUNT - 1)
      return String.format("Minimum open: %d or more", min);
    return String.format("Minimum open: %d - %d", min, max);
  }

  private static void reportSummaries(PrintStream out, Reporter reporter, String desc) {
    out.printf("%n======================%n%s%n======================%n%n", desc);
    reporter.reportSummaries(out);
  }
}
