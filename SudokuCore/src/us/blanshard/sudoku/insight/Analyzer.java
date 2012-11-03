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
import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Analyzes a Sudoku game state, producing a series of insights about it.
 *
 * @author Luke Blanshard
 */
public class Analyzer {

  public static class StopException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  /**
   * Called by {@link #analyze} for each insight found.  The callback may cut the
   * work short by throwing a "stop" exception.
   */
  public interface Callback {
    void take(Insight insight) throws StopException;
  }

  /**
   * Analyzes the given grid, providing found insights to the callback, and
   * returning true if it finished without being interrupted. This may be a
   * time-consuming operation; if run as a background thread it can be stopped
   * early by interrupting the thread.
   *
   * <p> Any {@link Implication}s returned will have elimination-only insights
   * as antecedents, and these may well include insights that have nothing to do
   * with the consequent. Call {@link #minimizeImplication} to squeeze out
   * irrelevant antecedents.
   */
  public static boolean analyze(GridMarks gridMarks, Callback callback) {
    boolean complete = false;

    try {
      findInsights(gridMarks, callback, null);
      complete = true;

    } catch (InterruptedException e) {
      // A normal event
    } catch (StopException e) {
      // Also a normal event
    }
    return complete;
  }

  /**
   * Slims down the given insight, by removing antecendents of implications that
   * are not required to imply the consequents.  May be stopped early by
   * interrupting the thread.  Returns the original insight if either the thread
   * was interrupted or there was no way to reduce it.
   */
  public static Insight minimize(GridMarks gridMarks, Insight insight) {
    try {
      switch (insight.type) {
        case IMPLICATION:
          return minimizeImplication(gridMarks, (Implication) insight);

        case DISPROVED_ASSIGNMENT:
          return minimizeDisproof(gridMarks, (DisprovedAssignment) insight);

        default:
          return insight;
      }

    } catch (InterruptedException e) {
      return insight;
    }
  }

  private static void findInsights(
      GridMarks gridMarks, Callback callback, @Nullable Set<Insight> index)
      throws InterruptedException {

    index = index == null ? Sets.<Insight>newHashSet() : Sets.newHashSet(index);
    Collector collector;

    if (gridMarks.hasErrors) {
      collector = new Collector(callback, index, false);
      findErrors(gridMarks, collector);
      checkInterruption();
    }

    collector = new Collector(callback, index, true);

    findSingletonLocations(gridMarks, collector);
    checkInterruption();
    findSingletonNumerals(gridMarks, collector);
    checkInterruption();

    findOverlaps(gridMarks, collector);
    checkInterruption();
    findSets(gridMarks, collector);
    checkInterruption();

    if (!collector.list.isEmpty()) {
      findImplications(gridMarks, collector, callback);
    }
  }

  private static void findImplications(GridMarks gridMarks, Collector antecedents, Callback callback) throws InterruptedException {

    Collector collector = new Collector(null, antecedents.index, true);
    findInsights(gridMarks.toBuilder().apply(antecedents.list).build(), collector, antecedents.index);

    for (Insight insight : collector.list)
      callback.take(new Implication(antecedents.list, insight));
  }

  private static class Collector implements Callback {
    @Nullable final Callback delegate;
    final Set<Insight> index;
    @Nullable final List<Insight> list;

