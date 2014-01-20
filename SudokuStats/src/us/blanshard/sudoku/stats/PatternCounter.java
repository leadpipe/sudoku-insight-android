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

import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.annotation.Nullable;

/**
 * Counts up times and relationships associated with {@link Pattern}s by
 * {@link InsightMeasurer}.
 */
public class PatternCounter {
  public static void main(String[] args) throws IOException {
    BufferedReader in = new BufferedReader(new FileReader("measurer.txt"));
    Splitter splitter = Splitter.on('\t');
    for (String line; (line = in.readLine()) != null; ) {
      Iterator<String> iter = splitter.split(line).iterator();
      Pattern.Coll found = Pattern.collFromString(iter.next());
      long ms = Long.parseLong(iter.next());
      int openCount = Integer.parseInt(iter.next());
      int numAssignments = Integer.parseInt(iter.next());
      List<Pattern.Coll> missed = Pattern.collsFromString(iter.next());
      Sp sp = Sp.fromList(found.patterns, openCount, numAssignments);
      noteFound(sp, ms, missed, openCount, numAssignments);
      for (Pattern.Coll coll : missed)
        for (Pattern p : coll.patterns)
          noteMissed(Sp.fromPattern(p, openCount, numAssignments), ms);
    }
    in.close();

    System.out.println(" ==== Found patterns times in seconds ====");
    printStats(found, 9);
    System.out.flush();

//    System.out.println(" ==== Missed patterns times in seconds ====");
//    printStats(missed, 100);

    System.out.println();
    Set<Sp> all = Sets.newTreeSet(numAhead.rowKeySet());
    all.addAll(numAhead.columnKeySet());
    for (Sp p : all) {
      Map<Sp, Count> aheadCounts = numAhead.row(p);
      Map<Sp, Count> behindCounts = numAhead.column(p);
      Collection<Sp> aheadOf = findClearLosers(aheadCounts, behindCounts);
      if (aheadOf != null)
        System.out.printf("%-24s\tahead of %d: %s\n", p, aheadOf.size(), aheadOf);
      Collection<Sp> behind = findClearLosers(behindCounts, aheadCounts);
      if (behind != null)
        System.out.printf("%-24s\tbehind %d: %s\n", p, behind.size(), behind);
    }

    System.out.printf("Total found: %d\n", foundOnce.size() + found.size());
    System.out.printf("Total missed: %d\n", missedOnce.size() + missed.size());
  }

  private static Collection<Sp> findClearLosers(Map<Sp, Count> aheadCounts,
      Map<Sp, Count> behindCounts) {
    Collection<Sp> aheadOf = null;
    for (Map.Entry<Sp, Count> e : aheadCounts.entrySet()) {
      int aheadCount = e.getValue().num;
      Count behind = behindCounts.get(e.getKey());
      int behindCount = behind == null ? 0 : behind.num;
      if (aheadCount + behindCount < 10) continue;
      if (behindCount * 2 <= aheadCount) {
        if (aheadOf == null) aheadOf = Lists.newArrayList();
        aheadOf.add(e.getKey());
      }
    }
    return aheadOf;
  }

  private static final Map<Sp, Double> foundOnce = Maps.newHashMap();
  private static final Map<Sp, DescriptiveStatistics> found = Maps.newHashMap();
  private static final Map<Sp, Double> missedOnce = Maps.newHashMap();
  private static final Map<Sp, DescriptiveStatistics> missed = Maps.newHashMap();
  private static final Table<Sp, Sp, Count> numAhead = TreeBasedTable.create();

  static class Count {
    int num;
    final Sp p1, p2;
    Count(Sp p1, Sp p2) { this.p1 = p1; this.p2 = p2; }
    @Override public String toString() { return String.valueOf(num); }
  }

  private static final EnumSet<Sp.Type> COMPARE =
      EnumSet.of(Sp.Type.FORCED_LOCATION, Sp.Type.FORCED_NUMERAL,
                 Sp.Type.IMPLICATION2, Sp.Type.COMBINATION2, Sp.Type.NONE);

  /**
   * Notes that the given pattern (or NONE) was seen in the given number
   * of milliseconds.
   */
  private static void noteFound(Sp p, long ms, List<Pattern.Coll> missed, int openCount, int numAssignments) {
    noteSp(p, ms, foundOnce, found);
    if (!COMPARE.contains(p.getType())) return;
    if (p.getType() != Sp.Type.NONE)
      noteAhead(p, Sp.NONE, 1);
    for (Pattern.Coll coll : missed) {
      Sp p2 = Sp.fromList(coll.patterns, openCount, numAssignments);
      if (!COMPARE.contains(p2.getType())) continue;
      if (p2.equals(p)) continue;
      noteAhead(p, p2, 1);
    }
  }

  private static void noteAhead(Sp p, Sp p2, int count) {
    Count c = numAhead.get(p, p2);
    if (c == null) numAhead.put(p, p2, (c = new Count(p, p2)));
    c.num += count;
  }

  /**
   * Notes that the given pattern was overlooked for the given number
   * of milliseconds.
   */
  private static void noteMissed(Sp p, long ms) {
    noteSp(p, ms, missedOnce, missed);
  }

  private static void noteSp(Sp p, long ms, Map<Sp, Double> first,
      Map<Sp, DescriptiveStatistics> stats) {
    Double prevSec = first.remove(p);
    if (prevSec != null) {
      DescriptiveStatistics s = new DescriptiveStatistics();
      s.addValue(prevSec);
      stats.put(p, s);
    }
    double sec = ms / 100.0;
    DescriptiveStatistics s = stats.get(p);
    if (s == null)
      first.put(p, sec);
    else
      s.addValue(sec);
  }

  private static void printStats(Map<Sp, DescriptiveStatistics> stats, int min) {
    SortedSet<Map.Entry<Sp, DescriptiveStatistics>> sorted = Sets.newTreeSet(
        new Ordering<Map.Entry<Sp, DescriptiveStatistics>>() {
          @Override public int compare(@Nullable Map.Entry<Sp, DescriptiveStatistics> left,
              @Nullable Map.Entry<Sp, DescriptiveStatistics> right) {
            return ComparisonChain.start()
                // Right comes first:
//                .compare(right.getValue().getN(), left.getValue().getN())
//                .compare(left.getValue().getPercentile(0.5), right.getValue().getPercentile(0.5))
                .compare(left.getValue().getMean(), right.getValue().getMean())
                .compare(left.getKey(), right.getKey())
                .result();
          }
        }
    );

    for (Map.Entry<Sp, DescriptiveStatistics> e : stats.entrySet()) {
      if (e.getValue().getN() >= min)
        sorted.add(e);
    }

    for (Map.Entry<Sp, DescriptiveStatistics> e : sorted) {
      DescriptiveStatistics s = e.getValue();
      System.out.format("count=%,-2d\tmedian=%3.1f\tmean=%3.1f\tstddev=%3.1f\tp=%s\n", s.getN(),
          s.getPercentile(0.5), s.getMean(), s.getStandardDeviation(), e.getKey());
    }
  }
}
