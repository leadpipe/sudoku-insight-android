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
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Analyzes the output from {@link InsightMeasurer} to look for probability
 * distributions in Sudoku solving.
 */
public class Probabilities {
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
          openCount, minOpen, numTrails, timestamp, effectiveTimestamp, moveNumber, effectiveMoveNumber);
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
      out.printf("%,d batches, %,d moves (%.2g moves per batch)%n%n",
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
          Sp rest = comb.minus(part);
          SpInfo restInfo = infos.getUnchecked(rest);
          partInfo.addAdditive(combInfo, restInfo);
        }
      }

      for (Sp.Implication imp : imps) {
        SpInfo impInfo = infos.getUnchecked(imp);
        for (Sp part : imp.parts.elementSet()) {
          // imp = part and rest
          SpInfo partInfo = infos.getUnchecked(part);
          Sp rest = imp.minus(part);
          SpInfo restInfo = infos.getUnchecked(rest);
          partInfo.addMultiplicative(impInfo, restInfo);
        }
      }

      for (Sp sp : singulars) {
        infos.getUnchecked(sp).report(out);
      }

      out.println();
    }
  }

  static class BatchProcess {
    private final Sp sp;
    private int movesMissed;
    private int movesMade;

    public BatchProcess(Sp sp) {
      this.sp = sp;
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
      ++movesMade;
    }

    public Dist getDist() {
      return forBinomial(movesMade, movesMade + movesMissed);
    }
  }

  // Aggregates distribution information about a specific Sp.
  static class SpInfo {
    final Sp sp;
    BatchProcess process;
    Dist direct;
    final Collection<SpInfo> inverted = Lists.newArrayList();
    Dist pooledAdditive;
    Dist pooledSupremum;
    Dist pooledMultiplicative;
    Dist pooledAdditiveMultiplicative;
    Dist pooledInfimum;

    SpInfo(Sp sp) {
      this.sp = sp;
    }

    void setProcess(BatchProcess process) {
      this.process = process;
      this.direct = process.getDist();
    }

    boolean hasEnoughData() {
      return direct != null && direct.count >= 1;
    }

    void addAdditive(SpInfo combInfo, SpInfo restInfo) {
      if (combInfo.hasEnoughData() && restInfo.hasEnoughData() && less(restInfo.direct, combInfo.direct)) {
        Dist implied = difference(combInfo.direct, restInfo.direct);
        pooledAdditive = pool(pooledAdditive, implied);
      }
      Dist supremum = combInfo.direct;
      pooledSupremum = pool(pooledSupremum, supremum);
    }

    void addMultiplicative(SpInfo impInfo, SpInfo restInfo) {
      if (impInfo.hasEnoughData() && restInfo.hasEnoughData() && less(impInfo.direct, restInfo.direct)) {
        Dist implied = quotient(impInfo.direct, restInfo.direct);
        pooledMultiplicative = pool(pooledMultiplicative, implied);
      }
      if (impInfo.pooledAdditive != null && restInfo.pooledAdditive != null
          && less(impInfo.pooledAdditive, restInfo.pooledAdditive)) {
        Dist implied = quotient(impInfo.pooledAdditive, restInfo.pooledAdditive);
        pooledAdditiveMultiplicative = pool(pooledAdditiveMultiplicative, implied);
      }
      Dist infimum = impInfo.direct;
      pooledInfimum = pool(pooledInfimum, infimum);
    }

    void report(PrintStream out) {
      if (process == null) {
        out.printf("%s: no direct data%n", sp);
        reportMultiplicatives(out);
      } else {
        out.printf("%s: %,d moves made, %,d moves missed (%s)%n",
                   sp, process.movesMade, process.movesMissed, direct);
        reportAdditives(out);
      }
    }

    void reportDist(PrintStream out, Dist d, String name) {
      if (d != null)
        out.printf("%s %s%n", name, d);
    }

    void reportAdditives(PrintStream out) {
      reportDist(out, pooledAdditive, "  When added:");
      reportDist(out, pooledSupremum, "                                                    Supremum:");
    }

    void reportMultiplicatives(PrintStream out) {
      reportDist(out, pooledMultiplicative, "  When multiplied direct:");
      reportDist(out, pooledAdditiveMultiplicative, "  When multiplied while adding:");
      reportDist(out, pooledInfimum, "                                                    Infimum:");
    }
  }

  // ============================

  private static final int COUNT = 3;
  private static final int SIZE = 60 / COUNT;
  private static final Reporter[] reporters = new Reporter[COUNT];
  private static final Reporter reporter = new Reporter();
  private static final boolean singleReporter = false;

  private static Reporter getReporter(
      int openCount, int minOpen, int numTrails, long timestamp,
      long effectiveTimestamp, int moveNumber, int effectiveMoveNumber) {
    if (singleReporter) return reporter;
    int index = min(COUNT - 1, openCount / SIZE);
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
      return String.format("Open squares: %d or more", min);
    return String.format("Open squares: %d - %d", min, max);
  }

  private static void reportSummaries(PrintStream out, Reporter reporter, String desc) {
    out.printf("%n======================%n%s%n======================%n%n", desc);
    reporter.reportSummaries(out);
  }
}
