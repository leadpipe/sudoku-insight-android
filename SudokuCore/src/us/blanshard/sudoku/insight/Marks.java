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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.asList;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;

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
 * @author Luke Blanshard
 */
@Immutable
public final class Marks {

  /**
   * The data array combines 16 bits of information for each location, for each
   * unit-numeral, and for each unit (in fact, twice for each unit).  The
   * location data comes first in the array, then the unit-numeral data, then
   * the unit data.
   *
   * <p> Location data is a 9-bit set of the numerals that could be assigned to
   * the location, plus a 4-bit slot for the numeral that is currently assigned
   * (zero means nothing currently is).  In addition, we keep an error bit in
   * the top bit of location 0's data: when set, the assignments and
   * eliminations embodied in the Marks are not consistent with the rules of
   * Sudoku.
   *
   * <p> Unit-numeral data is a 9-bit subset of the locations within the unit
   * that could be assigned to the numeral, plus a 4-bit slot for the currently
   * assigned location.  Zero means unassigned, and one through nine mean the
   * number of the assigned location within the unit.
   *
   * <p> For units, we keep two bit-sets each.  First come bit-sets meaning the
   * numerals currently unassigned within the unit, then come bit-sets meaning
   * the locations currently unassigned within the unit.
   */
  private final short[] data;

  /**
   * The number of elements of {@link #data}, one for each location and
   * unit-numeral plus two for each unit.
   */
  private static final int DATA_SIZE = Location.COUNT + UnitNumeral.COUNT + 2 * Unit.COUNT;

  /**
   * The number corresponding to "all bits included," used as the initial value
   * of every element of {@link #data}.
   */
  private static final short ALL_BITS = (1 << 9) - 1;

  /**
   * The mask to apply to {@link #data} values to extract just the bit-sets.
   */
  private static final short BITSET_MASK = ALL_BITS;

  /**
   * The number of bits to shift {@link #data} values to find assigned numerals
   * in location slots.
   */
  private static final int LOC_ASSIGNMENT_SHIFT = 9;

  /**
   * A mask to apply to {@link #data} values to extract just the assigned
   * numeral in location slots.
   */
  private static final int LOC_ASSIGNMENT_MASK = ((1 << 4) - 1) << LOC_ASSIGNMENT_SHIFT;

  /**
   * The bit mask for {@link #data} slot zero used to indicate an error.
   */
  private static final int LOC0_ERROR_BIT = 1 << 15;

  /**
   * The offset within {@link #data} where unit-numeral data begins.
   */
  private static final int UNITNUM_OFFSET = Location.COUNT;

  /**
   * The number of bits to shift {@link #data} values to find assigned locations
   * in unit-numeral slots.
   */
  private static final int UNITNUM_ASSIGNMENT_SHIFT = 9;

  /**
   * A mask to apply to {@link #data} values to extract just the assigned
   * location in unit-numeral slots.
   */
  private static final int UNITNUM_ASSIGNMENT_MASK = ((1 << 4) - 1) << UNITNUM_ASSIGNMENT_SHIFT;

  /**
   * The offset within {@link #data} where unit numeral-set data begins (the set
   * of numerals currently unassigned within the unit).
   */
  private static final int UNIT_NUMSET_OFFSET = UNITNUM_OFFSET + UnitNumeral.COUNT;

  /**
   * The offset within {@link #data} where unit location-set data begins (the
   * set of locations currently unassigned within the unit).
   */
  private static final int UNIT_LOCSET_OFFSET = UNIT_NUMSET_OFFSET + Unit.COUNT;

  private Marks(short[] data) {
    this.data = data;
  }

