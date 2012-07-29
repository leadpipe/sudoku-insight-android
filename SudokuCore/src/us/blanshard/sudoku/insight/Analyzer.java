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

import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Marks;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.game.Sudoku;

import com.google.common.collect.Maps;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Analyzes a Sudoku game state, producing a series of insights about it.  Also
 * has the capability to {@linkplain #rate rate a puzzle}.
 *
 * @author Luke Blanshard
 */
public class Analyzer {

  /**
   * The phases that {@link #analyze} may go through, and that are reported to
   * the callback as they are finished.
   */
  public enum Phase {
    START,
    ELEMENTARY,
    COMPLETE,
    INTERRUPTED;
  }

  /**
   * Called by {@link #analyze} for each insight found and for each phase
   * traversed.
   */
  public interface Callback {
    void phase(Phase phase);
    void take(Insight insight);
  }

  private final Sudoku game;
  private final Callback callback;
  private final Grid solution;
  private volatile Grid analysisTarget;

  public Analyzer(Sudoku game, Callback callback) {
    this.game = checkNotNull(game);
    this.callback = checkNotNull(callback);
    this.solution = checkNotNull(Solver.solve(game.getPuzzle(), new Random()).solution);
    setAnalysisTargetId(-1);
  }

  public Sudoku getGame() {
    return game;
  }

  /**
   * Takes a snapshot of the game's current progress in the given state for use
   * by {@link #analyze} the next time it's called.
   */
  public void setAnalysisTargetId(int stateId) {
    analysisTarget = game.getState(stateId).getGrid();
  }

  /**
   * Analyzes the {@linkplain #setAnalysisTargetId current target}, providing
   * found insights to the callback.  This may be a time-consuming operation; if
   * run as a background thread it can be stopped early by interrupting the
   * thread.
   */
  public void analyze() {
    Grid work = this.analysisTarget;
    callback.phase(Phase.START);

    Marks.Builder builder = Marks.builder();
    boolean ok = builder.assignAll(work);
    Marks marks = builder.build();

    try {
      findErrors(work, marks, ok);

      ok = findOverlapsAndSets(work, builder, ok);
      marks = builder.build();

      findSingletonLocations(work, marks);
      findSingletonNumerals(work, marks);
      callback.phase(Phase.ELEMENTARY);

      // TODO(leadpipe): look for chains that lead to contradiction

      callback.phase(Phase.COMPLETE);
    } catch (InterruptedException e) {
      callback.phase(Phase.INTERRUPTED);
    }
  }

  /**
   * Rates the given puzzle, returning a floating point number between 0 and 1,
   * where 0 means trivially easy and 1 means exceptionally difficult.  This may
   * be a time-consuming operation, and may be cut short by interrupting the
   * thread running it.
   */
  public static double rate(Grid puzzle) throws InterruptedException {
    return 0;
  }

  private static void checkInterruption() throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
  }

  private static class SetState {
    private final Map<Unit, NumSet> nums = Maps.newHashMap();
    private final Map<Unit, UnitSubset> locs = Maps.newHashMap();

    NumSet getNums(Unit unit) {
      NumSet set = nums.get(unit);
      return set == null ? NumSet.of() : set;
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

  private boolean findOverlapsAndSets(Grid work, Marks.Builder builder, boolean ok)
      throws InterruptedException {
    if (ok) {
      ok &= findOverlaps(work, builder);
      ok &= findSets(work, builder);
    }
    return ok;
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

  private boolean findOverlaps(Grid work, Marks.Builder builder) throws InterruptedException {
    checkInterruption();
    boolean ok = true;
    Marks marks = builder.build();
    for (Numeral num : Numeral.ALL) {
      ok &= findOverlaps(work, marks, builder, num, Block.ALL, Unit.Type.ROW, OVERLAP_BITS);
      ok &= findOverlaps(work, marks, builder, num, Block.ALL, Unit.Type.COLUMN, OVERLAP_BITS_2);
      ok &= findOverlaps(work, marks, builder, num, Row.ALL, Unit.Type.BLOCK, OVERLAP_BITS);
      ok &= findOverlaps(work, marks, builder, num, Column.ALL, Unit.Type.BLOCK, OVERLAP_BITS);
    }
    return ok;
  }

  private boolean findOverlaps(Grid work, Marks marks, Marks.Builder builder, Numeral num,
      List<? extends Unit> units, Unit.Type overlappingType, int[] bits) {
    boolean ok = true;
    for (Unit unit : units) {
      int index = Arrays.binarySearch(bits, marks.getBits(unit, num));
      if (index >= 0) {
        UnitSubset set = UnitSubset.ofBits(unit, bits[index]);
        Unit overlappingUnit = set.get(0).unit(overlappingType);
        UnitSubset overlappingSet = marks.get(overlappingUnit, num);
        if (overlappingSet.size() > set.size()) {
          // There's something to eliminate.
          UnitSubset extra = overlappingSet.minus(set);
          callback.take(new Overlap(unit, num, extra));
          for (Location loc : extra)
            if (!builder.eliminate(loc, num))
              ok = false;
        }
      }
    }
    return ok;
  }

  private static final int MAX_SET_SIZE = 4;

  private boolean findSets(Grid work, Marks.Builder builder) throws InterruptedException {
    SetState setState = new SetState();
    Marks marks = builder.build();
    boolean ok = true;
    int[] indices = new int[MAX_SET_SIZE];
    for (Unit unit : Unit.allUnits()) {
      for (int size = 2; size <= MAX_SET_SIZE; ++size) {
        ok &= findNakedSets(work, marks, builder, setState, unit, size, indices);
        ok &= findHiddenSets(work, marks, builder, setState, unit, size, indices);
      }
    }
    return ok;
  }

  private boolean findNakedSets(Grid work, Marks marks, Marks.Builder builder,
      SetState setState, Unit unit, int size, int[] indices) {
    boolean ok = true;
    UnitSubset inSets = setState.getLocs(unit);
    UnitSubset toCheck = UnitSubset.of(unit);
    int unsetCount = 0;
    for (Location loc : unit) {
      NumSet possible = marks.get(loc);
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
        for (int i = 0; i < size; ++i)
          bits |= marks.get(toCheck.get(indices[i])).bits;
        NumSet nums = NumSet.ofBits(bits);
        if (nums.size() == size) {
          UnitSubset locs = UnitSubset.of(unit);
          for (int i = 0; i < size; ++i)
            locs = locs.with(toCheck.get(indices[i]));
          setState.add(nums, locs);
          callback.take(new LockedSet(nums, locs, true));
          for (Location loc : locs.not())
            for (Numeral num : nums)
              if (!builder.eliminate(loc, num))
                ok = false;
          break;
        }
      } while (nextSubset(size, indices, toCheck.size()));
    }
    return ok;
  }

  private boolean findHiddenSets(Grid work, Marks marks, Marks.Builder builder,
      SetState setState, Unit unit, int size, int[] indices) {
    boolean ok = true;
    NumSet inSets = setState.getNums(unit);
    NumSet toCheck = NumSet.of();
    int unsetCount = 0;
    for (Numeral num : Numeral.ALL) {
      UnitSubset possible = marks.get(unit, num);
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
        for (int i = 0; i < size; ++i)
          bits |= marks.get(unit, toCheck.get(indices[i])).bits;
        UnitSubset locs = UnitSubset.ofBits(unit, bits);
        if (locs.size() == size) {
          NumSet nums = NumSet.of();
          for (int i = 0; i < size; ++i)
            nums = nums.with(toCheck.get(indices[i]));
          setState.add(nums, locs);
          callback.take(new LockedSet(nums, locs, false));
          for (Location loc : locs)
            for (Numeral num : nums.not())
              if (!builder.eliminate(loc, num))
                ok = false;
          break;
        }
      } while (nextSubset(size, indices, toCheck.size()));
    }
    return ok;
  }

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

  private void findErrors(Grid work, Marks marks, boolean ok) throws InterruptedException {
    checkInterruption();
    if (!ok) {
      findErrors(work, marks, callback);
    }
  }

  private void findSingletonLocations(Grid work, Marks marks) throws InterruptedException {
    checkInterruption();
    findSingletonLocations(work, marks, callback);
  }

  private void findSingletonNumerals(Grid work, Marks marks) throws InterruptedException {
    checkInterruption();
    findSingletonNumerals(work, marks, callback);
  }

  public static void findOverlapsAndSets(Grid work, Marks marks, Callback callback) {
    findOverlaps(work, marks, callback);
    findSets(work, marks, callback);
  }

  private static void findOverlaps(Grid work, Marks marks, Callback callback) {
    for (Numeral num : Numeral.ALL) {
      findOverlaps(work, marks, callback, num, Block.ALL, Unit.Type.ROW, OVERLAP_BITS);
      findOverlaps(work, marks, callback, num, Block.ALL, Unit.Type.COLUMN, OVERLAP_BITS_2);
      findOverlaps(work, marks, callback, num, Row.ALL, Unit.Type.BLOCK, OVERLAP_BITS);
      findOverlaps(work, marks, callback, num, Column.ALL, Unit.Type.BLOCK, OVERLAP_BITS);
    }
  }

  private static void findOverlaps(Grid work, Marks marks, Callback callback, Numeral num,
      List<? extends Unit> units, Unit.Type overlappingType, int[] bits) {
    for (Unit unit : units) {
      int index = Arrays.binarySearch(bits, marks.getBits(unit, num));
      if (index >= 0) {
        UnitSubset set = UnitSubset.ofBits(unit, bits[index]);
        Unit overlappingUnit = set.get(0).unit(overlappingType);
        UnitSubset overlappingSet = marks.get(overlappingUnit, num);
        if (overlappingSet.size() > set.size()) {
          // There's something to eliminate.
          callback.take(new Overlap(unit, num, overlappingSet.minus(set)));
        }
      }
    }
  }

  private static void findSets(Grid work, Marks marks, Callback callback) {
    SetState setState = new SetState();
    int[] indices = new int[MAX_SET_SIZE];
    for (Unit unit : Unit.allUnits()) {
      for (int size = 2; size <= MAX_SET_SIZE; ++size) {
        // Hidden sets are typically easier to see than naked ones.
        findHiddenSets(work, marks, callback, setState, unit, size, indices);
        findNakedSets(work, marks, callback, setState, unit, size, indices);
      }
    }
  }

  private static void findNakedSets(Grid work, Marks marks, Callback callback,
      SetState setState, Unit unit, int size, int[] indices) {
    UnitSubset inSets = setState.getLocs(unit);
    UnitSubset toCheck = UnitSubset.of(unit);
    int unsetCount = 0;
    for (Location loc : unit) {
      NumSet possible = marks.get(loc);
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

  private static void findHiddenSets(Grid work, Marks marks, Callback callback,
      SetState setState, Unit unit, int size, int[] indices) {
    NumSet inSets = setState.getNums(unit);
    NumSet toCheck = NumSet.of();
    int unsetCount = 0;
    for (Numeral num : Numeral.ALL) {
      UnitSubset possible = marks.get(unit, num);
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
          bits |= marks.getBits(unit, num);
          alreadyUsed |= inSets.contains(num);
        }
        if (alreadyUsed) continue;
        UnitSubset locs = UnitSubset.ofBits(unit, bits);
        if (locs.size() == size) {
          NumSet nums = NumSet.of();
          for (int i = 0; i < size; ++i)
            nums = nums.with(toCheck.get(indices[i]));
          setState.add(nums, locs);
          callback.take(new LockedSet(nums, locs, false));
          inSets = inSets.or(nums);
        }
      } while (nextSubset(size, indices, toCheck.size()));
    }
  }

  public static void findErrors(Grid work, Marks marks, Callback callback) {
    for (Unit unit : Unit.allUnits()) {

      // First look for actual conflicting assignments in this unit.
      NumSet seen = NumSet.ofBits(0);
      NumSet conflicting = NumSet.ofBits(0);
      for (Location loc : unit) {
        Numeral num = work.get(loc);
        if (num != null) {
          if (seen.contains(num)) conflicting = conflicting.with(num);
          seen = seen.with(num);
        }
      }
      for (Numeral num : conflicting) {
        UnitSubset locs = UnitSubset.ofBits(unit, 0);
        for (Location loc : unit)
          if (work.get(loc) == num) locs = locs.with(loc);
        callback.take(new Conflict(work, num, locs));
      }

      // Then look for numerals that have no possible assignments left in this
      // unit.
      for (Numeral num : conflicting.not()) {
        UnitSubset set = marks.get(unit, num);
        if (set.isEmpty()) {
          callback.take(new BarredNum(unit, num));
        }
      }
    }

    // Finally, look for locations that have no possible assignments left.
    for (Location loc : Location.ALL) {
      NumSet set = marks.get(loc);
      if (set.isEmpty()) {
        callback.take(new BarredLoc(loc));
      }
    }
  }

  public static void findSingletonLocations(Grid work, Marks marks, Callback callback) {
    for (Unit unit : Unit.allUnits())
      for (Numeral num : Numeral.ALL) {
        UnitSubset set = marks.get(unit, num);
        if (set.size() == 1) {
          Location loc = set.get(0);
          if (!work.containsKey(loc))
            callback.take(new ForcedLoc(unit, num, loc));
        }
      }
  }

  public static void findSingletonNumerals(Grid work, Marks marks, Callback callback) {
    for (Location loc : Location.ALL)
      if (!work.containsKey(loc)) {
        NumSet set = marks.get(loc);
        if (set.size() == 1)
          callback.take(new ForcedNum(loc, set.get(0)));
      }
  }
}
