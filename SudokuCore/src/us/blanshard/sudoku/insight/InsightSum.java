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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;

/**
 * A collection of insights about a Sudoku board.
 *
 * @author Luke Blanshard
 */
public class InsightSum implements Insight, Cloneable {

  private final LocSet errors;
  private final List<String> errorUnitNumerals;
  private final Grid.Builder assignments;
  private boolean complete;

  public InsightSum() {
    this(new LocSet(), Lists.<String>newArrayList(), Grid.BLANK);
  }

  public InsightSum(InsightSum that) {
    this(new LocSet(that.errors),
         Lists.newArrayList(that.errorUnitNumerals),
         that.assignments.build());
    this.complete = that.complete;
  }

  private InsightSum(LocSet errors, List<String> errorUnitNumerals, Grid assignments) {
    this.errors = errors;
    this.errorUnitNumerals = errorUnitNumerals;
    this.assignments = assignments.asBuilder();
  }

  public void append(Insight insight) {
    if (insight instanceof IllegalMove) {
      errors.addAll(((IllegalMove) insight).getConflictingLocations());
    } else if (insight instanceof BlockedLocation) {
      errors.add(((BlockedLocation) insight).getLocation());
    } else if (insight instanceof BlockedUnitNumeral) {
      BlockedUnitNumeral b = (BlockedUnitNumeral) insight;
      errorUnitNumerals.add(b.getUnit() + ":" + b.getNumeral());
    } else if (insight instanceof ForcedNumeral) {
      ForcedNumeral f = (ForcedNumeral) insight;
      assignments.put(f.getLocation(), f.getNumeral());
    } else if (insight instanceof ForcedLocation) {
      ForcedLocation f = (ForcedLocation) insight;
      assignments.put(f.getLocation(), f.getNumeral());
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
    int count;
    if (errors.size() > 0 || errorUnitNumerals.size() > 0) {
      sb.append("Error");
    } else if ((count = assignments.size()) > 0) {
      if (count == 1) sb.append("1 move");
      else sb.append(count).append(" moves");
    } else if (!complete) {
      sb.append("Working...");
    } else {
      sb.append("No insights available");
    }
    return sb.toString();
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    if (errors.size() > 0)
      sb.append("Error locations: ").append(errors).append("\n\n");
    if (errorUnitNumerals.size() > 0)
      sb.append("Error unit/numerals: ").append(errorUnitNumerals).append("\n\n");
    if (assignments.size() > 0)
      sb.append("Moves: ").append(Maps.newLinkedHashMap(assignments.build())).append("\n\n");
    if (!complete)
      sb.append("Working...");
    return sb.toString();
  }
}
