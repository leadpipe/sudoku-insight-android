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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.primitives.Ints;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Keeps track of the possible numerals that could go in each location, like the
 * marks some people fill in to Sudoku grids.  Also keeps track of the possible
 * locations within each unit that each numeral could occupy.  And tracks the
 * Insights that led to each set of numerals and locations.
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
   * For eliminated assignments, the insights that disprove them.
   */
  private final ListMultimap<Assignment, Insight> eliminations;

  /**
   * The eliminated assignments whose corresponding lists in {@link
   * #eliminations} are not in sorted order.
   */
  private final Set<Assignment> unsortedEliminations;

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

  private Marks(short[] data, ListMultimap<Assignment, Insight> eliminations,
                Set<Assignment> unsortedEliminations) {
    this.data = data;
    this.eliminations = eliminations;
    this.unsortedEliminations = unsortedEliminations;
  }

  public static Builder builder(Grid grid) {
    return new Builder(grid);
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
      Numeral num = getAssignedNumeral(loc);
      if (num != null) builder.put(loc, num);
    }
    return builder;
  }

  /**
   * Tells whether one or more of the assignments or eliminations made in this
   * Marks resulted in the rules of Sudoku being broken.
   */
  public boolean hasErrors() {
    return (data[0] & LOC0_ERROR_BIT) != 0;
  }

  /**
   * Returns the set of numerals that could go in the given location.
   */
  public NumSet getPossibleNumerals(Location loc) {
    return NumSet.ofBits(getBitsForPossibleNumerals(loc));
  }

  /**
   * Returns the bit-set corresponding to {@link #getPossibleNumerals(Location)}.
   */
  public int getBitsForPossibleNumerals(Location loc) {
    return data[loc.index] & BITSET_MASK;
  }

  /**
   * Returns the single numeral contained in {@link #getPossibleNumerals(Location)}, or null.
   */
  @Nullable public Numeral getOnlyPossibleNumeral(Location loc) {
    NumSet set = getPossibleNumerals(loc);
    return set.size() == 1 ? set.get(0) : null;
  }

  /**
   * Returns the numeral assigned to the given location, or null.
   */
  @Nullable public Numeral getAssignedNumeral(Location loc) {
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
  public UnitSubset getPossibleLocations(UnitNumeral unitNum) {
    return UnitSubset.ofBits(unitNum.unit, getBitsForPossibleLocations(unitNum));
  }

  /**
   * Returns the bit-set corresponding to {@link #getPossibleLocations(UnitNumeral)}.
   */
  public int getBitsForPossibleLocations(UnitNumeral unitNum) {
    return data[UNITNUM_OFFSET + unitNum.index] & BITSET_MASK;
  }

  /**
   * Returns the size of the set that would be returned by
   * {@link #getPossibleLocations(UnitNumeral)}.
   */
  public int getSizeOfPossibleLocations(UnitNumeral unitNum) {
    return NumSet.ofBits(getBitsForPossibleLocations(unitNum)).size();
  }

  /**
   * Returns the single location in {@link #getPossibleLocations(UnitNumeral)}, or null.
   */
  @Nullable public Location getOnlyPossibleLocation(UnitNumeral unitNum) {
    NumSet set = NumSet.ofBits(getBitsForPossibleLocations(unitNum));
    if (set.size() != 1) return null;
    return unitNum.unit.get(set.get(0).index);
  }

  /**
   * Returns the location assigned to the given numeral in the given unit, or
   * null.
   */
  @Nullable public Location getAssignedLocation(UnitNumeral unitNum) {
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
  public int getBitsForUnassignedNumerals(Unit unit) {
    return data[UNIT_NUMSET_OFFSET + unit.index] & BITSET_MASK;
  }

  /**
   * Returns the set of numerals that are currently unassigned within the given
   * unit.
   */
  public NumSet getUnassignedNumerals(Unit unit) {
    return NumSet.ofBits(getBitsForUnassignedNumerals(unit));
  }

  /**
   * Returns a bit-set of the locations within the given unit that are not
   * currently assigned a numeral.
   */
  public int getBitsForUnassignedLocations(Unit unit) {
    return data[UNIT_LOCSET_OFFSET + unit.index] & BITSET_MASK;
  }

  /**
   * Returns a subset of the locations in the given unit that are not currently
   * assigned a numeral.
   */
  public UnitSubset getUnassignedLocations(Unit unit) {
    return UnitSubset.ofBits(unit, getBitsForUnassignedLocations(unit));
  }

  /**
   * Tells whether the given numeral could be assigned to the given location.
   */
  public boolean isPossibleAssignment(Location loc, Numeral num) {
    return (data[loc.index] & num.bit) != 0;
  }

  /**
   * Tells whether the given location-numeral pair has been eliminated as a
   * possible assignment by another assignment.
   */
  public boolean isEliminatedByAssignment(Location loc, Numeral num) {
    Numeral alreadyNum = getAssignedNumeral(loc);
    if (alreadyNum != null) {
      return alreadyNum != num;
    }
    for (int i = 0; i < 3; ++i) {
      UnitNumeral un = UnitNumeral.of(loc.unitSubsetList.get(i).unit, num);
      Location alreadyLoc = getAssignedLocation(un);
      if (alreadyLoc != null) {
        return alreadyLoc != loc;
      }
    }
    return false;
  }

  /**
   * Returns the list of insights that imply the given assignment is not
   * possible.  The returned list is guaranteed to be sorted in cost order.
   */
  public synchronized List<Insight> getEliminationInsights(Assignment elimination) {
    List<Insight> list = eliminations.get(elimination);
    if (list.size() > 1 && unsortedEliminations.remove(elimination)) {
      Collections.sort(list, new Comparator<Insight>(){
          @Override public int compare(Insight a, Insight b) {
            return Ints.compare(a.getCost(), b.getCost());
          }
        });
    }
    return Collections.unmodifiableList(list);
  }

  @NotThreadSafe
  public static final class Builder {
    private Marks marks;
    private boolean built;

    private Builder(Grid grid) {
      this.marks = new Marks(new short[DATA_SIZE],
          ArrayListMultimap.<Assignment, Insight>create(), new HashSet<Assignment>());
      this.built = false;
      Arrays.fill(marks.data, ALL_BITS);
      for (Map.Entry<Location, Numeral> entry : grid.entrySet()) {
        add(new ExplicitAssignment(Assignment.of(entry.getKey(), entry.getValue())));
      }
    }

    private Builder(Marks marks) {
      this.marks = marks;
      this.built = true;
    }

    private Marks marks() {
      if (built) {
        this.marks = new Marks(
            this.marks.data.clone(),
            ArrayListMultimap.create(this.marks.eliminations),
            new HashSet<>(this.marks.unsortedEliminations));
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

    public NumSet getPossibleNumerals(Location loc) {
      return marks.getPossibleNumerals(loc);
    }

    public int getBitsForPossibleNumerals(Location loc) {
      return marks.getBitsForPossibleNumerals(loc);
    }

    public UnitSubset getPossibleLocations(UnitNumeral unitNum) {
      return marks.getPossibleLocations(unitNum);
    }

    public int getBitsForPossibleLocations(UnitNumeral unitNum) {
      return marks.getBitsForPossibleLocations(unitNum);
    }

    public boolean hasErrors() {
      return marks.hasErrors();
    }

    /**
     * Adds the given insight to this builder, assigning or eliminating as
     * needed.
     */
    public Builder add(Insight insight) {
      if (insight.isAssignment()) {
        assign(insight);
      } else if (insight.isElimination()) {
        for (Assignment a : insight.getEliminations()) {
          eliminate(a, insight);
        }
      }
      return this;
    }

    /**
     * Assigns the given numeral to the given location.  Sets the error bit if
     * the assignment is inconsistent with the rules of Sudoku.
     */
    private void assign(Insight insight) {
      Assignment assignment = insight.getAssignment();
      assert assignment != null;
      Location loc = assignment.location;
      Numeral num = assignment.numeral;

      boolean ok = true;

      // Remove this numeral from this location's peers, marking the
      // eliminations with the insight.
      for (Location peer : loc.peers)
        ok &= eliminate(Assignment.of(peer, num), insight);

      // Remove the other numerals from this location, WITHOUT marking with the
      // insight.
      NumSet others = getPossibleNumerals(loc).without(num);
      for (Numeral other : others)
        ok &= eliminate(Assignment.of(loc, other), null);

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

      if (ok) ok = getBitsForPossibleNumerals(loc) == num.bit;
      if (!ok) setError();
    }

    private void setError() {
      marks().data[0] |= LOC0_ERROR_BIT;
    }

    /**
     * Eliminates the given assignment.  Sets the error bit, and returns false,
     * if this is inconsistent with the rules of Sudoku.
     */
    private boolean eliminate(Assignment assignment, @Nullable Insight insight) {
      Location loc = assignment.location;
      Numeral num =  assignment.numeral;
      boolean answer = true;

      if (((marks().data[loc.index] &= ~num.bit) & BITSET_MASK) == 0)
        answer = false;  // This location has no possibilities left

      for (int i = 0; i < 3; ++i) {
        UnitSubset unitSubset = loc.unitSubsetList.get(i);
        int index = UnitNumeral.getIndex(unitSubset.unit, num);
        if ((marks.data[UNITNUM_OFFSET + index] &= ~unitSubset.bits) == 0)
          answer = false;  // This numeral has no possible locations left in this unit
      }

      if (insight != null) {
        List<Insight> insights = marks.eliminations.get(assignment);
        insights.add(insight);
        if (insights.size() > 1) {
          marks.unsortedEliminations.add(assignment);
        }
      }

      if (!answer) setError();
      return answer;
    }
  }

  @Override public String toString() {
    int width = 1;
    for (Location loc : Location.all()) {
      width = Math.max(width, getPossibleNumerals(loc).size());
      if (width == 1 && hasAssignment(loc)) {
        width = 2;
      }
    }
    StringBuilder sb = new StringBuilder();
    for (Row row : Row.all()) {
      for (Location loc : row) {
        append(getPossibleNumerals(loc), hasAssignment(loc), width, sb.append(' '));
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
    int size = Math.max(1, nums.size()) + (assigned ? 1 : 0);
    append(' ', (width - size) / 2, sb);
    if (nums.isEmpty()) {
      sb.append('?');
    } else {
      for (Numeral num : nums)
        sb.append(num.number);
      if (assigned) {
        sb.append('!');
      }
    }
    return append(' ', width - size - (width - size) / 2, sb);
  }

  private StringBuilder append(char c, int count, StringBuilder sb) {
    while (count-- > 0)
      sb.append(c);
    return sb;
  }
}
