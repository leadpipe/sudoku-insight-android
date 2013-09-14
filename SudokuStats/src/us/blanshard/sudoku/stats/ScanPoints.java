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

import us.blanshard.sudoku.stats.Pattern.ForcedNum;

import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;

import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;

/**
 * Analyzes the output from {@link InsightMeasurer} to look for Poisson
 * processes in Sudoku solving.
 */
public class ScanPoints {
  public static void main(String[] args) throws IOException {
    BufferedReader in = new BufferedReader(new FileReader("measurer.txt"));
    Splitter splitter = Splitter.on('\t');
    Reporter reporter = new Reporter();
    for (String line; (line = in.readLine()) != null; ) {
      Iterator<String> iter = splitter.split(line).iterator();
      List<Pattern> found = Pattern.listFromString(iter.next());
      long ms = Long.parseLong(iter.next());
      int openCount = Integer.parseInt(iter.next());
      iter.next();  // numAssignments
      List<List<Pattern>> missed = Pattern.combinationsFromString(iter.next());
      reporter.take(found, missed, ms, openCount);
    }
    in.close();
    reporter.reportSummaries(System.out);
  }

  static boolean hasSimpleAntecedents(Pattern.Implication p) {
    int count = 0;
    do {
      for (Pattern a : p.getAntecedents()) {
        if (++count > 3) return false;
        switch (a.getType()) {
          case OVERLAP:
            if (((Pattern.Overlap) a).getCategory() == Pattern.UnitCategory.LINE)
              return false;
            break;
          case LOCKED_SET: {
            Pattern.LockedSet l = (Pattern.LockedSet) a;
            if (l.isNaked() || l.getCategory() == Pattern.UnitCategory.LINE
                || l.getSetSize() > 3)
              return false;
            break;
          }
          default:
            return false;
        }
      }
      p = p.getConsequent().getType() == Pattern.Type.IMPLICATION
          ? (Pattern.Implication) p.getConsequent()
          : null;
    } while (p != null);
    return true;
  }

  static boolean isEasyFn(Pattern.ForcedNum p) {
    Sp.PeerMetrics pm = new Sp.PeerMetrics(p.getMetrics());
    return pm.openInBlock < 3 && !pm.bothLinesRequired;
  }

