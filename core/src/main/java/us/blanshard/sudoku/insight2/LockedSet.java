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
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
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
  @Nullable private final Unit overlap;
  private final boolean isNaked;
  private volatile List<Assignment> eliminations;

  public LockedSet(NumSet nums, UnitSubset locs, boolean isNaked) {
    this(nums, locs, isNaked, Analyzer.findOverlappingUnit(locs), null);
  }

  LockedSet(NumSet nums, UnitSubset locs, boolean isNaked, @Nullable Unit overlap,
            @Nullable ImmutableList<Assignment> eliminations) {
    super(Type.LOCKED_SET, Objects.hashCode(LockedSet.class, nums, locs, isNaked));
    this.nums = nums;
    this.locs = locs;
    this.isNaked = isNaked;
    this.overlap = overlap;
    this.eliminations = eliminations;
  }

  /**
   * Constructs a list of those eliminations implied by a locked set that have
   * not already been eliminated by an assignment in the given Marks (if one is
   * supplied).
   */
  public static ImmutableList<Assignment> makeEliminations(
      NumSet nums, UnitSubset locs, boolean isNaked, @Nullable Unit overlap, @Nullable Marks marks) {
    ImmutableList.Builder<Assignment> builder = ImmutableList.builder();
    if (!isNaked) nums = nums.not();
    if (isNaked) locs = locs.not();
    for (int i = 0; i < nums.size(); ++i)
      for (int j = 0; j < locs.size(); ++j)
        addIfPossibleAssignment(builder, locs.get(j), nums.get(i), marks);
    if (overlap != null) {
      if (!isNaked) nums = nums.not();
      UnitSubset extraLocs = overlap.subtract(locs.unit);
      for (int i = 0; i < nums.size(); ++i)
        for (int j = 0; j < extraLocs.size(); ++j)
          addIfPossibleAssignment(builder, extraLocs.get(j), nums.get(i), marks);
    }
    return builder.build();
  }

  private static void addIfPossibleAssignment(
      ImmutableList.Builder<Assignment> builder, Location loc, Numeral num, @Nullable Marks marks) {
    if (marks == null || marks.isPossibleAssignment(loc, num)
        || !marks.isEliminatedByAssignment(loc, num)) {
      builder.add(Assignment.of(loc, num));
    }
  }

  @Override public List<Assignment> getEliminations() {
    List<Assignment> answer = eliminations;
    if (answer == null) {
      synchronized (this) {
        if ((answer = eliminations) == null) {
          answer = eliminations = makeEliminations(nums, locs, isNaked, overlap, null);
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
    return overlap;
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
    return nums + " \u2194 " + locs + (isNaked ? "n" : "h");
  }
}
