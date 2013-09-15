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
import us.blanshard.sudoku.stats.Pattern.UnitCategory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
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
      if (++count > 2) return false;
      for (Pattern a : p.getAntecedents()) {
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

  static Predicate<List<Pattern>> any(final Predicate<Pattern> pred) {
    return new Predicate<List<Pattern>>() {
      @Override public boolean apply(List<Pattern> list) {
        return Iterables.any(list, pred);
      }
    };
  }

  static Predicate<List<Pattern>> all(final Predicate<Pattern> pred) {
    return new Predicate<List<Pattern>>() {
      @Override public boolean apply(List<Pattern> list) {
        return Iterables.all(list, pred);
      }
    };
  }

  static Predicate<List<Pattern>> none(Predicate<Pattern> pred) {
    return all(Predicates.not(pred));
  }

  static Predicate<List<Pattern>> notAll(Predicate<Pattern> pred) {
    return Predicates.not(all(pred));
  }

  static final Predicate<List<Pattern>> matchAnything = Predicates.alwaysTrue();

  static final Predicate<Pattern> matchFlsOnly = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      return p.getType() == Pattern.Type.FORCED_LOCATION;
    }
  };

  static final Predicate<Pattern> matchFlbsOnly = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      if (p.getType() != Pattern.Type.FORCED_LOCATION) return false;
      Pattern.ForcedLoc fl = (Pattern.ForcedLoc) p;
      return fl.getCategory() == UnitCategory.BLOCK;
    }
  };

  static final Predicate<Pattern> matchFllsOnly = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      if (p.getType() != Pattern.Type.FORCED_LOCATION) return false;
      Pattern.ForcedLoc fl = (Pattern.ForcedLoc) p;
      return fl.getCategory() == UnitCategory.LINE;
    }
  };

  static Predicate<Pattern> implied(final Predicate<Pattern> pred) {
    return new Predicate<Pattern>() {
      @Override public boolean apply(Pattern p) {
        return pred.apply(p.getNub());
      }
    };
  }

  static Predicate<Pattern> simplyImplied(final Predicate<Pattern> pred) {
    return new Predicate<Pattern>() {
      @Override public boolean apply(Pattern p) {
        if (pred.apply(p)) return true;
        if (!pred.apply(p.getNub())) return false;
        if (p.getType() != Pattern.Type.IMPLICATION) return false;
        return hasSimpleAntecedents((Pattern.Implication) p);
      }
    };
  }

  static final Predicate<Pattern> matchAllImpliedFls = implied(matchFlsOnly);
  static final Predicate<Pattern> matchSimplyImpliedFls = simplyImplied(matchFlsOnly);

  static final Predicate<Pattern> matchFlsAndFns = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      return p.isDirectAssignment();
    }
  };

  static final Predicate<Pattern> matchAllImpliedFlsAndFns = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      return p.isAssignment();
    }
  };

  static final Predicate<Pattern> matchSimplyImpliedFlsAndFns = new Predicate<Pattern>() {
    @Override public boolean apply(Pattern p) {
      if (p.isDirectAssignment()) return true;
      if (!p.isAssignment()) return false;
      if (p.getType() != Pattern.Type.IMPLICATION) return false;
      return hasSimpleAntecedents((Pattern.Implication) p);
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

  static final Predicate<Pattern> matchAllImpliedFlsAndEasyFns = implied(matchFlsAndEasyFns);
  static final Predicate<Pattern> matchSimplyImpliedFlsAndEasyFns = simplyImplied(matchFlsAndEasyFns);

  interface Consumer {
    void take(List<Pattern> found, List<List<Pattern>> missed, long ms, int openCount);
  }

  static class Reporter implements Consumer {
    private final List<Process> processes;

    public Reporter() {
      processes = ImmutableList.<Process>builder()
          .add(make("Forced locations", matchFlsOnly))
          .add(make("Forced locations everywhere", any(matchFlsOnly), any(matchFlsOnly)))
          .add(make("Forced locations mostly", any(matchFlsOnly), any(matchFlsOnly), 0.8))
          .add(make("Forced locations/block", matchFlbsOnly))
          .add(make("Forced locations/block everywhere", any(matchFlbsOnly), any(matchFlbsOnly)))
          .add(make("Forced locations/block mostly", any(matchFlbsOnly), any(matchFlbsOnly), 0.8))
          .add(make("Forced locations/line", matchFllsOnly))
          .add(make("Forced locations/line everywhere", any(matchFllsOnly), any(matchFllsOnly)))
          .add(make("Forced locations/line mostly", any(matchFllsOnly), any(matchFllsOnly), 0.8))
          .add(make("Forced locations/block w/no lines", Predicates.and(any(matchFlbsOnly), none(matchFllsOnly)), matchAnything))
          .add(make("Forced locations/block w/no indirect lines", Predicates.and(any(matchFlbsOnly), none(implied(matchFllsOnly))), matchAnything))
          .add(make("Forced locations/line w/no blocks", Predicates.and(any(matchFllsOnly), none(matchFlbsOnly)), matchAnything))
          .add(make("Forced locations/line w/no indirect blocks", Predicates.and(any(matchFllsOnly), none(implied(matchFlbsOnly))), matchAnything))
          .add(make("Forced locations/block w/no lines anywhere", Predicates.and(any(matchFlbsOnly), none(matchFllsOnly)), none(matchFllsOnly)))
          .add(make("Forced locations/line w/no blocks anywhere", Predicates.and(any(matchFllsOnly), none(matchFlbsOnly)), none(matchFlbsOnly)))
          .add(make("Implied forced locations", matchAllImpliedFls))
          .add(make("Implied forced locations/block w/no direct lines", Predicates.and(any(implied(matchFlbsOnly)), none(matchFllsOnly)), matchAnything))
          .add(make("Implied forced locations/block w/no indirect lines", Predicates.and(any(implied(matchFlbsOnly)), none(implied(matchFllsOnly))), matchAnything))
          .add(make("Simply-implied forced locations", matchSimplyImpliedFls))
          .add(make("Simply-implied forced locations/block w/no direct lines", Predicates.and(any(simplyImplied(matchFlbsOnly)), none(matchFllsOnly)), matchAnything))
          .add(make("Simply-implied forced locations/block w/no indirect lines", Predicates.and(any(simplyImplied(matchFlbsOnly)), none(implied(matchFllsOnly))), matchAnything))
          .add(make("Direct assignments", matchFlsAndFns))
          .add(make("Direct assignments everywhere", any(matchFlsAndFns), any(matchFlsAndFns)))
          .add(make("Direct assignments mostly", any(matchFlsAndFns), any(matchFlsAndFns), 0.8))
          .add(make("Implied assignments", matchAllImpliedFlsAndFns))
          .add(make("Simply-implied assignments", matchSimplyImpliedFls))
          .add(make("Simply-implied assignments everywhere", any(matchSimplyImpliedFls), any(matchSimplyImpliedFls)))
          .add(make("Simply-implied assignments mostly", any(matchSimplyImpliedFls), any(matchSimplyImpliedFls), 0.8))
          .add(make("Forced locations and easy forced numerals", matchFlsAndEasyFns))
          .add(make("Forced locations and easy forced numerals everywhere", any(matchFlsAndEasyFns), any(matchFlsAndEasyFns)))
          .add(make("Forced locations and easy forced numerals mostly", any(matchFlsAndEasyFns), any(matchFlsAndEasyFns), 0.8))
          .add(make("Implied forced locations and easy forced numerals", matchAllImpliedFlsAndEasyFns))
          .add(make("Simply-implied forced locations and easy forced numerals", matchSimplyImpliedFlsAndEasyFns))
          .add(make("Simply-implied forced locations and easy forced numerals everywhere", any(matchSimplyImpliedFlsAndEasyFns), any(matchSimplyImpliedFlsAndEasyFns)))
          .add(make("Simply-implied forced locations and easy forced numerals mostly", any(matchSimplyImpliedFlsAndEasyFns), any(matchSimplyImpliedFlsAndEasyFns), 0.8))
          .build();
    }

    private static Process make(String description, Predicate<Pattern> matches) {
      return make(description, any(matches), matchAnything);
    }

    private static Process make(String description, Predicate<List<Pattern>> foundMatches,
        Predicate<List<Pattern>> missedMatches) {
      return make(description, foundMatches, missedMatches, 1.0);
    }

    private static Process make(String description, Predicate<List<Pattern>> foundMatches,
        Predicate<List<Pattern>> missedMatches, double missedMatchesMinRatio) {
      return new Process(description, foundMatches, missedMatches, missedMatchesMinRatio);
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
    private final ProcessCounter counter = new ProcessCounter();
    private final String description;
    private final Predicate<List<Pattern>> foundMatches;
    private final Predicate<List<Pattern>> missedMatches;
    private final double missedMatchesMinRatio;
    private long movesIncluded;
    private long movesSkipped;

    public Process(String description, Predicate<List<Pattern>> foundMatches,
        Predicate<List<Pattern>> missedMatches, double missedMatchesMinRatio) {
      this.description = description;
      this.foundMatches = foundMatches;
      this.missedMatches = missedMatches;
      this.missedMatchesMinRatio = missedMatchesMinRatio;
    }

    @Override public void take(List<Pattern> found, List<List<Pattern>> missed, long ms,
        int openCount) {
      if (found.isEmpty()) return;

      boolean foundLocationMatches = foundMatches.apply(found);

      int assignments = found.size();
      int missedLocations = 0;
      int matchingMissedLocations = 0;
      for (List<Pattern> list : missed) {
        if (list.isEmpty() || !list.get(0).isAssignment()) continue;
        ++missedLocations;
        if (missedMatches.apply(list)) ++matchingMissedLocations;
        assignments += list.size();
      }

      if (!foundLocationMatches
          || (missedLocations > 0
              && matchingMissedLocations / (double) missedLocations < missedMatchesMinRatio)) {
        ++movesSkipped;
        return;
      }
      ++movesIncluded;
      double seconds = ms / 1000.0;
      double pointsScanned = openCount / (double) assignments;
      counter.count(seconds, pointsScanned);
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
    private double pendingPoints;

    public void count(double seconds, double pointsScanned) {
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
      out.printf("Seconds/scan-point: %.2f\n", 1.0 / stats.getMean());
      PoissonDistribution dist = new PoissonDistribution(stats.getMean());
      out.println("Histogram, actual vs predicted:");
      for (Multiset.Entry<Integer> e : TreeMultiset.create(pointsPerSecond).entrySet())
        out.printf("  %2d:%6d  |%9.2f\n", e.getElement(), e.getCount(),
            dist.probability(e.getElement()) * stats.getN());
    }
  }
}