    Collector(@Nullable Callback delegate, @Nullable Set<Insight> index, boolean makeList) {
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

  private static Insight minimizeImplication(GridMarks gridMarks, Implication implication)
      throws InterruptedException {
    Insight consequent = implication.getConsequent();
    ImmutableList<Insight> allAntecedents = ImmutableList.copyOf(implication.getAntecedents());
    ArrayDeque<Insight> requiredAntecedents = Queues.newArrayDeque();

    if (consequent instanceof Implication) {
      consequent = minimizeImplication(
          gridMarks.toBuilder().apply(implication.getAntecedents()).build(),
          (Implication) consequent);
    }

    for (int index = allAntecedents.size() - 1; index >= 0; --index) {
      checkInterruption();
      Insight antecedent = allAntecedents.get(index);
      if (mayBeAntecedentTo(antecedent, consequent)) {
        GridMarks withoutThisOne = gridMarks.toBuilder()
            .apply(allAntecedents.subList(0, index))
            .apply(requiredAntecedents)
            .build();
        if (!consequent.isImpliedBy(withoutThisOne))
          requiredAntecedents.addFirst(antecedent);
      }
    }

    if (requiredAntecedents.isEmpty())
      return consequent;
    if (requiredAntecedents.equals(allAntecedents))
      return implication;
    return new Implication(requiredAntecedents, consequent);
  }

  private static boolean mayBeAntecedentTo(Insight antecedent, Insight consequent) {
    if (antecedent.isAssignment()) return true;
    for (Assignment assignment : antecedent.getEliminations())
      if (consequent.mightBeRevealedByElimination(assignment))
        return true;
    return false;
  }

  private static Insight minimizeDisproof(GridMarks gridMarks, DisprovedAssignment disproof)
      throws InterruptedException {
    Insight resultingError = disproof.getResultingError();
    GridMarks postAssignment = gridMarks.toBuilder()
        .eliminate(disproof.getDisprovedAssignment()).build();
    Insight minimizedError = minimize(postAssignment, resultingError);
    return minimizedError == resultingError ? disproof
        : new DisprovedAssignment(disproof.getDisprovedAssignment(), minimizedError);
  }

  private static class SetState {
    private final Map<Unit, NumSet> nums = Maps.newHashMap();
    private final Map<Unit, UnitSubset> locs = Maps.newHashMap();

    NumSet getNums(Unit unit) {
      NumSet set = nums.get(unit);
      return set == null ? NumSet.NONE : set;
    }

    UnitSubset getLocs(Unit unit) {
      UnitSubset set = locs.get(unit);
      return set == null ? UnitSubset.of(unit) : set;
    }

    void add(NumSet nums, UnitSubset locs) {
      this.nums.put(locs.unit, nums.or(getNums(locs.unit)));
      this.locs.put(locs.unit, locs.or(getLocs(locs.unit)));
    }
  }

  /**
   * The bit patterns for unit subsets of overlapping units with 2 or 3
   * locations, for rows or columns overlapping with blocks, and for blocks
   * overlapping with rows.
   */
  private static final int[] OVERLAP_BITS = {
    0007, 0006, 0005, 0003, 0070, 0060, 0050, 0030, 0700, 0600, 0500, 0300};
  /**
   * Like {@code OVERLAP_BITS} but for blocks overlapping with columns.
   */
  private static final int[] OVERLAP_BITS_2 = {
    0111, 0110, 0101, 0011, 0222, 0220, 0202, 0022, 0444, 0440, 0404, 0044};
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

  public static void findOverlapsAndSets(GridMarks gridMarks, Callback callback) {
    findOverlaps(gridMarks, callback);
    findSets(gridMarks, callback);
  }

  public static void findOverlaps(GridMarks gridMarks, Callback callback) {
    for (Numeral num : Numeral.ALL) {
      findOverlaps(gridMarks, callback, num, Block.ALL, Unit.Type.ROW, OVERLAP_BITS);
      findOverlaps(gridMarks, callback, num, Block.ALL, Unit.Type.COLUMN, OVERLAP_BITS_2);
      findOverlaps(gridMarks, callback, num, Row.ALL, Unit.Type.BLOCK, OVERLAP_BITS);
      findOverlaps(gridMarks, callback, num, Column.ALL, Unit.Type.BLOCK, OVERLAP_BITS);
    }
  }

  private static void findOverlaps(GridMarks gridMarks, Callback callback, Numeral num,
      List<? extends Unit> units, Unit.Type overlappingType, int[] bits) {
    for (Unit unit : units) {
      int index = Arrays.binarySearch(bits, gridMarks.marks.getBits(unit, num));
      if (index >= 0) {
        UnitSubset set = UnitSubset.ofBits(unit, bits[index]);
        Unit overlappingUnit = set.get(0).unit(overlappingType);
        UnitSubset overlappingSet = gridMarks.marks.get(overlappingUnit, num);
        if (overlappingSet.size() > set.size()) {
          // There's something to eliminate.
          callback.take(new Overlap(unit, num, overlappingSet.minus(set)));
        }
      }
    }
  }

  public static void findSets(GridMarks gridMarks, Callback callback) {
    SetState setState = new SetState();
    int[] indices = new int[MAX_SET_SIZE];
    for (int size = 2; size <= MAX_SET_SIZE; ++size) {
      for (Unit unit : Unit.allUnits()) {
        // Hidden sets are typically easier to see than naked ones.
        findHiddenSets(gridMarks, callback, setState, unit, size, indices);
        findNakedSets(gridMarks, callback, setState, unit, size, indices);
      }
    }
  }

  private static void findNakedSets(GridMarks gridMarks, Callback callback,
      SetState setState, Unit unit, int size, int[] indices) {
    UnitSubset inSets = setState.getLocs(unit);
    UnitSubset toCheck = UnitSubset.of(unit);
    int unsetCount = 0;
    for (Location loc : unit) {
      NumSet possible = gridMarks.marks.get(loc);
      if (possible.size() > 1) {
        ++unsetCount;
        if (possible.size() <= size && !inSets.contains(loc)) {
          toCheck = toCheck.with(loc);
        }
      }
    }
    if (toCheck.size() >= size && unsetCount > size) {
      firstSubset(size, indices);
      do {
        int bits = 0;
        boolean alreadyUsed = false;
        for (int i = 0; i < size; ++i) {
          Location loc = toCheck.get(indices[i]);
          bits |= gridMarks.marks.getBits(loc);
          alreadyUsed |= inSets.contains(loc);
        }
        if (alreadyUsed) continue;
        NumSet nums = NumSet.ofBits(bits);
        if (nums.size() == size) {
          UnitSubset locs = UnitSubset.of(unit);
          for (int i = 0; i < size; ++i)
            locs = locs.with(toCheck.get(indices[i]));
          setState.add(nums, locs);
          callback.take(new LockedSet(nums, locs, true));
          inSets = inSets.or(locs);
        }
      } while (nextSubset(size, indices, toCheck.size()));
    }
  }

  private static void findHiddenSets(GridMarks gridMarks, Callback callback,
      SetState setState, Unit unit, int size, int[] indices) {
    NumSet inSets = setState.getNums(unit);
    NumSet toCheck = NumSet.NONE;
    int unsetCount = 0;
    for (Numeral num : Numeral.ALL) {
      UnitSubset possible = gridMarks.marks.get(unit, num);
      if (possible.size() > 1) {
        ++unsetCount;
        if (possible.size() <= size && !inSets.contains(num)) {
          toCheck = toCheck.with(num);
        }
      }
    }
    if (toCheck.size() >= size && unsetCount > size) {
      firstSubset(size, indices);
      do {
        int bits = 0;
        boolean alreadyUsed = false;
        for (int i = 0; i < size; ++i) {
          Numeral num = toCheck.get(indices[i]);
          bits |= gridMarks.marks.getBits(unit, num);
          alreadyUsed |= inSets.contains(num);
        }
        if (alreadyUsed) continue;
        UnitSubset locs = UnitSubset.ofBits(unit, bits);
        if (locs.size() == size) {
          NumSet nums = NumSet.NONE;
          for (int i = 0; i < size; ++i)
            nums = nums.with(toCheck.get(indices[i]));
          setState.add(nums, locs);
          callback.take(new LockedSet(nums, locs, false));
          inSets = inSets.or(nums);
        }
      } while (nextSubset(size, indices, toCheck.size()));
    }
  }

  public static void findErrors(GridMarks gridMarks, Callback callback) {
    // First look for actual conflicting assignments.
    for (Unit unit : Unit.allUnits()) {
      NumSet seen = NumSet.NONE;
      NumSet conflicting = NumSet.NONE;
      for (Location loc : unit) {
        Numeral num = gridMarks.grid.get(loc);
        if (num != null) {
          if (seen.contains(num)) conflicting = conflicting.with(num);
          seen = seen.with(num);
        }
      }
      for (Numeral num : conflicting) {
        UnitSubset locs = UnitSubset.ofBits(unit, 0);
        for (Location loc : unit)
          if (gridMarks.grid.get(loc) == num) locs = locs.with(loc);
        callback.take(new Conflict(gridMarks.grid, num, locs));
      }
    }

    // Then look for numerals that have no possible assignments left in each
    // unit.
    for (Unit unit : Unit.allUnits()) {
      for (Numeral num : Numeral.ALL) {
        UnitSubset set = gridMarks.marks.get(unit, num);
        if (set.isEmpty()) {
          callback.take(new BarredNum(unit, num));
        }
      }
    }

    // Finally, look for locations that have no possible assignments left.
    for (Location loc : Location.ALL) {
      NumSet set = gridMarks.marks.get(loc);
      if (set.isEmpty()) {
        callback.take(new BarredLoc(loc));
      }
    }
  }

  public static void findAssignments(GridMarks gridMarks, Callback callback) {
    findSingletonLocations(gridMarks, callback);
    findSingletonNumerals(gridMarks, callback);
  }

  public static void findSingletonLocations(GridMarks gridMarks, Callback callback) {
    for (Unit unit : Unit.allUnits())
      for (Numeral num : Numeral.ALL) {
        UnitSubset set = gridMarks.marks.get(unit, num);
        if (set.size() == 1) {
          Location loc = set.get(0);
          if (!gridMarks.grid.containsKey(loc))
            callback.take(new ForcedLoc(unit, num, loc));
        }
      }
  }

  public static void findSingletonNumerals(GridMarks gridMarks, Callback callback) {
    for (Location loc : Location.ALL)
      if (!gridMarks.grid.containsKey(loc)) {
        NumSet set = gridMarks.marks.get(loc);
        if (set.size() == 1)
          callback.take(new ForcedNum(loc, set.get(0)));
      }
  }
}
