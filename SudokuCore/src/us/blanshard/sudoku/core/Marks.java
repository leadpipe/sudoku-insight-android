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
 * @author Luke Blanshard
 */
public /*final*/ class Marks {

  /*private*/ final short[] bits;
  private static final short ALL_BITS = (1 << 9) - 1;

  private Marks(short[] bits) {
    this.bits = bits;
  }

  /**
   * Creates a Marks for the given grid.  Only the local constraints for each
   * location are applied: the result is likely to be less complete than passing
   * the grid to the builder.
   */
  public Marks(Grid grid) {
    this(new short[81]);
    for (Location loc : Location.ALL) {
      if (grid.containsKey(loc)) bits[loc.index] = grid.get(loc).bit;
      else bits[loc.index] = ALL_BITS;
    }
    for (Unit unit : Unit.allUnits()) {
      short missing = unit.getMissingBits(grid);
      for (Location loc : unit) {
        bits[loc.index] &= missing;
      }
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder asBuilder() {
    return new Builder(this);
  }

  public Grid asGrid() {
    Grid.Builder builder = Grid.builder();
    for (Location loc : Location.ALL) {
      NumSet possible = get(loc);
      if (possible.size() == 1)
        builder.put(loc, possible.iterator().next());
    }
    return builder.build();
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
  public UnitSubset get(Unit unit, Numeral num) {
    int bit = 1, bits = 0;
    for (Location loc : unit) {
      if (get(loc).contains(num))
        bits |= bit;
      bit <<= 1;
    }
    return UnitSubset.ofBits(unit, bits);
  }

  public static /*final*/ class Builder {
    /*private*/ Marks marks;
    /*private*/ boolean built;

    private Builder() {
      short[] bits = new short[81];
      Arrays.fill(bits, ALL_BITS);
      this.marks = new Marks(bits);
      this.built = false;
    }

    private Builder(Marks marks) {
      this.marks = marks;
      this.built = true;
    }

    /*private*/ Marks marks() {
      if (built) {
        Marks marks = new Marks(this.marks.bits.clone());
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
      return marks.asGrid();
    }

    public NumSet get(Location loc) {
      return marks.get(loc);
    }

    public UnitSubset get(Unit unit, Numeral num) {
      return marks.get(unit, num);
    }

    /**
     * Assigns all the associated locations and numerals in the given map (note
     * that {@link Grid} is this kind of map), returns true if they could all be
     * assigned.
     */
    public boolean assignAll(Map<Location, Numeral> grid) {
      for (Map.Entry<Location, Numeral> entry : grid.entrySet()) {
        if (!assign(entry.getKey(), entry.getValue()))
          return false;
      }
      return true;
    }

    /**
     * Assigns the given numeral to the given location, returns true if all the
     * ramifications of that assignment are consistent with the rules of Sudoku.
     */
    public boolean assign(Location loc, Numeral num) {
      NumSet others = get(loc).minus(NumSet.of(num));
      for (Numeral other : others)
        if (!eliminate(loc, other))
          return false;
      return marks.bits[loc.index] == num.bit;
    }

    /**
     * Eliminates the given numeral as a possible assignment to the given
     * location, returns true if all the ramifications of that elimination are
     * consistent with the rules of Sudoku.
     */
    public boolean eliminate(Location loc, Numeral num) {
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
          if (!eliminate(peer, last))
            return false;
      }

      // Look in all units this location belongs to, to see if there's only a
      // single remaining slot in the unit for this numeral, and if so, assign
      // it there.
      for (UnitSubset unitSubset : loc.unitSubsets.values()) {
        if (!eliminateFromUnit(num, unitSubset))
          return false;
      }

      return true;
    }

    /**
     * Eliminates the given numeral from the given unit subset's unit, return
     * false if a contradiction is found.  The given subset is guaranteed to be
     * a singleton.
     */
    protected boolean eliminateFromUnit(Numeral num, UnitSubset unitSubset) {
      Location possible = null;
      for (Location unitLoc : unitSubset.unit) {
        if (get(unitLoc).contains(num)) {
          if (possible != null)
            return true;  // Two or more possibilities for num in this unit; try the next unit.
          possible = unitLoc;
        }
      }
      if (possible == null)
        return false;  // This unit has no place for this numeral.
      if (!assign(possible, num))
        return false;  // Assigning the numeral to the only possible location in this unit failed.
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

  /**
   * A subclass that maintains the unit-numeral assignment points in addition to
   * the location ones.
   */
  public static class Fat extends Marks {
    private final short[] unitBits;

    private Fat(short[] bits, short[] unitBits) {
      super(bits);
      this.unitBits = unitBits;
    }

    public static Builder builder() {
      return new Builder();
    }

    @Override public Builder asBuilder() {
      return new Builder(this);
    }

    @Override public UnitSubset get(Unit unit, Numeral num) {
      return UnitSubset.ofBits(unit, unitBits[unit.unitIndex() * 9 + num.index]);
    }

    public static class Builder extends Marks.Builder {
      private Fat fatMarks;

      public Builder() {
        super(null);
        short[] bits = new short[81];
        Arrays.fill(bits, ALL_BITS);
        short[] unitBits = new short[Unit.COUNT * 9];
        Arrays.fill(unitBits, ALL_BITS);
        this.fatMarks = new Fat(bits, unitBits);
        this.marks = this.fatMarks;
        this.built = false;
      }

      private Builder(Fat fatMarks) {
        super(fatMarks);
        this.fatMarks = fatMarks;
      }

      @Override Marks marks() {
        if (built) {
          Fat fatMarks = new Fat(this.fatMarks.bits.clone(), this.fatMarks.unitBits.clone());
          this.fatMarks = fatMarks;
          this.marks = fatMarks;
          this.built = false;
        }
        return this.fatMarks;
      }

      @Override protected boolean eliminateFromUnit(Numeral num, UnitSubset unitSubset) {
        // Remove this location from the possible locations within this unit
        // that this numeral may be assigned.
        fatMarks.unitBits[unitSubset.unit.unitIndex() * 9 + num.index] &= ~unitSubset.bits;

        UnitSubset remaining = get(unitSubset.unit, num);
        if (remaining.size() == 0)
          return false;  // no possibilities left in this assignment set

        if (remaining.size() == 1  // Last possibility left.  Assign it.
            && !assign(remaining.iterator().next(), num))
          return false;

        return true;
      }
    }
  }
}
