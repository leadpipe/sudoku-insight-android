/*
Copyright 2011 Google Inc.

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
package us.blanshard.sudoku.core;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * A row, column, or block of a Sudoku grid: a set of 9 locations that must all
 * contain different numerals in a valid Sudoku.
 *
 * @author Luke Blanshard
 */
public abstract class Unit extends AbstractCollection<Location> implements Collection<Location> {

  public static final int COUNT = 3 * 9;  // 9 each of rows, columns, and blocks.

  public enum Type {
    ROW, COLUMN, BLOCK
  }

  /**
   * Returns a list of all the units.  The index for each unit equals what's
   * returned by {@link #unitIndex}.
   */
  public static List<Unit> allUnits() {
    return AllUnits.INSTANCE.list;
  }

  /** Returns the row, column, or block corresponding to the given unit index. */
  public static Unit ofIndex(int unitIndex) {
    if (unitIndex < 9) return Row.ofIndex(unitIndex);
    if (unitIndex < 18) return Column.ofIndex(unitIndex - 9);
    return Block.ofIndex(unitIndex - 18);
  }

  /** Returns the numerals that have conflicts in this unit in the given grid. */
  public final NumSet getConflicts(Grid grid) {
    return NumSet.ofBits(getConflictBits(grid));
  }

  /** Returns the numerals that are not set in this unit in the given grid. */
  public final NumSet getMissing(Grid grid) {
    return NumSet.ofBits(getMissingBits(grid));
  }

  final short getConflictBits(Grid grid) {
    short bits = 0, seen = 0;
    for (Location loc : this) {
      if (grid.containsKey(loc)) {
        int bit = 1 << grid.get(loc).index;
        if ((seen & bit) != 0) bits |= bit;
        seen |= bit;
      }
    }
    return bits;
  }

  final short getMissingBits(Grid grid) {
    short bits = (1 << 9) - 1;  // All on.
    for (Location loc : this) {
      if (grid.containsKey(loc)) {
        int bit = 1 << grid.get(loc).index;
        bits &= ~bit;
      }
    }
    return bits;
  }

  /** The index of this unit in {@link #allUnits}. */
  public abstract int unitIndex();

  public abstract boolean contains(Location loc);
  public abstract Type getType();

  /** Returns the index of the given location within this unit, or -1. */
  public final int indexOf(Location loc) {
    for (int i = 0; i < 9; ++i)
      if (locations[i] == loc.index)
        return i;
    return -1;
  }

  /** Returns the location at the given index within this unit. */
  public final Location get(int index) {
    return Location.of(locations[index]);
  }

  @Override public final int size() {
    return 9;
  }

  @Override public final boolean contains(Object o) {
    if (o instanceof Location) {
      return contains((Location) o);
    }
    return false;
  }

  @Override public final Iterator<Location> iterator() {
    return Location.iterator(locations);
  }

  protected final byte[] locations = new byte[9];

  private static enum AllUnits {
    INSTANCE;
    private final List<Unit> list;
    private AllUnits() {
      this.list = ImmutableList.<Unit>builder()
          .addAll(Row.ALL)  // This order is relied on by unitIndex impls.
          .addAll(Column.ALL)
          .addAll(Block.ALL)
          .build();
    }
  }
}
