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

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

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
@Immutable
public final class SolverMarks {

  private final short[] bits;
  private static final short ALL_BITS = (1 << 9) - 1;
  private static final int UNIT_OFFSET = Location.COUNT;

  private SolverMarks(short[] bits) {
    this.bits = bits;
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
    for (Location loc : Location.all()) {
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
    return NumSet.ofBits(bits[loc.index]);
  }

  /**
   * Returns the set of locations within the given unit that could hold the
   * given numeral.
   */
  public UnitSubset get(UnitNumeral unitNum) {
    return UnitSubset.ofBits(unitNum.unit, getBits(unitNum));
  }

  /**
   * Returns the bit-set corresponding to {@link #get(UnitNumeral)}.
   */
  public int getBits(UnitNumeral unitNum) {
    return bits[UNIT_OFFSET + unitNum.index];
  }

  @NotThreadSafe
  public static final class Builder {
    private SolverMarks marks;
    private boolean built;

    private Builder() {
      this.marks = new SolverMarks(new short[Location.COUNT + UnitNumeral.COUNT]);
      this.built = false;
      clear();
    }

    private Builder(SolverMarks marks) {
      this.marks = marks;
      this.built = true;
    }

    private SolverMarks marks() {
      if (built) {
        SolverMarks marks = new SolverMarks(this.marks.bits.clone());
        this.marks = marks;
        this.built = false;
      }
      return this.marks;
    }

    public SolverMarks build() {
      built = true;
      return marks;
    }

    public Grid asGrid() {
      return marks.toGrid();
    }

    public NumSet get(Location loc) {
      return marks.get(loc);
    }

    public UnitSubset get(UnitNumeral unitNum) {
      return marks.get(unitNum);
    }

    public int getBits(UnitNumeral unitNum) {
      return marks.getBits(unitNum);
    }

    /**
     * Resets this builder to a state of all possibilities open.
     */
    public Builder clear() {
      Arrays.fill(marks().bits, ALL_BITS);
      return this;
    }

    /**
     * Assigns the given numeral to the given location, recursively assigning
     * numerals to other locations when they become the only possibilities.
     * Returns true if all the assignments that follow from this assignment are
     * consistent with the rules of Sudoku.
     */
    public boolean assignRecursively(Location loc, Numeral num) {
      NumSet others = get(loc).minus(num.asSet());
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
      UnitNumeral unitNum = UnitNumeral.of(unitSubset.unit, num);
      marks.bits[UNIT_OFFSET + unitNum.index] &= ~unitSubset.bits;

      UnitSubset remaining = get(unitNum);
      if (remaining.size() == 0)
        return false;  // no possibilities left in this assignment set

      if (remaining.size() == 1  // Last possibility left.  Assign it.
          && !assignRecursively(remaining.get(0), num))
        return false;

      return true;
    }
  }
}
