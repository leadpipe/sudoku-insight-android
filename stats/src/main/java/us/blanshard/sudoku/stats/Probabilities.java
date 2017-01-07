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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.min;

import us.blanshard.sudoku.insight.Evaluator;
import us.blanshard.sudoku.stats.Pattern.Coll;

import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

/**
 * Analyzes the output from {@link InsightMeasurer} to look for probability
 * distributions in Sudoku solving.
 */
public class Probabilities {
  public static void main(String[] args) throws IOException {
    final int numBuckets = 6;
    final Bucketer bucketer = new MinOpenBucketer(numBuckets);
    Probabilities instance = new Probabilities(bucketer);

    BufferedReader in = new BufferedReader(new FileReader("measurer.txt"));
    Splitter splitter = Splitter.on('\t');
    for (String line; (line = in.readLine()) != null;) {
      Iterator<String> iter = splitter.split(line).iterator();
      boolean isTrailhead = Boolean.parseBoolean(iter.next());
      iter.next();  // elapsed ms
      int openCount = Integer.parseInt(iter.next());
      int minOpen = Integer.parseInt(iter.next());
      int numTrails = Integer.parseInt(iter.next());
      long timestamp = Long.parseLong(iter.next());
      long effectiveTimestamp = Long.parseLong(iter.next());
      int moveNumber = Integer.parseInt(iter.next());
      int effectiveMoveNumber = Integer.parseInt(iter.next());

      Reporter reporter =
          instance.getReporter(openCount, minOpen, numTrails, timestamp, effectiveTimestamp,
              moveNumber, effectiveMoveNumber);
      if (!isTrailhead) {
        reporter.notePlayed(openCount, Pattern.collsFromString(iter.next()));
      }
      reporter.noteMissed(openCount, Pattern.collsFromString(iter.next()));
      if (iter.hasNext()) {
        in.close();
        throw new IOException("Mismatch in line consumption");
      }
    }
    in.close();
    instance.reportSummaries(System.out);
  }

