/*
Copyright 2016 Luke Blanshard

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
package us.blanshard.sudoku.insight2;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;

/**
 * Describes a locked set: a set of numerals and a set of locations such that
 * each of the numerals must inhabit one of the locations.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class LockedSet extends Insight {
  private final NumSet nums;
  private final UnitSubset locs;
  @Nullable private final UnitSubset extraElims;
  private final boolean isNaked;
  private volatile List<Assignment> eliminations;

  public static LockedSet newNaked(NumSet nums, UnitSubset locs) {
    Unit overlap = Analyzer.findOverlappingUnit(locs);
    if (overlap != null && overlap.type == Unit.Type.BLOCK) {
      // Naked sets with overlaps behave the same regardless of which unit is
      // considered "the" unit.  So we force "the" unit to be the block in this
      // case.
      UnitSubset blockLocs = overlap.intersect(locs);
      overlap = locs.unit;
      locs = blockLocs;
    }
    return new LockedSet(nums, locs, /* isNaked = */ true, overlap);
  }

  public static LockedSet newHidden(NumSet nums, UnitSubset locs) {
    // Hidden sets with overlaps behave differently when each unit is
    // considered "the" unit.  So we don't normalize the order.
    return new LockedSet(nums, locs, /* isNaked = */ false, Analyzer.findOverlappingUnit(locs));
  }

  private LockedSet(NumSet nums, UnitSubset locs, boolean isNaked, @Nullable Unit overlap) {
    super(Type.LOCKED_SET, Objects.hashCode(LockedSet.class, nums, locs, isNaked));
    this.nums = nums;
    this.locs = locs;
    this.isNaked = isNaked;
    this.extraElims = overlap == null ? null : overlap.subtract(locs.unit);
  }

  @Override public List<Assignment> getEliminations() {
    List<Assignment> answer = eliminations;
    if (answer == null) {
      synchronized (this) {
        if ((answer = eliminations) == null) {
          ImmutableList.Builder<Assignment> builder = ImmutableList.builder();
          NumSet nums = isNaked ? this.nums : this.nums.not();
          UnitSubset locs = isNaked ? this.locs.not() : this.locs;
          for (int i = 0; i < nums.size(); ++i)
            for (int j = 0; j < locs.size(); ++j)
              builder.add(Assignment.of(locs.get(j), nums.get(i)));
          if (extraElims != null)
            for (int i = 0; i < this.nums.size(); ++i)
              for (int j = 0; j < extraElims.size(); ++j)
                builder.add(Assignment.of(extraElims.get(j), this.nums.get(i)));
          answer = eliminations = builder.build();
        }
      }
    }
    return answer;
  }

  public boolean isNakedSet() {
    return isNaked;
  }

  public boolean isHiddenSet() {
    return !isNaked;
  }

  public NumSet getNumerals() {
    return nums;
  }

  public UnitSubset getLocations() {
    return locs;
  }

  @Nullable public Unit getOverlappingUnit() {
    return extraElims == null ? null : extraElims.unit;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    LockedSet that = (LockedSet) o;
    return this.nums.equals(that.nums)
        && this.locs.equals(that.locs)
        && this.isNaked == that.isNaked;
  }

  @Override public String toString() {
    return nums + " \u2194 " + locs;
  }
}
