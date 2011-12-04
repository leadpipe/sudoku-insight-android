package us.blanshard.sudoku.core;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

/**
 * An immutable set of Locations, always a subset of the locations in a
 * particular unit.
 *
 * @author Luke Blanshard
 */
public final class UnitSubset extends AbstractSet<Location> implements Set<Location> {

  /** The unit this is a subset of. */
  public final Unit unit;

  /** The locations in this set expressed as a bit set. */
  public final short bits;

  /** We piggyback on NumSet's implementation of a canonical set-of-9-elements. */
  private final NumSet set;

  private UnitSubset(Unit unit, short bits) {
    this.unit = unit;
    this.bits = bits;
    this.set  = NumSet.ofBits(bits);
  }

  /** Returns the set corresponding to the given bit set within the given unit. */
  public static UnitSubset ofBits(Unit unit, int bits) {
    return new UnitSubset(unit, (short) bits);
  }

  /** Returns the set containing the given locations within the given unit. */
  public static UnitSubset of(Unit unit, Location... locs) {
    short bits = 0;
    for (Location loc : locs)
      bits |= bitFor(unit, loc);
    return new UnitSubset(unit, bits);
  }

  /** Returns the complement of this set. */
  public UnitSubset not() {
    return ofBits(unit, 511 & (~this.bits));
  }

  /** Returns the intersection of this set and another one. */
  public UnitSubset and(UnitSubset that) {
    return ofBits(unit, this.bits & that.bits);
  }

  /** Returns the union of this set and another one. */
  public UnitSubset or(UnitSubset that) {
    return ofBits(unit, this.bits | that.bits);
  }

  /** Returns the symmetric difference of this set and another one. */
  public UnitSubset xor(UnitSubset that) {
    return ofBits(unit, this.bits ^ that.bits);
  }

  /** Returns the asymmetric difference of this set and another one. */
  public UnitSubset minus(UnitSubset that) {
    return ofBits(unit, this.bits & (~that.bits));
  }

  public boolean contains(Location loc) {
    if (unit.contains(loc))
      return (bits & bitFor(unit, loc)) != 0;
    return false;
  }

  @Override public boolean contains(Object o) {
    if (o instanceof Location) {
      return contains((Location) o);
    }
    return false;
  }

  public Location get(int index) {
    return unit.get(getIndex(index));
  }

  public int getIndex(int index) {
    return set.get(index).index;
  }

  @Override public Iterator<Location> iterator() {
    return new Iter();
  }

  @Override public int size() {
    return set.size();
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof UnitSubset) {
      UnitSubset that = (UnitSubset) o;
      return this.unit == that.unit && this.bits == that.bits;
    }
    return super.equals(o);
  }

  @Override public int hashCode() {
    // Must match Set's contract, no shortcut to iterating and adding.
    return super.hashCode();
  }

  private static int bitFor(Unit unit, Location loc) {
    int index = unit.indexOf(loc);
    if (index < 0)
      throw new IllegalArgumentException("Location " + loc + " is not in unit " + unit);
    return 1 << index;
  }

  private class Iter implements Iterator<Location> {
    private Iterator<Numeral> setIter = set.iterator();

    @Override public boolean hasNext() {
      return setIter.hasNext();
    }

    @Override public Location next() {
      return unit.get(setIter.next().index);
    }

    @Override public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
