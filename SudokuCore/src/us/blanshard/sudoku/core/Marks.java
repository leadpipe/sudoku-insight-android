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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.asList;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
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
  public UnitSubset get(UnitNumeral unitNum) {
    return UnitSubset.ofBits(unitNum.unit, getBits(unitNum));
  }

  /**
   * Returns the bit-set corresponding to {@link #get(UnitNumeral)}.
   */
  public int getBits(UnitNumeral unitNum) {
    return unitBits[unitNum.index];
  }

  /**
   * Returns the size of the set that would be returned by {@link #get(UnitNumeral)}.
   */
  public int getSize(UnitNumeral unitNum) {
    return NumSet.ofBits(getBits(unitNum)).size();
  }

  /**
   * Returns the single location in {@link #get(UnitNumeral)}, or null.
   */
  @Nullable public Location getSingleton(UnitNumeral unitNum) {
    NumSet set = NumSet.ofBits(getBits(unitNum));
    if (set.size() != 1) return null;
    return unitNum.unit.get(set.get(0).index);
  }

  @NotThreadSafe
  public static final class Builder {
    private Marks marks;
    private boolean built;

    private Builder() {
      this.marks = new Marks(new short[Location.COUNT], new short[UnitNumeral.COUNT]);
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
      NumSet others = get(loc).minus(num.asSet());
      for (Numeral other : others)
        answer &= eliminate(loc, other);

      return answer && marks.bits[loc.index] == num.bit;
    }

    /**
     * Makes the given assignment, returns true if it is (locally) consistent
     * with the rules of Sudoku.
     */
    public boolean assign(Assignment assignment) {
      return assign(assignment.location, assignment.numeral);
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

      for (int i = 0; i < 3; ++i) {
        UnitSubset unitSubset = loc.unitSubsetList.get(i);
        int index = UnitNumeral.getIndex(unitSubset.unit, num);
        if ((marks.unitBits[index] &= ~unitSubset.bits) == 0)
          answer = false;  // This numeral has no possible locations left in this unit
      }

      return answer;
    }

    /**
     * Eliminates the given assignment, returns true if this is (locally)
     * consistent with the rules of Sudoku.
     */
    public boolean eliminate(Assignment assignment) {
      return eliminate(assignment.location, assignment.numeral);
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
      marks.unitBits[unitNum.index] &= ~unitSubset.bits;

      UnitSubset remaining = get(unitNum);
      if (remaining.size() == 0)
        return false;  // no possibilities left in this assignment set

      if (remaining.size() == 1  // Last possibility left.  Assign it.
          && !assignRecursively(remaining.get(0), num))
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
    for (Location loc : Location.all()) {
      width = Math.max(width, get(loc).size());
    }
    StringBuilder sb = new StringBuilder();
    for (Row row : Row.all()) {
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

  /**
   * Treats all characters besides numerals and question marks as word
   * separators.  Requires there to be 81 words.  A word consisting of a
   * question mark is treated as a location with no possible assignments.  A
   * word consisting of numerals (1 through 9) is treated as a location with
   * those numerals as the possible assignments.
   */
  public static Marks fromString(String s) {
    Builder builder = builder();
    List<String> words = asList(s.split("[^1-9?]+"));
    if (!words.isEmpty() && words.get(0).isEmpty())
      words = words.subList(1, words.size());
    checkArgument(words.size() == 81, "expected 81 words, got %s", words);
    for (Location loc : Location.all()) {
      NumSet nums = NumSet.NONE;
      for (char c : words.get(loc.index).toCharArray()) {
        if (c >= '1' && c <= '9')
          nums = nums.with(Numeral.of(c - '0'));
      }
      for (Numeral not : nums.not())
        builder.eliminate(loc, not);
    }
    return builder.build();
  }
}
