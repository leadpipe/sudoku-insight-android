/*
Copyright 2012 Google Inc.

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
package us.blanshard.sudoku.insight;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Unit;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A collection of insights about a Sudoku board.  Not thread-safe.
 *
 * @author Luke Blanshard
 */
@NotThreadSafe
public class InsightSum implements Cloneable {

  private final LocSet errors;
  private final List<String> errorUnitNumerals;
  private final List<String> overlaps;
  private final ArrayListMultimap<Unit, LockedSet> sets;
  private final Grid.Builder assignments;
  private boolean complete;

  public InsightSum() {
    this(new LocSet(), Lists.<String>newArrayList(), Lists.<String>newArrayList(),
         ArrayListMultimap.<Unit, LockedSet>create(), Grid.BLANK);
  }

  public InsightSum(InsightSum that) {
    this(new LocSet(that.errors),
         Lists.newArrayList(that.errorUnitNumerals),
         Lists.newArrayList(that.overlaps),
         ArrayListMultimap.create(that.sets),
         that.assignments.build());
    this.complete = that.complete;
  }

  private InsightSum(
      LocSet errors, List<String> errorUnitNumerals, List<String> overlaps,
      ArrayListMultimap<Unit, LockedSet> sets, Grid assignments) {
    this.errors = errors;
    this.errorUnitNumerals = errorUnitNumerals;
    this.overlaps = overlaps;
    this.sets = sets;
    this.assignments = assignments.toBuilder();
  }

  public void append(Insight insight) {
    switch (insight.getType()) {
    case CONFLICT:
      errors.addAll(((Conflict) insight).getLocations());
      break;
    case BARRED_LOCATION:
      errors.add(((BarredLoc) insight).getLocation());
      break;
    case BARRED_NUMERAL: {
      BarredNum b = (BarredNum) insight;
      errorUnitNumerals.add(b.getUnit() + ":" + b.getNumeral());
      break;
    }
    case FORCED_LOCATION: {
      ForcedLoc f = (ForcedLoc) insight;
      assignments.put(f.getLocation(), f.getNumeral());
      break;
    }
    case FORCED_NUMERAL: {
      ForcedNum f = (ForcedNum) insight;
      assignments.put(f.getLocation(), f.getNumeral());
      break;
    }
    case OVERLAP: {
      Overlap o = (Overlap) insight;
      overlaps.add(o.toBasicString());
      break;
    }
    case LOCKED_SET: {
      LockedSet l = (LockedSet) insight;
      sets.put(l.getLocations().unit, l);
      break;
    }
    default:
      break;
    }
  }

  public void setComplete() {
    complete = true;
  }

  public boolean isComplete() {
    return complete;
  }

  @Override public InsightSum clone() {
    return new InsightSum(this);
  }

  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    if (errors.size() > 0 || errorUnitNumerals.size() > 0) {
      sb.append("Error");
    } else if (assignments.size() > 0 || overlaps.size() > 0 || sets.size() > 0) {
      appendCountLine(sb, sets.size(), "set");
      if (sets.size() > 1) appendCount(sb.append(" in "), sets.keySet().size(), "unit");
      appendCountLine(sb, overlaps.size(), "overlap");
      appendCountLine(sb, assignments.size(), "move");
      if (!complete) sb.append("...");
    } else if (!complete) {
      sb.append("Working...");
    } else {
      sb.append("No insights available");
    }
    return sb.toString();
  }

  private void appendCountLine(StringBuilder sb, int count, String singular) {
    if (count > 0) {
      if (sb.length() > 0) sb.append('\n');
      appendCount(sb, count, singular);
    }
  }

  private void appendCount(StringBuilder sb, int count, String singular) {
    sb.append(count).append(' ').append(singular);
    if (count > 1) sb.append('s');
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    if (errors.size() > 0)
      sb.append("Error locations: ").append(errors).append("\n\n");
    if (errorUnitNumerals.size() > 0)
      sb.append("Error unit/numerals: ").append(errorUnitNumerals).append("\n\n");
    if (sets.size() > 0)
      sb.append("Sets: ").append(sets).append("\n\n");
    if (overlaps.size() > 0)
      sb.append("Overlaps: ").append(overlaps).append("\n\n");
    if (assignments.size() > 0)
      sb.append("Moves: ").append(Maps.newLinkedHashMap(assignments.build())).append("\n\n");
    if (!complete)
      sb.append("Working...");
    return sb.toString();
  }
}
