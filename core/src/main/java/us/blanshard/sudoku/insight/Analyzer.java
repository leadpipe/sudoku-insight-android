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
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

  /**
   * Controls the behavior of {@link #analyze}.
   */
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
   * <p> Any {@link Implication}s returned may include insights as
   * antecedents that have nothing to do with the consequent. Call
   * {@link #minimizeImplication} to squeeze out irrelevant antecedents.
   */
  public static boolean analyze(Marks marks, Callback callback) {
    return analyze(marks, callback, DEFAULT_OPTIONS);
  }

  private static final Options DEFAULT_OPTIONS = new Options(true, false);

  /**
   * Like the standard version of analyze, but lets you control the detailed
   * behavior of the analyzer.
   */
  public static boolean analyze(Marks marks, Callback callback, Options options) {
    boolean complete = false;

    try {
      findInsights(marks, callback, null, options);
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
  public static Insight minimize(Marks marks, Insight insight) {
    try {
      switch (insight.type) {
        case IMPLICATION:
          return minimizeImplication(marks, (Implication) insight);

        case DISPROVED_ASSIGNMENT:
          return minimizeDisproof(marks, (DisprovedAssignment) insight);

        default:
          return insight;
      }

    } catch (InterruptedException e) {
      return insight;
    }
  }

  private static void findInsights(
      Marks marks, Callback callback,
      @Nullable Set<Insight> index, Options options) throws InterruptedException {

    index = index == null ? Sets.<Insight>newHashSet() : Sets.newHashSet(index);
    Collector collector;

    if (marks.hasErrors()) {
      collector = new Collector(callback, index, false);
      findErrors(marks, collector);
      checkInterruption();
    }

    collector = new Collector(callback, index, options.followAssignments);

    findSingletonLocations(marks, collector);
    checkInterruption();
    findSingletonNumerals(marks, collector);
    checkInterruption();

    if (!options.followAssignments)
      collector = new Collector(callback, index, true);

    findOverlaps(marks, collector);
    checkInterruption();
    findSets(marks, collector);
    checkInterruption();

    if (!collector.list.isEmpty()) {
      findImplications(marks, collector, callback, options);
    }
  }

  private static void findImplications(Marks marks, Collector antecedents,
      Callback callback, Options options) throws InterruptedException {

    Collector collector = new Collector(null, antecedents.index, true);
    findInsights(marks.toBuilder().apply(antecedents.list).build(), collector,
        antecedents.index, options);

    if (!collector.list.isEmpty()) {
      List<Insight> antecedentsList = ImmutableList.copyOf(antecedents.list);
      for (Insight insight : collector.list)
        if (insight.isElimination())
          callback.take(insight);
        else
          callback.take(new Implication(antecedentsList, insight));
    }
  }

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

  private static Insight minimizeImplication(Marks marks, Implication implication)
      throws InterruptedException {
    Insight consequent = implication.getConsequent();
    ImmutableList<Insight> allAntecedents = ImmutableList.copyOf(implication.getAntecedents());
    ArrayDeque<Insight> requiredAntecedents = Queues.newArrayDeque();

    if (consequent instanceof Implication) {
      consequent = minimizeImplication(
          marks.toBuilder().apply(implication.getAntecedents()).build(),
          (Implication) consequent);
    }

    for (int index = allAntecedents.size() - 1; index >= 0; --index) {
      checkInterruption();
      Insight antecedent = allAntecedents.get(index);
      if (mayBeAntecedentTo(antecedent, consequent)) {
        Marks withoutThisOne = marks.toBuilder()
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
    List<Assignment> elims = antecedent.getEliminations();
    for (int i = 0, c = elims.size(); i < c; ++i)
      if (consequent.mightBeRevealedByElimination(elims.get(i)))
        return true;
    return false;
  }

  private static Insight minimizeDisproof(Marks marks, DisprovedAssignment disproof)
      throws InterruptedException {
    Insight resultingError = disproof.getResultingError();
    Marks postAssignment = marks.toBuilder()
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
      if (set == null) locs.put(unit, (set = UnitSubset.of(unit)));
      return set;
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
      int bits = marks.getBits(unitNum);
      int index = Arrays.binarySearch(bitsArray, bits);
      if (index >= 0 || NumSet.ofBits(bits).size() == 1) {
        UnitSubset set = UnitSubset.ofBits(unit, bits);
        Unit overlappingUnit = set.get(0).unit(overlappingType);
        UnitNumeral oun = UnitNumeral.of(overlappingUnit, num);
        UnitSubset overlappingSet = marks.getSet(oun);
        if (overlappingSet.size() > set.size()) {
          // There's something to eliminate.
          if (set.size() > 1 || getNumOpen(marks, unit, overlappingUnit) > 1) {
            // There is more than one unassigned location, ie it looks like an overlap.
            callback.take(new Overlap(unit, num, overlappingSet.minus(set)));
          }
        }
      }
    }
  }

  /** Returns the number of unassigned locations in the intersection of the two units. */
  private static int getNumOpen(Marks marks, Unit unit, Unit overlappingUnit) {
    UnitSubset set = unit.intersect(overlappingUnit);
    int count = 0;
    for (int i = 0; i < set.size(); ++i) {
      if (!marks.hasAssignment(set.get(i))) ++count;
    }
    return count;
  }

  public static void findSets(Marks marks, Callback callback) {
    SetState setState = new SetState();
    int[] indices = new int[MAX_SET_SIZE];
    for (int size = 2; size <= MAX_SET_SIZE; ++size) {
      for (Unit unit : Unit.allUnits()) {
        // We look for hidden sets at each size first, because they tend to be
        // easier to see.
        findHiddenSets(marks, callback, setState, unit, size, indices);
        findNakedSets(marks, callback, setState, unit, size, indices);
      }
    }
  }

  private static void findNakedSets(Marks marks, Callback callback,
      SetState setState, Unit unit, int size, int[] indices) {
    UnitSubset inSets = setState.getLocs(unit);
    int bitsToCheck = 0;
    int unsetCount = 0;
    for (int i = 0; i < unit.size(); ++i) {
      Location loc = unit.get(i);
      NumSet possible = marks.getSet(loc);
      int possibleSize = possible.size();
      if (possibleSize > 1 ||
          (possibleSize == 1 && !marks.hasAssignment(UnitNumeral.of(unit, possible.get(0))))) {
        ++unsetCount;
        if (possibleSize <= size && !inSets.contains(loc)) {
          bitsToCheck |= loc.unitSubsets.get(unit.type).bits;
        }
      }
    }
    if (UnitSubset.bitsSize(bitsToCheck) >= size && unsetCount > size + 1) {
      UnitSubset toCheck = UnitSubset.ofBits(unit, bitsToCheck);
      firstSubset(size, indices);
      do {
        int bits = 0;
        boolean alreadyUsed = false;
        for (int i = 0; i < size; ++i) {
          Location loc = toCheck.get(indices[i]);
          bits |= marks.getBits(loc);
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

  private static void findHiddenSets(Marks marks, Callback callback,
      SetState setState, Unit unit, int size, int[] indices) {
    NumSet inSets = setState.getNums(unit);
    NumSet toCheck = NumSet.NONE;
    int unsetCount = 0;
    for (int i = 0; i < Numeral.COUNT; ++i) {
      Numeral num = Numeral.ofIndex(i);
      UnitSubset possible = marks.getSet(UnitNumeral.of(unit, num));
      int possibleSize = possible.size();
      if (possibleSize > 1 || (possibleSize == 1 && !marks.hasAssignment(possible.get(0)))) {
        ++unsetCount;
        if (possibleSize <= size && !inSets.contains(num)) {
          toCheck = toCheck.with(num);
        }
      }
    }
    if (toCheck.size() >= size && unsetCount > size + 1) {
      firstSubset(size, indices);
      do {
        int bits = 0;
        boolean alreadyUsed = false;
        for (int i = 0; i < size; ++i) {
          Numeral num = toCheck.get(indices[i]);
          bits |= marks.getBits(UnitNumeral.of(unit, num));
          alreadyUsed |= inSets.contains(num);
        }
        if (alreadyUsed) continue;
        if (UnitSubset.bitsSize(bits) == size) {
          UnitSubset locs = UnitSubset.ofBits(unit, bits);
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

  public static void findErrors(Marks marks, Callback callback) {
    // First look for actual conflicting assignments.
    for (Unit unit : Unit.allUnits()) {
      NumSet seen = NumSet.NONE;
      NumSet conflicting = NumSet.NONE;
      for (Location loc : unit) {
        Numeral num = marks.get(loc);
        if (num != null) {
          if (seen.contains(num)) conflicting = conflicting.with(num);
          seen = seen.with(num);
        }
      }
      for (Numeral num : conflicting) {
        UnitSubset locs = UnitSubset.ofBits(unit, 0);
        for (Location loc : unit)
          if (marks.get(loc) == num) locs = locs.with(loc);
        callback.take(new Conflict(num, locs));
      }
    }

    // Then look for numerals that have no possible assignments left in each
    // unit.
    for (Unit unit : Unit.allUnits()) {
      for (Numeral num : Numeral.all()) {
        if (marks.getSetSize(UnitNumeral.of(unit, num)) == 0) {
          callback.take(new BarredNum(unit, num));
        }
      }
    }

    // Finally, look for locations that have no possible assignments left.
    for (Location loc : Location.all()) {
      NumSet set = marks.getSet(loc);
      if (set.isEmpty()) {
        callback.take(new BarredLoc(loc));
      }
    }
  }

  public static void findSingletonLocations(Marks marks, Callback callback) {
    for (int i = 0; i < UnitNumeral.COUNT; ++i) {
      UnitNumeral un = UnitNumeral.of(i);
      Location loc = marks.getSingleton(un);
      if (loc != null && !marks.hasAssignment(loc))
        callback.take(new ForcedLoc(un.unit, un.numeral, loc));
    }
  }

  public static void findSingletonNumerals(Marks marks, Callback callback) {
    for (int i = 0; i < Location.COUNT; ++i) {
      Location loc = Location.of(i);
      if (!marks.hasAssignment(loc)) {
        NumSet set = marks.getSet(loc);
        if (set.size() == 1)
          callback.take(new ForcedNum(loc, set.get(0)));
      }
    }
  }
}
