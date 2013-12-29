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
package us.blanshard.sudoku.insight;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

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

  public LockedSet(NumSet nums, UnitSubset locs, boolean isNaked) {
    super(Type.LOCKED_SET);
    this.nums = nums;
    this.locs = locs;
    this.isNaked = isNaked;
    Unit overlap = Analyzer.findOverlappingUnit(locs);
    this.extraElims = overlap == null ? null : overlap.subtract(locs.unit);
  }

  @Override public Collection<Assignment> getEliminations() {
    Collection<Assignment> answer = eliminations;
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
    return !isNakedSet();
  }

  public NumSet getNumerals() {
    return nums;
  }

  public UnitSubset getLocations() {
    return locs;
  }

  @Override public void apply(GridMarks.Builder builder) {
    for (int i = 0, count = getEliminations().size(); i < count; ++i)
      builder.eliminate(eliminations.get(i));
  }

  @Override public boolean isImpliedBy(GridMarks gridMarks) {
    if (isNakedSet()) {
      for (Location loc : locs)
        if (!gridMarks.marks.get(loc).isSubsetOf(nums)) return false;
    } else {
      for (Numeral num : nums)
        if (!gridMarks.marks.get(UnitNumeral.of(locs.unit, num)).isSubsetOf(locs)) return false;
    }
    return true;
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    if (isNakedSet()) {
      return locs.contains(elimination.location) && !nums.contains(elimination.numeral);
    } else {
      return !locs.contains(elimination.location) && nums.contains(elimination.numeral);
    }
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    LockedSet that = (LockedSet) o;
    return this.nums.equals(that.nums)
        && this.locs.equals(that.locs);
  }

  @Override public int hashCode() {
    return (nums.bits << 9) | locs.bits;
  }

  @Override public String toString() {
    return nums + " \u2194 " + locs;
  }

  @Override public void addScanTargets(Collection<Location> locs, Collection<UnitNumeral> unitNums) {
    if (isNakedSet())
      for (int i = 0; i < this.locs.size(); ++i)
        locs.add(this.locs.get(i));
    else for (int i = 0; i < this.nums.size(); ++i)
      unitNums.add(UnitNumeral.of(this.locs.unit, this.nums.get(i)));
  }

  @Override public int getScanTargetCount() {
    return locs.size();
  }
}
