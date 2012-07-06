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

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Marks;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.UnitSubset;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * Describes a locked set: a set of numerals and a set of locations such that
 * each of the numerals must inhabit one of the locations.
 *
 * @author Luke Blanshard
 */
@Immutable
public class LockedSet extends Insight.Atom {
  private final NumSet nums;
  private final UnitSubset locs;
  private volatile Collection<Assignment> eliminations;

  public LockedSet(Grid grid, NumSet nums, UnitSubset locs, boolean isNaked) {
    super(isNaked ? Pattern.nakedSet(grid, nums, locs) : Pattern.hiddenSet(grid, nums, locs));
    this.nums = nums;
    this.locs = locs;
  }

  @Override public Collection<Assignment> getEliminations() {
    Collection<Assignment> answer = eliminations;
    if (answer == null) {
      synchronized (this) {
        if ((answer = eliminations) == null) {
          ImmutableList.Builder<Assignment> builder = ImmutableList.builder();
          NumSet nums = isNakedSet() ? this.nums : this.nums.not();
          UnitSubset locs = isNakedSet() ? this.locs.not() : this.locs;
          for (Numeral num : nums)
            for (Location loc : locs)
              builder.add(Assignment.of(loc, num));
          eliminations = answer = builder.build();
        }
      }
    }
    return answer;
  }

  public boolean isNakedSet() {
    return getPattern().getType() == Pattern.Type.NAKED_SET;
  }

  public boolean isHiddenSet() {
    return !isNakedSet();
  }

  public NumSet getNumerals() {
    return nums;
  }

  public UnitSubset getLocations() {
    return locs;
  }

  @Override public boolean apply(Grid.Builder gridBuilder, Marks.Builder marksBuilder) {
    boolean ok = true;
    for (Assignment assignment : getEliminations())
      ok &= marksBuilder.eliminate(assignment);
    return ok;
  }

  @Override public boolean isImpliedBy(Grid grid, Marks marks) {
    if (isNakedSet()) {
      for (Location loc : locs)
        if (!marks.get(loc).isSubsetOf(nums)) return false;
    } else {
      for (Numeral num : nums)
        if (!marks.get(locs.unit, num).isSubsetOf(locs)) return false;
    }
    return true;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    LockedSet that = (LockedSet) o;
    return this.pattern.equals(that.pattern)
        && this.nums.equals(that.nums)
        && this.locs.equals(that.locs);
  }

  @Override public int hashCode() {
    return Objects.hashCode(pattern, nums, locs);
  }

  @Override public String toString() {
    return nums.toString() + " x " + locs;
  }
}
