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

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Analyzes a Sudoku game state, producing a series of insights about it.
 *
 * @author Luke Blanshard
 */
public class Analyzer {

  public static class StopException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

//  /**
//   * Controls the behavior of {@link #analyze}.
//   */
  @Immutable
  public static final class Options {
    /**
     * When true, uses assignment insights as antecedents when constructing
     * implications.  When false, leaves assignments out and only follows
     * elimination insights.
     */
    public final boolean followAssignments;
    /**
     * When true, provides all elimination insights to the callback, even
     * those that only appear during the search for implications.
     */
    public final boolean returnAllEliminations;

    public Options(boolean followAssignments, boolean returnAllEliminations) {
      this.followAssignments = followAssignments;
      this.returnAllEliminations = returnAllEliminations;
    }
  }

//  /**
//   * Called by {@link #analyze} for each insight found.  The callback may cut the
//   * work short by throwing a "stop" exception.
//   */
  public interface Callback {
    void take(Insight insight) throws StopException;
  }

  /**
   * Analyzes the given grid, providing found insights to the callback, and
   * returning true if it finished without being interrupted. This may be a
   * time-consuming operation; if run as a background thread it can be stopped
   * early by interrupting the thread.
   */
//  public static boolean analyze(Marks marks, Callback callback) {
//    return analyze(marks, callback, DEFAULT_OPTIONS);
//  }

//  private static final Options DEFAULT_OPTIONS = new Options(true, false);

  /**
   * Like the standard version of analyze, but lets you control the detailed
   * behavior of the analyzer.
   */
//  public static boolean analyze(Marks marks, Callback callback, Options options) {
//    boolean complete = false;
//
//    try {
//      findInsights(marks, callback, null, options);
//      complete = true;
//
//    } catch (InterruptedException | StopException e) {
//      // A normal event
//    }
//    return complete;
//  }

//  private static void findInsights(
//      Marks marks, Callback callback,
//      @Nullable Set<Insight> index, Options options) throws InterruptedException {
//
//    index = index == null ? Sets.<Insight>newHashSet() : Sets.newHashSet(index);
//    Collector collector;
//
//    if (marks.hasErrors()) {
//      collector = new Collector(callback, index, false);
//      findErrors(marks, collector);
//      checkInterruption();
//    }
//
//    collector = new Collector(callback, index, options.followAssignments);
//
//    findSingletonLocations(marks, collector);
//    checkInterruption();
//    findSingletonNumerals(marks, collector);
//    checkInterruption();
//
//    if (!options.followAssignments)
//      collector = new Collector(callback, index, true);
//
//    findOverlaps(marks, collector);
//    checkInterruption();
//    findSets(marks, collector);
//    checkInterruption();
//
//    if (!collector.list.isEmpty()) {
//      findImplications(marks, collector, callback, options);
//    }
//  }

//  private static void findImplications(Marks marks, Collector antecedents,
//      Callback callback, Options options) throws InterruptedException {
//
//    Collector collector = new Collector(null, antecedents.index, true);
//    findInsights(marks.toBuilder().apply(antecedents.list).build(), collector,
//        antecedents.index, options);
//
//    if (!collector.list.isEmpty()) {
//      List<Insight> antecedentsList = ImmutableList.copyOf(antecedents.list);
//      for (Insight insight : collector.list)
//        if (insight.isElimination())
//          callback.take(insight);
//        else
//          callback.take(new Implication(antecedentsList, insight));
//    }
//  }

  private static class Collector implements Callback {
    @Nullable final Callback delegate;
    final Set<Insight> index;
    @Nullable final List<Insight> list;

    Collector(@Nullable Callback delegate, Set<Insight> index, boolean makeList) {
      this.delegate = delegate;
      this.index = index;
      this.list = makeList ? Lists.<Insight>newArrayList() : null;
    }

    @Override public void take(Insight insight) {
      if (index.add(insight)) {
        if (list != null) list.add(insight);
        if (delegate != null) delegate.take(insight);
      }
    }
  }