  public static Marks fromGrid(Grid grid) {
    return builder().assignAll(grid).build();
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
      Numeral num = get(loc);
      if (num != null) builder.put(loc, num);
    }
    return builder;
  }

  /**
   * Tells whether one or more of the assignments or eliminations made in this
   * Marks resulted in there being no possible moves.
   */
  public boolean hasErrors() {
    return (data[0] & LOC0_ERROR_BIT) != 0;
  }

  /**
   * Returns the set of numerals that could go in the given location.
   */
  public NumSet getSet(Location loc) {
    return NumSet.ofBits(getBits(loc));
  }

  /**
   * Returns the bit-set corresponding to {@link #getSet(Location)}.
   */
  public int getBits(Location loc) {
    return data[loc.index] & BITSET_MASK;
  }

  /**
   * Returns the single numeral contained in {@link #getSet(Location)}, or null.
   */
  @Nullable public Numeral getSingleton(Location loc) {
    NumSet set = getSet(loc);
    return set.size() == 1 ? set.get(0) : null;
  }

  /**
   * Returns the numeral assigned to the given location, or null.
   */
  @Nullable public Numeral get(Location loc) {
    int assigned = (data[loc.index] & LOC_ASSIGNMENT_MASK) >> LOC_ASSIGNMENT_SHIFT;
    return assigned == 0 ? null : Numeral.of(assigned);
  }

  /**
   * Tells whether the given location has a numeral assigned to it.
   */
  public boolean hasAssignment(Location loc) {
    int assigned = (data[loc.index] & LOC_ASSIGNMENT_MASK) >> LOC_ASSIGNMENT_SHIFT;
    return assigned != 0;
  }

  /**
   * Returns a set of the locations that have assignments.
   */
  public LocSet getAssignedLocations() {
    LocSet answer = new LocSet();
    for (int index = 0; index < Location.COUNT; ++index) {
      if ((data[index] & LOC_ASSIGNMENT_MASK) != 0)
        answer.add(Location.of(index));
    }
    return answer;
  }

  /**
   * Returns the number of locations that have been assigned numerals.
   */
  public int getNumAssignments() {
    int answer = 0;
    for (int index = 0; index < Location.COUNT; ++index) {
      if ((data[index] & LOC_ASSIGNMENT_MASK) != 0)
        ++answer;
    }
    return answer;
  }

  /**
   * Returns the number of locations that have not been assigned numerals.
   */
  public int getNumOpenLocations() {
    return Location.COUNT - getNumAssignments();
  }

  /**
   * Is this grid correctly solved?
   */
  public boolean isSolved() {
    return getNumOpenLocations() == 0 && !hasErrors();
  }

  /**
   * Returns the set of locations within the given unit that could hold the
   * given numeral.
   */
  public UnitSubset getSet(UnitNumeral unitNum) {
    return UnitSubset.ofBits(unitNum.unit, getBits(unitNum));
  }

  /**
   * Returns the bit-set corresponding to {@link #getSet(UnitNumeral)}.
   */
  public int getBits(UnitNumeral unitNum) {
    return data[UNITNUM_OFFSET + unitNum.index] & BITSET_MASK;
  }

  /**
   * Returns the size of the set that would be returned by
   * {@link #getSet(UnitNumeral)}.
   */
  public int getSetSize(UnitNumeral unitNum) {
    return NumSet.ofBits(getBits(unitNum)).size();
  }

  /**
   * Returns the single location in {@link #getSet(UnitNumeral)}, or null.
   */
  @Nullable public Location getSingleton(UnitNumeral unitNum) {
    NumSet set = NumSet.ofBits(getBits(unitNum));
    if (set.size() != 1) return null;
    return unitNum.unit.get(set.get(0).index);
  }

  /**
   * Returns the location assigned to the given numeral in the given unit, or
   * null.
   */
  @Nullable public Location get(UnitNumeral unitNum) {
    int assigned = (data[UNITNUM_OFFSET + unitNum.index] & UNITNUM_ASSIGNMENT_MASK) >> UNITNUM_ASSIGNMENT_SHIFT;
    return assigned == 0 ? null : unitNum.unit.get(assigned - 1);
  }

  /**
   * Returns true if the grid contains an assignment of the given numeral
   * within the given unit.
   */
  public boolean hasAssignment(UnitNumeral unitNum) {
    int assigned = (data[UNITNUM_OFFSET + unitNum.index] & UNITNUM_ASSIGNMENT_MASK) >> UNITNUM_ASSIGNMENT_SHIFT;
    return assigned != 0;
  }

  /**
   * Returns a bit-set of the numerals that do not yet have an assigned location
   * within the given unit.
   */
  public int getUnassignedNumeralBits(Unit unit) {
    return data[UNIT_NUMSET_OFFSET + unit.index] & BITSET_MASK;
  }

  /**
   * Returns the set of numerals that are currently unassigned within the given
   * unit.
   */
  public NumSet getUnassignedNumerals(Unit unit) {
    return NumSet.ofBits(getUnassignedNumeralBits(unit));
  }

  /**
   * Returns a bit-set of the locations within the given unit that are not
   * currently assigned a numeral.
   */
  public int getUnassignedLocationBits(Unit unit) {
    return data[UNIT_LOCSET_OFFSET + unit.index] & BITSET_MASK;
  }

  /**
   * Returns a subset of the locations in the given unit that are not currently
   * assigned a numeral.
   */
  public UnitSubset getUnassignedLocations(Unit unit) {
    return UnitSubset.ofBits(unit, getUnassignedLocationBits(unit));
  }

  @NotThreadSafe
  public static final class Builder {
    private Marks marks;
    private boolean built;

    private Builder() {
      this.marks = new Marks(new short[DATA_SIZE]);
      this.built = false;
      clear();
    }

    private Builder(Marks marks) {
      this.marks = marks;
      this.built = true;
    }

    private Marks marks() {
      if (built) {
        Marks marks = new Marks(this.marks.data.clone());
        this.marks = marks;
        this.built = false;
      }
      return this.marks;
    }

    public Marks build() {
      built = true;
      return marks;
    }

    public Grid toGrid() {
      return marks.toGrid();
    }

    public NumSet get(Location loc) {
      return marks.getSet(loc);
    }

    public int getBits(Location loc) {
      return marks.getBits(loc);
    }

    public UnitSubset get(UnitNumeral unitNum) {
      return marks.getSet(unitNum);
    }

    public int getBits(UnitNumeral unitNum) {
      return marks.getBits(unitNum);
    }

    public boolean hasErrors() {
      return marks.hasErrors();
    }

    /**
     * Resets this builder to a state of all possibilities open.
     */
    public Builder clear() {
      Arrays.fill(marks().data, ALL_BITS);
      return this;
    }

    /**
     * Assigns the given numeral to the given location.  Sets the error bit if
     * the assignment is inconsistent with the rules of Sudoku.
     */
    public Builder assign(Location loc, Numeral num) {
      boolean ok = true;

      // Remove this numeral from this location's peers.
      for (Location peer : loc.peers)
        ok &= eliminateInternal(peer, num);

      // Remove the other numerals from this location.
      NumSet others = get(loc).without(num);
      for (Numeral other : others)
        ok &= eliminateInternal(loc, other);

      // Save the numeral in the location's data slot.
      short value = marks.data[loc.index];
      value &= ~LOC_ASSIGNMENT_MASK;
      value |= (num.number << LOC_ASSIGNMENT_SHIFT);
      marks.data[loc.index] = value;

      // Save the location in the 3 unit-numerals' slots, and reduce the sets of
      // available numerals and locations in each unit.
      for (int i = 0; i < 3; ++i) {
        UnitSubset unitSubset = loc.unitSubsetList.get(i);
        int index = UnitNumeral.getIndex(unitSubset.unit, num);
        value = marks.data[UNITNUM_OFFSET + index];
        value &= ~UNITNUM_ASSIGNMENT_MASK;
        value |= (unitSubset.getIndex(0) + 1) << UNITNUM_ASSIGNMENT_SHIFT;
        marks.data[UNITNUM_OFFSET + index] = value;

        marks.data[UNIT_NUMSET_OFFSET + unitSubset.unit.index] &= ~num.bit;
        marks.data[UNIT_LOCSET_OFFSET + unitSubset.unit.index] &= ~unitSubset.bits;
      }

      if (ok) ok = getBits(loc) == num.bit;
      if (!ok) setError();
      return this;
    }

    private void setError() {
      marks().data[0] |= LOC0_ERROR_BIT;
    }

    /**
     * Makes the given assignment.  Sets the error bit if the assignment is inconsistent
     * with the rules of Sudoku.
     */
    public Builder assign(Assignment assignment) {
      return assign(assignment.location, assignment.numeral);
    }

    /**
     * Eliminates the given numeral as a possibility for the given location, and
     * the location as a possibility for the numeral within the location's
     * units.  Sets the error bit if any of these sets ends up empty.
     */
    public Builder eliminate(Location loc, Numeral num) {
      eliminateInternal(loc, num);
      return this;
    }

    private boolean eliminateInternal(Location loc, Numeral num) {
      boolean answer = true;

      if (((marks().data[loc.index] &= ~num.bit) & BITSET_MASK) == 0)
        answer = false;  // This location has no possibilities left

      for (int i = 0; i < 3; ++i) {
        UnitSubset unitSubset = loc.unitSubsetList.get(i);
        int index = UnitNumeral.getIndex(unitSubset.unit, num);
        if ((marks.data[UNITNUM_OFFSET + index] &= ~unitSubset.bits) == 0)
          answer = false;  // This numeral has no possible locations left in this unit
      }

      if (!answer)
        setError();
      return answer;
    }

    /**
     * Eliminates the given assignment.  Sets the error bit if this is
     * inconsistent with the rules of Sudoku.
     */
    public Builder eliminate(Assignment assignment) {
      return eliminate(assignment.location, assignment.numeral);
    }

    /**
     * Assigns all the associated locations and numerals in the given map (note
     * that {@link Grid} is this kind of map).
     */
    public Builder assignAll(Map<Location, Numeral> grid) {
      for (Map.Entry<Location, Numeral> entry : grid.entrySet())
        assign(entry.getKey(), entry.getValue());
      return this;
    }

    public Builder apply(Insight insight) {
      insight.apply(this);
      return this;
    }

    public Builder apply(Iterable<Insight> insights) {
      for (Insight insight : insights)
        insight.apply(this);
      return this;
    }

    public Builder apply(List<Insight> insights) {
      for (int i = 0, c = insights.size(); i < c; ++i)
        insights.get(i).apply(this);
      return this;
    }
  }

  @Override public boolean equals(Object object) {
    if (this == object) return true;
    if (!(object instanceof Marks)) return false;
    Marks that = (Marks) object;
    return Arrays.equals(this.data, that.data);
  }

  @Override public int hashCode() {
    return Arrays.hashCode(data);
  }

  @Override public String toString() {
    int width = 1;
    for (Location loc : Location.all()) {
      width = Math.max(width, getSet(loc).size());
    }
    StringBuilder sb = new StringBuilder();
    for (Row row : Row.all()) {
      for (Location loc : row) {
        append(getSet(loc), hasAssignment(loc), width, sb.append(' '));
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

  private StringBuilder append(NumSet nums, boolean assigned, int width, StringBuilder sb) {
    int size = Math.max(1, nums.size());
    append(' ', (width - size) / 2, sb);
    if (nums.isEmpty()) {
      sb.append('?');
    } else {
      for (Numeral num : nums)
        sb.append(num.number);
      if (assigned) {
        sb.append('!');
        ++size;
      }
    }
    return append(' ', width - size - (width - size) / 2, sb);
  }

  private StringBuilder append(char c, int count, StringBuilder sb) {
    while (count-- > 0)
      sb.append(c);
    return sb;
  }

  /**
   * Treats all characters besides numerals, queries, and bangs as word
   * separators.  Requires there to be 81 words. A word consisting of a question
   * mark is treated as a location with no possible assignments.  A word
   * consisting of numerals (1 through 9) is treated as a location with those
   * numerals as the possible assignments; if it's just one numeral and is
   * followed by a bang, it's treated as an assignment.
   */
  public static Marks fromString(String s) {
    Builder builder = builder();
    List<String> words = asList(s.split("[^1-9?!]+"));
    if (!words.isEmpty() && words.get(0).isEmpty())
      words = words.subList(1, words.size());
    checkArgument(words.size() == 81, "expected 81 words, got %s", words);
    NumSet[] sets = new NumSet[81];
    for (Location loc : Location.all()) {
      boolean bang = false;
      NumSet nums = NumSet.NONE;
      for (char c : words.get(loc.index).toCharArray()) {
        if (c == '!')
          bang = true;
        else if (c >= '1' && c <= '9')
          nums = nums.with(Numeral.of(c - '0'));
      }
      sets[loc.index] = nums;
      if (bang) {
        checkArgument(nums.size() == 1, "can't assign multiple numerals to the same location");
        builder.assign(loc, nums.get(0));
      }
    }
    for (Location loc : Location.all()) {
      NumSet nums = sets[loc.index];
      for (Numeral not : builder.get(loc).minus(nums))
        builder.eliminate(loc, not);
    }
    return builder.build();
  }
}
