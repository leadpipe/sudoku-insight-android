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
package us.blanshard.sudoku.core;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable set of Locations, always a subset of the locations in a
 * particular unit.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class UnitSubset extends AbstractSet<Location> implements Set<Location> {

  /** The unit this is a subset of. */
  public final Unit unit;

  /** The locations in this set expressed as a bit set. */
  public final short bits;

  /** The bit pattern for a set containing every location of a unit. */
  public static final int ALL_BITS = NumSet.ALL_BITS;

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

  /** Returns the size of the set implied by the given bits. */
  public static int bitsSize(int bits) {
    return NumSet.ofBits(bits).size();
  }

  /** Returns the set containing the given locations within the given unit. */
  public static UnitSubset of(Unit unit, Location... locs) {
    short bits = 0;
    for (Location loc : locs) {
      UnitSubset singleton = loc.unitSubsets.get(unit.getType());
      checkArgument(singleton.unit == unit);
      bits |= singleton.bits;
    }
    return new UnitSubset(unit, bits);
  }

  /** Returns the singleton set containing the given location within the given unit. */
  public static UnitSubset singleton(Unit unit, Location loc) {
    for (int i = 0; i < 9; ++i)
      if (unit.locations[i] == loc.index)
        return ofBits(unit, 1 << i);
    throw new IllegalArgumentException("Location " + loc + " is not in unit " + unit);
  }

  /** Returns the union of this set with the singleton containing the given location. */
  public UnitSubset with(Location loc) {
    return or(loc.unitSubsets.get(unit.getType()));
  }

  /** Returns the difference of this set and the singleton containing the given location. */
  public UnitSubset without(Location loc) {
    return minus(loc.unitSubsets.get(unit.getType()));
  }

  /** Returns the complement of this set. */
  public UnitSubset not() {
    return ofBits(unit, 511 & (~this.bits));
  }

  /** Returns the intersection of this set and another one. */
  public UnitSubset and(Iterable<Location> it) {
    UnitSubset that = sameUnit(it);
    if (that != null) return ofBits(unit, this.bits & that.bits);
    short bits = 0;
    for (Location loc : it) {
      UnitSubset singleton = loc.unitSubsets.get(unit.getType());
      if (singleton.unit == this.unit) bits |= singleton.bits;
    }
    return ofBits(unit, this.bits & bits);
  }

  /** Returns the union of this set and another one. */
  public UnitSubset or(Iterable<Location> it) {
    UnitSubset that = sameUnit(it);
    if (that != null) return ofBits(unit, this.bits | that.bits);
    short bits = this.bits;
    for (Location loc : it) {
      UnitSubset singleton = loc.unitSubsets.get(unit.getType());
      checkArgument(singleton.unit == unit);
      bits |= singleton.bits;
    }
    return ofBits(unit, bits);
  }

  /** Returns the symmetric difference of this set and another one. */
  public UnitSubset xor(Iterable<Location> it) {
    UnitSubset that = sameUnit(it);
    if (that != null) return ofBits(unit, this.bits ^ that.bits);
    short bits = this.bits;
    for (Location loc : it) {
      UnitSubset singleton = loc.unitSubsets.get(unit.getType());
      checkArgument(singleton.unit == unit);
      bits ^= singleton.bits;
    }
    return ofBits(unit, bits);
  }

  /** Returns the asymmetric difference of this set and another one. */
  public UnitSubset minus(Iterable<Location> it) {
    UnitSubset that = sameUnit(it);
    if (that != null) return ofBits(unit, this.bits & (~that.bits));
    short bits = 0;
    for (Location loc : it) {
      UnitSubset singleton = loc.unitSubsets.get(unit.getType());
      if (singleton.unit == this.unit) bits |= singleton.bits;
    }
    return ofBits(unit, this.bits & (~bits));
  }

  public boolean isSubsetOf(Iterable<Location> it) {
    return this.minus(it).isEmpty();
  }

  public boolean isSupersetOf(Iterable<Location> it) {
    UnitSubset that = sameUnit(it);
    if (that != null) return this.isSupersetOfBits(that.bits);
    for (Location loc : it)
      if (!this.contains(loc)) return false;
    return true;
  }

  public boolean isSupersetOfBits(int bits) {
    return (this.bits & bits) == bits;
  }

  public boolean contains(Location loc) {
    UnitSubset that = loc.unitSubsets.get(unit.getType());
    return this.unit == that.unit
        && (this.bits & that.bits) != 0;
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
      if (this.unit == that.unit) return this.bits == that.bits;
    }
    return super.equals(o);
  }

  @Override public int hashCode() {
    // Must match Set's contract, no shortcut to iterating and adding.
    return super.hashCode();
  }

  private UnitSubset sameUnit(Iterable<Location> it) {
    if (it instanceof UnitSubset) {
      UnitSubset that = (UnitSubset) it;
      if (that.unit == this.unit)
        return that;
    }
    return null;
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