  // A simplified probability distribution. We assume that everything's normal
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
    return new Dist(Math.abs(a.mean - b.mean), Math.abs(a.variance - b.variance), Math.min(a.count,
        b.count));
  }

  static Dist pool(Dist a, Dist b) {
    if (a == null)
      return b;
    if (b == null)
      return a;
    int wa = a.count;  // Note: a.count - 1 may be more correct, but this is
    int wb = b.count;  // associative.
    return new Dist((wa * a.mean + wb * b.mean) / (wa + wb), (wa * a.variance + wb * b.variance)
        / (wa + wb), wa + wb);
  }

  static double mean(Dist d) {
    return d == null ? Double.NaN : d.mean;
  }

  static class Reporter {
    private final LoadingCache<Sp, SpCounter> counters;
    private int batchCount;
    private int moveCount;

    public Reporter() {
      counters = CacheBuilder.newBuilder().build(new CacheLoader<Sp, SpCounter>() {
        @Override public SpCounter load(Sp sp) {
          return new SpCounter(sp);
        }
      });
    }

    public void noteMissed(int openCount, List<Coll> missed) {
      for (Coll coll : missed) {
        boolean allImps = coll.areAllImplications();
        Collection<Sp> sps = spsFromPatterns(openCount, coll);
        for (Sp sp : sps) {
          counters.getUnchecked(sp).addMissed(allImps, coll.patterns.size());
        }
      }
    }

    public void notePlayed(int openCount, List<Coll> played) {
      ++batchCount;
      for (Coll coll : played) {
        ++moveCount;
        boolean allImps = coll.areAllImplications();
        Collection<Sp> sps = spsFromPatterns(openCount, coll);
        for (Sp sp : sps) {
          counters.getUnchecked(sp).addPlayed(allImps, coll.patterns.size());
        }
      }
    }

    private Collection<Sp> spsFromPatterns(int openCount, Coll coll) {
      Sp unlikelyForcedNumeral = null;
      boolean likelyForcedLocationFound = false;
      List<Sp> sps = Lists.newArrayList();
      for (Pattern p : coll.patterns) {
        Sp sp = Sp.fromPattern(p, openCount);
        if (sp == null) continue;
        sps.add(sp);
        if (isUnlikelyForcedNumeral(sp)) unlikelyForcedNumeral = sp;
        else if (isLikelyForcedLocation(sp)) likelyForcedLocationFound = true;
      }
      if (likelyForcedLocationFound && unlikelyForcedNumeral != null) {
        // We're just dropping the unlikely ones, because there is a strong
        // correlation between them and the likely ones, and they are in fact
        // quite difficult to see.  The likely ones therefore increase the
        // apparent likelihood of the unlikely but correlated ones.
        sps.remove(unlikelyForcedNumeral);
      }
      return sps;
    }

    private boolean isUnlikelyForcedNumeral(Sp sp) {
      // It's unlikely if it's an implication containing at least one naked
      // set with !sameNumeral, and leading to a forced numeral.
      boolean containsNakedSet = false;
      while (sp.type == Sp.Type.IMPLICATION) {
        Sp.Implication imp = (Sp.Implication) sp;
        for (Sp ant : imp.antecedents) {
          if (ant.type == Sp.Type.LOCKED_SET) {
            Sp.LockedSet set = (Sp.LockedSet) ant;
            if (set.isNaked() && !set.sameNumeral) containsNakedSet = true;
          }
        }
        sp = imp.consequent;
      }
      return containsNakedSet && sp.type == Sp.Type.FORCED_NUMERAL;
    }

    private boolean isLikelyForcedLocation(Sp sp) {
      // It's likely if it's a forced location, or any series of overlaps or
      // small hidden sets leading to one.
      while (sp.type == Sp.Type.IMPLICATION) {
        Sp.Implication imp = (Sp.Implication) sp;
        for (Sp ant : imp.antecedents) {
          if (ant.type == Sp.Type.OVERLAP) continue;
          if (ant.type == Sp.Type.FORCED_LOCATION) continue;
          if (ant.type == Sp.Type.LOCKED_SET) {
            Sp.LockedSet set = (Sp.LockedSet) ant;
            if (!set.isNaked() && set.size() == 2) continue;
          }
          return false;
        }
        sp = imp.consequent;
      }
      return sp.type == Sp.Type.FORCED_LOCATION;
    }

    public double[] reportSummaries(PrintStream out) {
      double[] probabilities = new double[Evaluator.Pattern.values().length];
      Arrays.fill(probabilities, Double.NaN);
      out.printf("%,d batches, %,d moves (%.2g moves per batch)%n%n", batchCount, moveCount,
          moveCount / (double) batchCount);

      final Set<Sp> singulars = Sets.newTreeSet();
      LoadingCache<Sp, SpInfo> infos =
          CacheBuilder.newBuilder().build(new CacheLoader<Sp, SpInfo>() {
            @Override public SpInfo load(Sp sp) {
              if (sp.isSingular())
                singulars.add(sp);
              return new SpInfo(sp);
            }
          });

      ConcurrentMap<Sp, SpCounter> p = counters.asMap();
      Set<Sp.Implication> imps = Sets.newTreeSet();
      for (Sp sp : p.keySet()) {
        SpCounter counter = p.get(sp);
        infos.getUnchecked(sp).setCounter(counter);
        if (sp.getType() == Sp.Type.IMPLICATION && sp.isMove())
          imps.add((Sp.Implication) sp);
      }

      for (Sp.Implication imp : imps) {
        SpInfo impInfo = infos.getUnchecked(imp);
        for (Sp antecedent : imp.antecedents.elementSet()) {
          // imp = antecedent and rest
          SpInfo antecedentInfo = infos.getUnchecked(antecedent);
          Sp rest = imp.minus(antecedent);
          SpInfo restInfo = infos.getUnchecked(rest);
          antecedentInfo.infer(impInfo, restInfo);
        }
      }

      for (Sp sp : singulars) {
        probabilities[sp.getEvaluatorPattern().ordinal()] = infos.getUnchecked(sp).report(out);
      }

      out.println();
      return probabilities;
    }
  }

  // The base class for counting played/missed.
  static class Counter {
    private int missed;
    private int played;

    public int played() {
      return played;
    }

    public int missed() {
      return missed;
    }

    public int total() {
      return played + missed;
    }

    public void addMissed() {
      ++missed;
    }

    public void addPlayed() {
      ++played;
    }

    public Dist getDist() {
      return forBinomial(played, total());
    }
  }

  static class SpCounter extends Counter {
    private final Sp sp;
    @Nullable private final Counter allImpsCounter;

    public SpCounter(Sp sp) {
      this.sp = sp;
      this.allImpsCounter = sp.type == Sp.Type.IMPLICATION ? new Counter() : null;
    }

    public Sp sp() {
      return sp;
    }

    public void addMissed(boolean allImps, int size) {
      addMissed();
      if (sp.isMove() && allImps)
        allImpsCounter.addMissed();
    }

    public void addPlayed(boolean allImps, int size) {
      addPlayed();
      if (sp.isMove() && allImps)
        allImpsCounter.addPlayed();
    }

    @Nullable public Dist getAllImpsDist() {
      return allImpsCounter == null ? null : allImpsCounter.getDist();
    }
  }

  // Aggregates distribution information about a specific Sp.
  static class SpInfo {
    final Sp sp;
    SpCounter counter;
    Dist dist;
    Dist pooledInferred;
    int numLess;
    int numPooled;
    int maxMissedCount;
    int maxLessCount;

    SpInfo(Sp sp) {
      this.sp = sp;
    }

    void setCounter(SpCounter counter) {
      this.counter = counter;
      //this.dist = sp.type == Sp.Type.IMPLICATION ? counter.getAllImpsDist() : counter.getDist();
      this.dist = counter.getDist();
    }

    boolean hasEnoughData() {
      return dist != null && dist.count >= 40;
    }

    void infer(SpInfo impInfo, SpInfo restInfo) {
      if (impInfo.hasEnoughData() && restInfo.hasEnoughData()) {
        Dist inferred = quotient(impInfo.dist, restInfo.dist);
        pooledInferred = pool(pooledInferred, inferred);
        ++numPooled;
        if (impInfo.dist.mean < restInfo.dist.mean) {
          ++numLess;
        }
      } else if (impInfo.dist != null && restInfo.dist != null) {
        int minCount = Math.min(impInfo.dist.count, restInfo.dist.count);
        if (minCount > maxMissedCount)
          maxMissedCount = minCount;
        if (minCount > maxLessCount && impInfo.dist.count < restInfo.dist.count)
          maxLessCount = minCount;
      }
    }

    double report(PrintStream out) {
      if (counter == null) {
        out.printf("%-8s inferred %-24s  less %3.0f%% %d/%d%n", sp, pooledInferred, 100.0 * numLess
            / numPooled, numLess, numPooled);
        return mean(pooledInferred);
      } else {
        out.printf("%-8s %s%n", sp, dist);
        return mean(dist);
      }
    }
  }

  abstract static class Bucketer {
    public final int numBuckets;

    Bucketer(int numBuckets) {
      checkArgument(numBuckets > 0, "bad count %s");
      this.numBuckets = numBuckets;
    }

    abstract int getBucket(int openCount, int minOpen, int numTrails, long timestamp,
        long effectiveTimestamp, int moveNumber, int effectiveMoveNumber);

    String headline(int bucket) {
      if (numBuckets == 1)
        return "All in one bucket";
      if (bucket == numBuckets - 1)
        return String.format("%s: %d or more", getBucketsDescription(), getBucketLeast(bucket));
      return String.format("%s: %d - %d", getBucketsDescription(),
          getBucketLeast(bucket), getBucketMost(bucket));
    }

    abstract int getBucketLeast(int bucket);
    abstract int getBucketMost(int bucket);
    abstract String getBucketsDescription();
  }

  abstract static class LocCountBucketer extends Bucketer {
    protected final int bucketSize;

    LocCountBucketer(int numBuckets) {
      super(numBuckets);
      this.bucketSize = 60 / numBuckets;
    }

    @Override int getBucket(int openCount, int minOpen, int numTrails, long timestamp,
        long effectiveTimestamp, int moveNumber, int effectiveMoveNumber) {
      final int locCount =
          getLocCount(openCount, minOpen, numTrails, timestamp, effectiveTimestamp, moveNumber,
              effectiveMoveNumber);
      return min(numBuckets - 1, locCount / bucketSize);
    }

    @Override int getBucketLeast(int bucket) {
      return bucket * bucketSize;
    }
    @Override int getBucketMost(int bucket) {
      return getBucketLeast(bucket) + bucketSize - 1;
    }

    abstract int getLocCount(int openCount, int minOpen, int numTrails, long timestamp,
        long effectiveTimestamp, int moveNumber, int effectiveMoveNumber);
  }

  static class OpenCountBucketer extends LocCountBucketer {
    public OpenCountBucketer(int numBuckets) {
      super(numBuckets);
    }

    @Override int getLocCount(int openCount, int minOpen, int numTrails, long timestamp,
        long effectiveTimestamp, int moveNumber, int effectiveMoveNumber) {
      return openCount;
    }

    @Override String getBucketsDescription() {
      return "Open counts";
    }
  }

  static class MinOpenBucketer extends LocCountBucketer {
    public MinOpenBucketer(int numBuckets) {
      super(numBuckets);
    }

    @Override int getLocCount(int openCount, int minOpen, int numTrails, long timestamp,
        long effectiveTimestamp, int moveNumber, int effectiveMoveNumber) {
      return minOpen;
    }

    @Override String getBucketsDescription() {
      return "Min open counts";
    }
  }

  // ============================

  private final Bucketer bucketer;
  private final Reporter[] reporters;

  private Probabilities(Bucketer bucketer) {
    this.bucketer = bucketer;
    this.reporters = new Reporter[bucketer.numBuckets];
  }

  private Reporter getReporter(int openCount, int minOpen, int numTrails, long timestamp,
      long effectiveTimestamp, int moveNumber, int effectiveMoveNumber) {
    int index = bucketer.getBucket(openCount, minOpen, numTrails, timestamp, effectiveTimestamp, moveNumber, effectiveMoveNumber);
    Reporter answer = reporters[index];
    if (answer == null)
      reporters[index] = answer = new Reporter();
    return answer;
  }

  private void reportSummaries(PrintStream out) {
    int numBuckets = bucketer.numBuckets;
    double[][] probabilities = new double[numBuckets][];
    for (int i = 0; i < numBuckets; ++i) {
      Reporter r = reporters[i];
      if (r != null)
        probabilities[i] = reportSummaries(out, r, bucketer.headline(i));
    }
    out.println("Means per pattern:");
    for (Evaluator.Pattern p : Evaluator.Pattern.values()) {
      out.printf("    %s(", p);
      for (int i = 0; i < numBuckets; ++i) {
        if (i > 0)
          out.print(", ");
        out.printf("%.3g", probabilities[i][p.ordinal()]);
      }
      out.println("),");
    }
  }

  private double[] reportSummaries(PrintStream out, Reporter reporter, String desc) {
    out.printf("%n======================%n%s%n======================%n%n", desc);
    return reporter.reportSummaries(out);
  }
}