  /**
   * The bit patterns for unit subsets of overlapping units with 2 or 3
   * locations, for rows or columns overlapping with blocks, and for blocks
   * overlapping with rows.
   */
  private static final int[] OVERLAP_BITS = {
      0b000_000_111, 0b000_000_110, 0b000_000_101, 0b000_000_011,
      0b000_111_000, 0b000_110_000, 0b000_101_000, 0b000_011_000,
      0b111_000_000, 0b110_000_000, 0b101_000_000, 0b011_000_000};
  /**
   * Like {@code OVERLAP_BITS} but for blocks overlapping with columns.
   */
  private static final int[] OVERLAP_BITS_2 = {
      0b001_001_001, 0b001_001_000, 0b001_000_001, 0b000_001_001,
      0b010_010_010, 0b010_010_000, 0b010_000_010, 0b000_010_010,
      0b100_100_100, 0b100_100_000, 0b100_000_100, 0b000_100_100};
  static {
    Arrays.sort(OVERLAP_BITS);
    Arrays.sort(OVERLAP_BITS_2);
  }

  private static final int MAX_SET_SIZE = 4;

  private static void firstSubset(int size, int[] indices) {
    for (int i = 0; i < size; ++i)
      indices[i] = i;
  }

  private static boolean nextSubset(int size, int[] indices, int count) {
    for (int i = size; i-- > 0; --count) {
      if (++indices[i] < count) {
        while (++i < size)
          indices[i] = 1 + indices[i - 1];
        return true;
      }
    }
    return false;
  }