  static final Predicate<Pattern> matchFlsOnly = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      return p.getType() == Pattern.Type.FORCED_LOCATION;
    }
  };

  static final Predicate<Pattern> matchAllImpliedFls = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      return p.getNub().getType() == Pattern.Type.FORCED_LOCATION;
    }
  };

  static final Predicate<Pattern> matchSimplyImpliedFls = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      switch (p.getType()) {
        case FORCED_LOCATION: return true;
        default:
          if (p.getType() != Pattern.Type.IMPLICATION) return false;
          return hasSimpleAntecedents((Pattern.Implication) p);
      }
    }
  };

  static final Predicate<Pattern> matchFlsAndFns = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      switch (p.getType()) {
        case FORCED_LOCATION:
        case FORCED_NUMERAL: return true;
        default: return false;
      }
    }
  };

  static final Predicate<Pattern> matchAllImpliedFlsAndFns = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      switch (p.getNub().getType()) {
        case FORCED_LOCATION:
        case FORCED_NUMERAL: return true;
        default: return false;
      }
    }
  };

  static final Predicate<Pattern> matchSimplyImpliedFlsAndFns = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      switch (p.getType()) {
        case FORCED_LOCATION:
        case FORCED_NUMERAL: return true;
        default:
          if (p.getType() != Pattern.Type.IMPLICATION) return false;
          return hasSimpleAntecedents((Pattern.Implication) p);
      }
    }
  };

  static final Predicate<Pattern> matchFlsAndEasyFns = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      switch (p.getType()) {
        case FORCED_LOCATION: return true;
        case FORCED_NUMERAL: return isEasyFn((ForcedNum) p);
        default: return false;
      }
    }
  };

  static final Predicate<Pattern> matchAllImpliedFlsAndEasyFns = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      switch (p.getNub().getType()) {
        case FORCED_LOCATION: return true;
        case FORCED_NUMERAL: return isEasyFn((ForcedNum) p.getNub());
        default: return false;
      }
    }
  };

  static final Predicate<Pattern> matchSimplyImpliedFlsAndEasyFns = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      switch (p.getType()) {
        case FORCED_LOCATION: return true;
        case FORCED_NUMERAL: return isEasyFn((ForcedNum) p);
        default:
          if (p.getType() != Pattern.Type.IMPLICATION) return false;
          if (p.getNub().getType() == Pattern.Type.FORCED_NUMERAL
              && !isEasyFn((ForcedNum) p.getNub())) return false;
          return hasSimpleAntecedents((Pattern.Implication) p);
      }
    }
  };

  interface Consumer {
    void take(List<Pattern> found, List<List<Pattern>> missed, long ms, int openCount);
  }

  static class Reporter implements Consumer {
    private final List<Process> processes;

    public Reporter() {
      processes = ImmutableList.<Process>builder()
          .add(make(matchFlsOnly, "Forced locations only"))
          .add(make(matchAllImpliedFls, "All implied forced locations"))
          .add(make(matchSimplyImpliedFls, "Simply-implied forced locations"))
          .add(make(matchFlsAndFns, "Direct assignments"))
          .add(make(matchAllImpliedFlsAndFns, "All implied assignments"))
          .add(make(matchSimplyImpliedFls, "Simply-implied assignments"))
          .add(make(matchFlsAndEasyFns, "Forced locations and easy forced numerals"))
          .add(make(matchAllImpliedFlsAndEasyFns, "All implied forced locations and easy forced numerals"))
          .add(make(matchSimplyImpliedFlsAndEasyFns, "Simply-implied forced locations and easy forced numerals"))
          .build();
    }

    private static Process make(Predicate<Pattern> matches, String description) {
      return new Process(description, matches);
    }

    public void reportSummaries(PrintStream out) {
      for (Process p : processes)
        p.report(out);
    }

    @Override public void take(List<Pattern> found, List<List<Pattern>> missed, long ms,
        int openCount) {
      for (Process p : processes)
        p.take(found, missed, ms, openCount);
    }
  }

  static class Process implements Consumer {
    private final String description;
    private final Predicate<Pattern> matches;
    private final ProcessCounter counter;
    private long movesIncluded;
    private long movesSkipped;

    public Process(String description, Predicate<Pattern> matches) {
      this.description = description;
      this.matches = matches;
      this.counter = new ProcessCounter();
    }

    @Override public void take(List<Pattern> found, List<List<Pattern>> missed, long ms,
        int openCount) {
      if (!Iterables.any(found, matches)) {
        if (found.size() > 0) ++movesSkipped;
        return;
      }
      ++movesIncluded;

      int matchingAssignments = 1;
      for (List<Pattern> list : missed)
        if (Iterables.any(list, matches))
          ++matchingAssignments;

      double seconds = ms / 1000.0;
      double pointsScanned = openCount / (double) matchingAssignments;
      double secondsPerPoint = seconds / pointsScanned;
      counter.count(secondsPerPoint);
    }

    public void report(PrintStream out) {
      out.printf("%s: %,d moves included, %,d skipped\n", description, movesIncluded, movesSkipped);
      counter.report(out);
      out.println();
    }
  }

  static class ProcessCounter {
    private final Multiset<Integer> pointsPerSecond = HashMultiset.create();
    private double pendingSeconds;
    private int pendingPoints;

    public void count(double secondsPerPoint) {
      pendingSeconds += secondsPerPoint;
      while (pendingSeconds >= 1.0) {
        pointsPerSecond.add(pendingPoints);
        pendingPoints = 0;
        pendingSeconds -= 1.0;
      }
      ++pendingPoints;
    }

    public void report(PrintStream out) {
      SummaryStatistics stats = new SummaryStatistics();
      for (int points : pointsPerSecond)
        stats.addValue(points);
      out.printf("Scan-points/second: %.2f; var: %.2f (%.2f%%)\n", stats.getMean(),
          stats.getVariance(), 100 * stats.getVariance() / stats.getMean());
      PoissonDistribution dist = new PoissonDistribution(stats.getMean());
      out.println("Histogram, actual vs predicted:");
      for (Multiset.Entry<Integer> e : TreeMultiset.create(pointsPerSecond).entrySet())
        out.printf("  %2d:%6d  |%9.2f\n", e.getElement(), e.getCount(),
            dist.probability(e.getElement()) * stats.getN());
    }
  }
}
