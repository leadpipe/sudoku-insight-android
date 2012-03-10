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

import java.util.Arrays;
import java.util.Map;

/**
 * Keeps track of the possible numerals that could go in each location, like the
 * marks some people fill in to Sudoku grids.  Also keeps track of the possible
 * locations within each unit that each numeral could occupy.
 *
 * <p> The outer class is immutable; has a nested builder.
 *
 * <p> The builder's mutually recursive assign/eliminate methods are due to
 * Peter Norvig (http://norvig.com/sudoku.html).
 *
 * @author Luke Blanshard
 */
public final class Marks {

  private final short[] bits;
  private final short[] unitBits;
  private static final short ALL_BITS = (1 << 9) - 1;

  private Marks(short[] bits, short[] unitBits) {
    this.bits = bits;
    this.unitBits = unitBits;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public Grid toGrid() {
    return toGridBuilder().build();
  }

  public Grid.Builder toGridBuilder() {
    Grid.Builder builder = Grid.builder();
    for (Location loc : Location.ALL) {
      NumSet possible = get(loc);
      if (possible.size() == 1)
        builder.put(loc, possible.iterator().next());
    }
    return builder;
  }

  /**
   * Returns the set of numerals that could go in the given location.
   */
  public NumSet get(Location loc) {
    return NumSet.ofBits(getBits(loc));
  }

  /**
   * Returns the bit-set corresponding to {@link #get(Location)}.
   */
  public int getBits(Location loc) {
    return bits[loc.index];
  }

  /**
   * Returns the set of locations within the given unit that could hold the
   * given numeral.
   */
  public UnitSubset get(Unit unit, Numeral num) {
    return UnitSubset.ofBits(unit, getBits(unit, num));
  }

  /**
   * Returns the bit-set corresponding to {@link #get(Unit,Numeral)}.
   */
  public int getBits(Unit unit, Numeral num) {
    return unitBits[unit.unitIndex() * 9 + num.index];
  }

  public static final class Builder {
    private Marks marks;
    private boolean built;

    private Builder() {
      this.marks = new Marks(new short[81], new short[Unit.COUNT * 9]);
      this.built = false;
      clear();
    }

    private Builder(Marks marks) {
      this.marks = marks;
      this.built = true;
    }

    private Marks marks() {
      if (built) {
        Marks marks = new Marks(this.marks.bits.clone(), this.marks.unitBits.clone());
        this.marks = marks;
        this.built = false;
      }
      return this.marks;
    }

    public Marks build() {
      built = true;
      return marks;
    }

    public Grid asGrid() {
      return marks.toGrid();
    }

    public NumSet get(Location loc) {
      return marks.get(loc);
    }

    public int getBits(Location loc) {
      return marks.getBits(loc);
    }

    public UnitSubset get(Unit unit, Numeral num) {
      return marks.get(unit, num);
    }

    public int getBits(Unit unit, Numeral num) {
      return marks.getBits(unit, num);
    }

    /**
     * Resets this builder to a state of all possibilities open.
     */
    public Builder clear() {
      Arrays.fill(marks().bits, ALL_BITS);
      Arrays.fill(marks.unitBits, ALL_BITS);
      return this;
    }

    /**
     * Assigns the given numeral to the given location.  Returns true if this
     * assignment is (locally) consistent with the rules of Sudoku.
     */
    public boolean assign(Location loc, Numeral num) {
      boolean answer = true;

      // Remove this numeral from this location's peers.
      for (Location peer : loc.peers)
        answer &= eliminate(peer, num);

      // Remove the other numerals from this location.
      NumSet others = get(loc).minus(NumSet.of(num));
      for (Numeral other : others)
        answer &= eliminate(loc, other);

      return answer && marks.bits[loc.index] == num.bit;
    }

    /**
     * Eliminates the given numeral as a possibility for the given location, and
     * the location as a possibility for the numeral within the location's
     * units.  Returns false if any of these sets ends up empty.
     */
    public boolean eliminate(Location loc, Numeral num) {
      boolean answer = true;

      if ((marks().bits[loc.index] &= ~num.bit) == 0)
        answer = false;  // This location has no possibilities left

      for (UnitSubset unitSubset : loc.unitSubsets.values()) {
        if ((marks.unitBits[unitSubset.unit.unitIndex() * 9 + num.index] &= ~unitSubset.bits) == 0)
          answer = false;  // This numeral has no possible locations left in this unit
      }

      return answer;
    }

    /**
     * Assigns all the associated locations and numerals in the given map (note
     * that {@link Grid} is this kind of map), returns true if they could all be
     * assigned.
     */
    public boolean assignAll(Map<Location, Numeral> grid) {
      boolean answer = true;

      for (Map.Entry<Location, Numeral> entry : grid.entrySet())
        answer &= assign(entry.getKey(), entry.getValue());

      return answer;
    }

    /**
     * Assigns the given numeral to the given location, recursively assigning
     * numerals to other locations when they become the only possibilities.
     * Returns true if all the assignments that follow from this assignment are
     * consistent with the rules of Sudoku.
     */
    public boolean assignRecursively(Location loc, Numeral num) {
      NumSet others = get(loc).minus(NumSet.of(num));
      for (Numeral other : others)
        if (!eliminateRecursively(loc, other))
          return false;
      return marks.bits[loc.index] == num.bit;
    }

    /**
     * Recursively assigns all the associated locations and numerals in the
     * given map (note that {@link Grid} is this kind of map), returns true if
     * they could all be assigned.
     */
    public boolean assignAllRecursively(Map<Location, Numeral> grid) {
      for (Map.Entry<Location, Numeral> entry : grid.entrySet()) {
        if (!assignRecursively(entry.getKey(), entry.getValue()))
          return false;
      }
      return true;
    }

    /**
     * Eliminates the given numeral as a possible assignment to the given
     * location, returns true if all the ramifications of that elimination are
     * consistent with the rules of Sudoku.
     */
    private boolean eliminateRecursively(Location loc, Numeral num) {
      if (!get(loc).contains(num))
        return true;  // already eliminated

      marks().bits[loc.index] &= ~num.bit;

      NumSet remaining = get(loc);
      if (remaining.size() == 0)
        return false;  // no possibilities left here

      if (remaining.size() == 1) {
        // Last possibility left.  Eliminate it from this location's peers.
        Numeral last = remaining.iterator().next();
        for (Location peer : loc.peers)
          if (!eliminateRecursively(peer, last))
            return false;
      }

      // Look in all units this location belongs to, to see if there's only a
      // single remaining slot in the unit for this numeral, and if so, assign
      // it there.
      for (UnitSubset unitSubset : loc.unitSubsets.values()) {
        if (!eliminateRecursivelyFromUnit(num, unitSubset))
          return false;
      }

      return true;
    }

    /**
     * Eliminates the location in the given unit subset from the locations in
     * the unit that could contain the given numeral.  Returns false if a
     * contradiction is found.  The given subset is guaranteed to be a
     * singleton.
     */
    private boolean eliminateRecursivelyFromUnit(Numeral num, UnitSubset unitSubset) {
      // Remove this location from the possible locations within this unit
      // that this numeral may be assigned.
      marks.unitBits[unitSubset.unit.unitIndex() * 9 + num.index] &= ~unitSubset.bits;

      UnitSubset remaining = get(unitSubset.unit, num);
      if (remaining.size() == 0)
        return false;  // no possibilities left in this assignment set

      if (remaining.size() == 1  // Last possibility left.  Assign it.
          && !assignRecursively(remaining.iterator().next(), num))
        return false;

      return true;
    }
  }

  @Override public boolean equals(Object object) {
    if (this == object) return true;
    if (!(object instanceof Marks)) return false;
    Marks that = (Marks) object;
    return Arrays.equals(this.bits, that.bits);
  }

  @Override public int hashCode() {
    return Arrays.hashCode(bits);
  }

  @Override public String toString() {
    int width = 1;
    for (Location loc : Location.ALL) {
      width = Math.max(width, get(loc).size());
    }
    StringBuilder sb = new StringBuilder();
    for (Row row : Row.ALL) {
      for (Location loc : row) {
        append(get(loc), width, sb.append(' '));
        if (loc.column.number == 3 || loc.column.number == 6)
          sb.append(" |");
      }
      sb.append('\n');
      if (row.number == 3 || row.number == 6) {
        append('-', 3 * width + 4, sb).append('+');
        append('-', 3 * width + 4, sb).append('+');
        append('-', 3 * width + 4, sb).append('\n');
      }
    }
    return sb.toString();
  }

  private StringBuilder append(NumSet nums, int width, StringBuilder sb) {
    int size = Math.max(1, nums.size());
    append(' ', (width - size) / 2, sb);
    if (nums.isEmpty()) {
      sb.append('?');
    } else {
      for (Numeral num : nums)
        sb.append(num.number);
    }
    return append(' ', width - size - (width - size) / 2, sb);
  }

  private StringBuilder append(char c, int count, StringBuilder sb) {
    while (count-- > 0)
      sb.append(c);
    return sb;
  }
}