  /** Checks for the current thread being interrupted, without clearing the bit. */
  public static void checkInterruption() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
  }

  public static void findOverlapsAndSets(Marks marks, Callback callback) {
    findOverlaps(marks, callback);
    findSets(marks, callback);
  }

  public static void findOverlaps(Marks marks, Callback callback) {
    for (Numeral num : Numeral.all()) {
      findOverlaps(marks, callback, num, Block.all(), Unit.Type.ROW, OVERLAP_BITS);
      findOverlaps(marks, callback, num, Block.all(), Unit.Type.COLUMN, OVERLAP_BITS_2);
      findOverlaps(marks, callback, num, Row.all(), Unit.Type.BLOCK, OVERLAP_BITS);
      findOverlaps(marks, callback, num, Column.all(), Unit.Type.BLOCK, OVERLAP_BITS);
    }
  }

  /**
   * Looks to see whether the given unit subset belongs to a single different
   * unit as well, and returns that unit if so.
   */
  @Nullable public static Unit findOverlappingUnit(UnitSubset set) {
    if (set.size() < 2 || set.size() > 3) return null;
    if (set.unit.type == Unit.Type.BLOCK) {
      int index = Arrays.binarySearch(OVERLAP_BITS, set.bits);
      if (index >= 0)
        return set.get(0).row;
      index = Arrays.binarySearch(OVERLAP_BITS_2, set.bits);
      if (index >= 0)
        return set.get(0).column;
    } else {
      int index = Arrays.binarySearch(OVERLAP_BITS, set.bits);
      if (index >= 0)
        return set.get(0).block;
    }
    return null;
  }

  private static void findOverlaps(Marks marks, Callback callback, Numeral num,
      List<? extends Unit> units, Unit.Type overlappingType, int[] bitsArray) {
    for (int i = 0; i < units.size(); ++i) {
      Unit unit = units.get(i);
      UnitNumeral unitNum = UnitNumeral.of(unit, num);
      int bits = marks.getBitsForPossibleLocations(unitNum);
      int index = Arrays.binarySearch(bitsArray, bits);
      if (index >= 0 || NumSet.ofBits(bits).size() == 1) {
        UnitSubset set = UnitSubset.ofBits(unit, bits);
        Unit overlappingUnit = set.get(0).unit(overlappingType);
        UnitNumeral oun = UnitNumeral.of(overlappingUnit, num);
        UnitSubset overlappingSet = marks.getPossibleLocations(oun);
        if (overlappingSet.size() > set.size()) {
          // There's something to eliminate.
          if (set.size() > 1 ||
              forcedLocationCountsAsOverlap(marks, num, unit, set, overlappingUnit)) {
            callback.take(new Overlap(unit, num, overlappingSet.minus(set)));
          }
        }
      }
    }
  }

  private static boolean forcedLocationCountsAsOverlap(
      Marks marks, Numeral num, Unit unit, UnitSubset set, Unit overlappingUnit) {
    UnitSubset intersection = unit.intersect(overlappingUnit);
    UnitSubset unassigned = marks.getUnassignedLocations(unit);
    UnitSubset possibles = intersection.and(unassigned).minus(set);
    if (!possibles.isEmpty()) {
      // There is more than one unassigned location, so it might include an
      // overlap.  We count it as an overlap if removing the insights that
      // eliminate 2 or more of the overlapping locations still leaves all of
      // the remaining open locations eliminated.
      //
      // Note that arriving at this branch implies that there is exactly one
      // possible location for the current numeral within the current unit.
      // That is, one of the 3 locations in the intersection between the two
      // units is NOT currently eliminated.  Therefore, we can just examine the
      // insights that eliminate the other 2 locations (those left in the
      // "possibles" set), and in isolation.
      UnitSubset required = unassigned.minus(intersection);
      Set<Insight> insights = new HashSet<>();
      OUTER:
      for (int i = 0; i < possibles.size(); ++i) {
        insights.clear();
        insights.addAll(marks.getEliminationInsights(Assignment.of(possibles.get(i), num)));
        for (int j = 0; j < required.size(); ++j) {
          if (insights.containsAll(marks.getEliminationInsights(Assignment.of(required.get(j), num)))) {
            // Dropping all of these insights would mean this
            // required-to-be-eliminated location is no longer eliminated.  So
            // this iteration of i fails.  Go on to the next.
            continue OUTER;
          }
        }
        // This currently eliminated location in the intersection could have all
        // of its eliminating insights removed without restoring any of the
        // other unassigned locations in the unit.  So we've found a non-obvious
        // elimination.
        return true;
      }
    }
    return false;
  }

  public static void findSets(Marks marks, Callback callback) {
    int[] indices = new int[MAX_SET_SIZE];
    for (Unit unit : Unit.allUnits()) {
      for (int size = 2; size <= MAX_SET_SIZE; ++size) {
        findHiddenSets(marks, callback, unit, size, indices);
        findNakedSets(marks, callback, unit, size, indices);
      }
    }
  }

  private static void findNakedSets(Marks marks, Callback callback, Unit unit, int size, int[] indices) {
    int bitsToCheck = 0;
    for (int i = 0; i < Unit.UNIT_SIZE; ++i) {
      Location loc = unit.get(i);
      NumSet possible = marks.getPossibleNumerals(loc);
      int possibleSize = possible.size();
      if (possibleSize > 1 ||
          (possibleSize == 1 && !marks.hasAssignment(UnitNumeral.of(unit, possible.get(0))))) {
        if (possibleSize <= size) {
          bitsToCheck |= loc.unitSubsets.get(unit.type).bits;
        }
      }
    }
    if (UnitSubset.bitsSize(bitsToCheck) >= size) {
      UnitSubset toCheck = UnitSubset.ofBits(unit, bitsToCheck);
      firstSubset(size, indices);
      do {
        int bits = 0;
        for (int i = 0; i < size; ++i) {
          Location loc = toCheck.get(indices[i]);
          bits |= marks.getBitsForPossibleNumerals(loc);
        }
        NumSet nums = NumSet.ofBits(bits);
        if (nums.size() == size) {
          UnitSubset locs = UnitSubset.of(unit);
          for (int i = 0; i < size; ++i)
            locs = locs.with(toCheck.get(indices[i]));
          Unit overlap = findOverlappingUnit(locs);
          if (overlap != null && overlap.type == Unit.Type.BLOCK) {
            // Block and line naked sets have identical results.  Do not emit
            // both of them.
            continue;
          }
          ImmutableList<Assignment> eliminations = LockedSet.makeEliminations(
              nums, locs, /*isNaked = */true, overlap, marks);
          // Make sure there is work for the insight to do before emitting it.
          if (!eliminations.isEmpty()) {
            callback.take(new LockedSet(nums, locs, /*isNaked = */true, overlap, eliminations));
          }
        }
      } while (nextSubset(size, indices, toCheck.size()));
    }
  }

  private static void findHiddenSets(Marks marks, Callback callback, Unit unit, int size, int[] indices) {
    NumSet toCheck = NumSet.NONE;
    for (int i = 0; i < Numeral.COUNT; ++i) {
      Numeral num = Numeral.ofIndex(i);
      UnitSubset possible = marks.getPossibleLocations(UnitNumeral.of(unit, num));
      int possibleSize = possible.size();
      if (possibleSize > 1 || (possibleSize == 1 && !marks.hasAssignment(possible.get(0)))) {
        if (possibleSize <= size) {
          toCheck = toCheck.with(num);
        }
      }
    }
    if (toCheck.size() >= size) {
      firstSubset(size, indices);
      do {
        int bits = 0;
        for (int i = 0; i < size; ++i) {
          Numeral num = toCheck.get(indices[i]);
          bits |= marks.getBitsForPossibleLocations(UnitNumeral.of(unit, num));
        }
        if (UnitSubset.bitsSize(bits) == size) {
          UnitSubset locs = UnitSubset.ofBits(unit, bits);
          NumSet nums = NumSet.NONE;
          for (int i = 0; i < size; ++i)
            nums = nums.with(toCheck.get(indices[i]));
          Unit overlap = findOverlappingUnit(locs);
          ImmutableList<Assignment> eliminations = LockedSet.makeEliminations(
              nums, locs, /*isNaked = */false, overlap, marks);
          // Make sure there is work for the insight to do before emitting it.
          if (!eliminations.isEmpty()) {
            callback.take(new LockedSet(nums, locs, /*isNaked = */false, overlap, eliminations));
          }
        }
      } while (nextSubset(size, indices, toCheck.size()));
    }
  }

  public static void findErrors(Marks marks, Callback callback) {
    // First look for actual conflicting assignments.
    boolean conflictFound = false;
    for (Unit unit : Unit.allUnits()) {
      NumSet seen = NumSet.NONE;
      NumSet conflicting = NumSet.NONE;
      for (Location loc : unit) {
        Numeral num = marks.getAssignedNumeral(loc);
        if (num != null) {
          if (seen.contains(num)) conflicting = conflicting.with(num);
          seen = seen.with(num);
        }
      }
      for (Numeral num : conflicting) {
        UnitSubset locs = UnitSubset.ofBits(unit, 0);
        for (Location loc : unit)
          if (marks.getAssignedNumeral(loc) == num) locs = locs.with(loc);
        callback.take(new Conflict(num, locs));
        conflictFound = true;
      }
    }

    // Then look for numerals that have no possible assignments left in each
    // unit.
    for (Unit unit : Unit.allUnits()) {
      for (Numeral num : Numeral.all()) {
        UnitNumeral unitNum = UnitNumeral.of(unit, num);
        if (marks.getSizeOfPossibleLocations(unitNum) == 0 &&
            // Skip if the numeral is already assigned, if there was a conflict.
            (!conflictFound || !marks.hasAssignment(unitNum))) {
          callback.take(new BarredNum(unitNum));
        }
      }
    }

    // Finally, look for locations that have no possible assignments left.
    for (Location loc : Location.all()) {
      if (marks.getPossibleNumerals(loc).isEmpty() &&
          // Skip if the location is already assigned, if there was a conflict.
          (!conflictFound || !marks.hasAssignment(loc))) {
        callback.take(new BarredLoc(loc));
      }
    }
  }

  public static void findSingletonLocations(Marks marks, Callback callback) {
    for (int i = 0; i < UnitNumeral.COUNT; ++i) {
      UnitNumeral un = UnitNumeral.of(i);
      Location loc = marks.getOnlyPossibleLocation(un);
      if (loc != null && !marks.hasAssignment(loc))
        callback.take(new ForcedLoc(un.unit, un.numeral, loc));
    }
  }

  public static void findSingletonNumerals(Marks marks, Callback callback) {
    for (int i = 0; i < Location.COUNT; ++i) {
      Location loc = Location.of(i);
      if (!marks.hasAssignment(loc)) {
        NumSet set = marks.getPossibleNumerals(loc);
        if (set.size() == 1)
          callback.take(new ForcedNum(loc, set.get(0)));
      }
    }
  }
}
