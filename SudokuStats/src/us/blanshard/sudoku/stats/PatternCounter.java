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

import us.blanshard.sudoku.stats.Pattern.Type;

import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
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
import java.util.Map;
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
      String found = iter.next();
      long ms = Long.parseLong(iter.next());
      Multiset<Pattern> missed = Pattern.multisetFromString(iter.next());
      if (found.isEmpty())
        noteFound(Pattern.NONE, ms, missed);
      else for (Pattern p : Pattern.listFromString(found))
        noteFound(p, ms, missed);
      for (Pattern p : missed)
        noteMissed(p, ms);
    }
    in.close();

    System.out.println(" ==== Found patterns times in seconds ====");
    printStats(found, 9);

//    System.out.println(" ==== Missed patterns times in seconds ====");
//    printStats(missed, 100);

    System.out.println();
    for (Pattern p : numAhead.rowKeySet()) {
      Map<Pattern, Count> aheadCounts = numAhead.row(p);
      Map<Pattern, Count> behindCounts = numAhead.column(p);
      Collection<Pattern> aheadOf = findClearLosers(aheadCounts, behindCounts);
      if (aheadOf != null)
        System.out.printf("%-24s\tahead of %d: %s\n", p, aheadOf.size(), aheadOf);
      Collection<Pattern> behind = findClearLosers(behindCounts, aheadCounts);
      if (behind != null)
        System.out.printf("%-24s\tbehind %d: %s\n", p, behind.size(), behind);
    }

    System.out.printf("Total found: %d\n", foundOnce.size() + found.size());
    System.out.printf("Total missed: %d\n", missedOnce.size() + missed.size());
  }

  private static Collection<Pattern> findClearLosers(Map<Pattern, Count> aheadCounts,
      Map<Pattern, Count> behindCounts) {
    Collection<Pattern> aheadOf = null;
    for (Map.Entry<Pattern, Count> e : aheadCounts.entrySet()) {
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

  private static final Map<Pattern, Double> foundOnce = Maps.newHashMap();
  private static final Map<Pattern, DescriptiveStatistics> found = Maps.newHashMap();
  private static final Map<Pattern, Double> missedOnce = Maps.newHashMap();
  private static final Map<Pattern, DescriptiveStatistics> missed = Maps.newHashMap();
  private static final Table<Pattern, Pattern, Count> numAhead = TreeBasedTable.create();

  static class Count {
    int num;
  }

  private static final EnumSet<Pattern.Type> COMPARE =
      EnumSet.of(Type.FORCED_LOCATION, Type.FORCED_NUMERAL, Type.NONE);

  /**
   * Notes that the given pattern (or NONE) was seen in the given number
   * of milliseconds.
   */
  private static void noteFound(Pattern p, long ms, Multiset<Pattern> missed) {
    notePattern(p, ms, foundOnce, found);
    if (!COMPARE.contains(p.getType())) return;
    if (p != Pattern.NONE)
      noteAhead(p, Pattern.NONE, 1);
    for (Multiset.Entry<Pattern> e : missed.entrySet()) {
      Pattern p2 = e.getElement();
      if (!COMPARE.contains(p2.getType())) continue;
      if (p2.equals(p)) continue;
      noteAhead(p, p2, e.getCount());
    }
  }

  private static void noteAhead(Pattern p, Pattern p2, int count) {
    Count c = numAhead.get(p, p2);
    if (c == null) numAhead.put(p, p2, (c = new Count()));
    c.num += count;
  }

  /**
   * Notes that the given pattern was overlooked for the given number
   * of milliseconds.
   */
  private static void noteMissed(Pattern p, long ms) {
    notePattern(p, ms, missedOnce, missed);
  }

  private static void notePattern(Pattern p, long ms, Map<Pattern, Double> first,
      Map<Pattern, DescriptiveStatistics> stats) {
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

  private static void printStats(Map<Pattern, DescriptiveStatistics> stats, int min) {
    SortedSet<Map.Entry<Pattern, DescriptiveStatistics>> sorted = Sets.newTreeSet(
        new Ordering<Map.Entry<Pattern, DescriptiveStatistics>>() {
          @Override public int compare(@Nullable Map.Entry<Pattern, DescriptiveStatistics> left,
              @Nullable Map.Entry<Pattern, DescriptiveStatistics> right) {
            return ComparisonChain.start()
                // Right comes first:
//                .compare(right.getValue().getN(), left.getValue().getN())
                .compare(left.getValue().getPercentile(0.5), right.getValue().getPercentile(0.5))
                .compare(left.getKey(), right.getKey())
                .result();
          }
        }
    );

    for (Map.Entry<Pattern, DescriptiveStatistics> e : stats.entrySet()) {
      if (e.getValue().getN() >= min)
        sorted.add(e);
    }

    for (Map.Entry<Pattern, DescriptiveStatistics> e : sorted) {
      DescriptiveStatistics s = e.getValue();
      System.out.format("count=%,-2d\tmedian=%3.1f\tmean=%3.1f\tstddev=%3.1f\tp=%s\n", s.getN(),
          s.getPercentile(0.5), s.getMean(), s.getStandardDeviation(), e.getKey());
    }
  }
}
