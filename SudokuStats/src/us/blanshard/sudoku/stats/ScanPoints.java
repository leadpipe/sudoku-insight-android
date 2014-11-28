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

import static java.lang.Math.min;

import us.blanshard.sudoku.insight.Insight.Realm;
import us.blanshard.sudoku.stats.Pattern.Coll;

import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Analyzes the output from {@link InsightMeasurer} to look for probability
 * distributions in Sudoku solving.
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

  // A simplified probability distribution.  We assume that everything's normal
  // and we can add and multiply and still get normal distibutions back.
  static class Dist {
    final double mean;
    final double variance;
    final int count;

    Dist(double mean, double variance, int count) {
      this.mean = mean;
      this.variance = variance;
      this.count = count;
    }

    @Override public String toString() {
      return String.format("μ=%.2g, σ=%.2g, n=%,d", mean, Math.sqrt(variance), count);
    }
  }

  // Returns a distribution approximating the binomial for the given number of
  // successes (r) and trials (n).
  static Dist forBinomial(int r, int n) {
    double p = (r + 1.0) / (n + 2.0);
    double var = p * (1 - p) / (n + 3);
    return new Dist(p, var, n);
  }

  static boolean less(Dist a, Dist b) {
    return a.mean < b.mean && a.variance < b.variance;
  }

  static Dist sum(Dist a, Dist b) {
    return new Dist(a.mean + b.mean, a.variance + b.variance, Math.min(a.count, b.count));
  }

  static Dist difference(Dist a, Dist b) {
    return new Dist(a.mean - b.mean, a.variance - b.variance, Math.min(a.count, b.count));
  }

  static Dist product(Dist a, Dist b) {
    return new Dist(a.mean * b.mean, a.variance * b.variance, Math.min(a.count, b.count));
  }

  static Dist quotient(Dist a, Dist b) {
    return new Dist(a.mean / b.mean, a.variance / b.variance, Math.min(a.count, b.count));
  }

  static Dist absoluteDifference(Dist a, Dist b) {
    return new Dist(Math.abs(a.mean - b.mean), Math.abs(a.variance - b.variance), Math.min(a.count, b.count));
  }

  static Dist pool(Dist a, Dist b) {
    if (a == null) return b;
    if (b == null) return a;
    int wa = a.count;  // Note: a.count - 1 may be more correct, but this is
    int wb = b.count;  // associative.
    return new Dist(
        (wa * a.mean + wb * b.mean) / (wa + wb),
        (wa * a.variance + wb * b.variance) / (wa + wb),
        wa + wb);
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
    private final LoadingCache<Sp, BatchProcess> processes;
    private final ProcessCounter trailheadCounter = new ProcessCounter();
    private int batchCount;
    private int moveCount;

    public Reporter() {
      processes = CacheBuilder.newBuilder().build(new CacheLoader<Sp, BatchProcess>() {
        @Override public BatchProcess load(Sp sp) {
          return new BatchProcess(sp);
        }
      });
    }

    public void takeTrailhead(double seconds, int openCount, List<Coll> missed) {
      trailheadCounter.count(seconds, 1);
      for (Coll coll : missed) {
        processes.getUnchecked(Sp.fromList(coll.patterns, openCount)).addMissed();
      }
    }

    public void takeBatch(double seconds, int openCount, Universe universe,
                          List<Coll> played, List<Coll> missed) {
      ++batchCount;
      for (Coll coll : played) {
        ++moveCount;
        processes.getUnchecked(Sp.fromList(coll.patterns, openCount)).take(seconds, openCount, universe);
      }
      for (Coll coll : missed) {
        processes.getUnchecked(Sp.fromList(coll.patterns, openCount)).addMissed();
      }
    }

    public void reportSummaries(PrintStream out) {
      out.printf("%,d batches, %,d moves (%.1f moves per batch)%n%n",
                 batchCount, moveCount, moveCount / (double) batchCount);

      final Set<Sp> singulars = Sets.newTreeSet();
      LoadingCache<Sp, SpInfo> infos = CacheBuilder.newBuilder().build(new CacheLoader<Sp, SpInfo>() {
        @Override public SpInfo load(Sp sp) {
          if (sp.isSingular()) singulars.add(sp);
          return new SpInfo(sp);
        }
      });

      ConcurrentMap<Sp, BatchProcess> p = processes.asMap();
      Set<Sp.Combination> combs = Sets.newTreeSet();
      Set<Sp.Implication> imps = Sets.newTreeSet();
      for (Sp sp : p.keySet()) {
        BatchProcess process = p.get(sp);
        infos.getUnchecked(sp).setProcess(process);
        switch (sp.getType()) {
          case COMBINATION: combs.add((Sp.Combination) sp); break;
          case IMPLICATION: imps.add((Sp.Implication) sp); break;
          default: break;
        }
      }

      for (Sp.Combination comb : combs) {
        SpInfo combInfo = infos.getUnchecked(comb);
        for (Sp part : comb.parts.elementSet()) {
          if (part.getType() == Sp.Type.IMPLICATION)
            imps.add((Sp.Implication) part);
          // comb = part or rest
          SpInfo partInfo = infos.getUnchecked(part);
          combInfo.addAdditivePart(partInfo);
          Sp rest = comb.minus(part);
          SpInfo restInfo = infos.getUnchecked(rest);
          partInfo.addAdditive(combInfo, restInfo);
        }
      }

      for (Sp.Implication imp : imps) {
        SpInfo impInfo = infos.getUnchecked(imp);
        for (Sp part : imp.antecedents.elementSet()) {
          // imp = part and rest
          SpInfo partInfo = infos.getUnchecked(part);
          impInfo.addMultiplicativePart(partInfo);
          Sp rest = imp.minus(part);
          SpInfo restInfo = infos.getUnchecked(rest);
          partInfo.addMultiplicative(impInfo, restInfo);
        }
      }

      for (Sp sp : singulars) {
        infos.getUnchecked(sp).report(out);
      }

      out.printf("%nTrailheads (%,d):%n", trailheadCounter.count);
      trailheadCounter.report(out);
      out.println();
    }
  }

  static class BatchProcess {
    private final Sp sp;
    private final LoadingCache<Integer, ProcessCounter> realmCounters;
    private final ProcessCounter overallCounter = new ProcessCounter();
    private int movesMissed;
    private int movesMade;
    private final boolean reportByRealm = false;

    public BatchProcess(Sp sp) {
      this.sp = sp;
      realmCounters = CacheBuilder.newBuilder().build(new CacheLoader<Integer, ProcessCounter>() {
        @Override public ProcessCounter load(Integer realmVector) {
          return new ProcessCounter();
        }
      });
    }

    public Sp sp() {
      return sp;
    }

    public int movesMade() {
      return movesMade;
    }

    public int movesMissed() {
      return movesMissed;
    }

    public int totalMoves() {
      return movesMade + movesMissed;
    }

    public void addMissed() {
      ++movesMissed;
    }

    public void take(double seconds, int openCount, Universe universe) {
      realmCounters.getUnchecked(universe.realmVector)
          .count(seconds, pointsScanned(openCount, universe));
      overallCounter.count(seconds, pointsScanned(openCount, 4, universe));
      ++movesMade;
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

    public Dist getDist() {
      return forBinomial(movesMade, movesMade + movesMissed);
    }

    public void report(PrintStream out) {
      out.println(" - Overall:");
      overallCounter.report(out);

      if (reportByRealm) {
        out.println(" - By realm vector:");
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

  // Aggregates distribution information about a specific Sp.
  static class SpInfo {
    final Sp sp;
    BatchProcess process;
    Dist direct;
    final Collection<SpInfoPair> additives = Lists.newArrayList();
    final Collection<SpInfoPair> multiplicatives = Lists.newArrayList();
    final Collection<SpInfo> inverted = Lists.newArrayList();
    Dist pooledAdditiveImplied;
    Dist pooledMultiplicativeImplied;
    Dist pooledSupremum;
    Dist pooledInfimum;
    int additivesOut;
    int multiplicativesOut;

    SpInfo(Sp sp) {
      this.sp = sp;
    }

    void setProcess(BatchProcess process) {
      this.process = process;
      this.direct = process.getDist();
    }

    boolean hasEnoughData() {
      return direct != null && direct.count >= 20;
    }

    void addAdditive(SpInfo combInfo, SpInfo restInfo) {
      if (combInfo.hasEnoughData()) {
        SpInfoPair p = new SpInfoPair(combInfo, restInfo);
        additives.add(p);
        if (restInfo.hasEnoughData() && less(restInfo.direct, combInfo.direct)) {
          p.implied = quotient(
              difference(combInfo.direct, restInfo.direct),
              difference(new Dist(1, 1, restInfo.direct.count), restInfo.direct));
          pooledAdditiveImplied = pool(pooledAdditiveImplied, p.implied);
        } else {
          if (restInfo.hasEnoughData() && !less(restInfo.direct, combInfo.direct))
            combInfo.inverted.add(restInfo);
          p.supremum = combInfo.direct;
          pooledSupremum = pool(pooledSupremum, p.supremum);
        }
      } else {
        ++additivesOut;
      }
    }

    void addAdditivePart(SpInfo partInfo) {
    }

    void addMultiplicative(SpInfo impInfo, SpInfo restInfo) {
      if (impInfo.hasEnoughData()) {
        SpInfoPair p = new SpInfoPair(impInfo, restInfo);
        multiplicatives.add(p);
        if (restInfo.hasEnoughData() && less(impInfo.direct, restInfo.direct)) {
          p.implied = quotient(impInfo.direct, restInfo.direct);
          pooledMultiplicativeImplied = pool(pooledMultiplicativeImplied, p.implied);
        } else {
          if (restInfo.hasEnoughData() && !less(impInfo.direct, restInfo.direct))
            impInfo.inverted.add(restInfo);
          p.infimum = impInfo.direct;
          pooledInfimum = pool(pooledInfimum, p.infimum);
        }
      } else {
        ++multiplicativesOut;
      }
    }

    void addMultiplicativePart(SpInfo partInfo) {
    }

    void report(PrintStream out) {
      if (process == null) {
        out.printf("%s: no direct data%n", sp);
      } else {
        out.printf("%s: %,d moves made, %,d moves missed (%s)%n",
                   sp, process.movesMade, process.movesMissed, direct);
      }

      if (additivesOut > 0 || !additives.isEmpty()) {
        reportAdditives(out);
      }

      if (multiplicativesOut > 0 || !multiplicatives.isEmpty()) {
        reportMultiplicatives(out);
      }

      if (process == null) {
        out.println();
      } else {
        process.report(out);
      }
    }

    void reportDist(PrintStream out, Dist d, String name) {
      if (d != null)
        out.printf("%s %s%n", name, d);
    }

    void reportAdditives(PrintStream out) {
      out.printf(" -- Additives (%,d in, %,d out) --%n", additives.size(), additivesOut);
      if (reportDetails) {
        for (SpInfoPair p : additives) {
          out.printf("  - comb: %s (%s)%n    rest: %s (%s)%n", p.collInfo.sp, p.collInfo.direct,
                     p.restInfo.sp, p.restInfo.direct);
          reportDist(out, p.implied, "    implied");
          reportDist(out, p.supremum, "    sup");
        }
        reportDist(out, direct, "Direct");
      }
      reportDist(out, pooledAdditiveImplied, "Pooled Implied:");
      reportDist(out, pooledSupremum, "Pooled Supremum:");
    }

    void reportMultiplicatives(PrintStream out) {
      out.printf(" -- Multiplicatives (%,d in, %,d out) --%n", multiplicatives.size(), multiplicativesOut);
      if (reportDetails) {
        for (SpInfoPair p : multiplicatives) {
          out.printf("  - imp: %s (%s)%n    rest: %s (%s)%n", p.collInfo.sp, p.collInfo.direct,
                     p.restInfo.sp, p.restInfo.direct);
          reportDist(out, p.implied, "    implied");
          reportDist(out, p.infimum, "    inf");
        }
      }
      reportDist(out, pooledMultiplicativeImplied, "Pooled Implied:");
      reportDist(out, pooledInfimum, "Pooled Infimum:");
    }
  }

  static class SpInfoPair {
    final SpInfo collInfo;
    final SpInfo restInfo;
    Dist implied;
    Dist supremum;
    Dist infimum;

    SpInfoPair(SpInfo collInfo, SpInfo restInfo) {
      this.collInfo = collInfo;
      this.restInfo = restInfo;
    }
  }

  // ============================

  private static final int COUNT = 6;
  private static final Reporter[] reporters = new Reporter[COUNT];
  private static final Reporter reporter = new Reporter();
  private static final boolean singleReporter = true;
  private static final boolean reportDetails = false;

  private static Reporter getReporter(
      int minOpen, int numTrails, long timestamp,
      long effectiveTimestamp, int moveNumber, int effectiveMoveNumber) {
    if (singleReporter) return reporter;
    int index = min(COUNT - 1, minOpen / 10);
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
