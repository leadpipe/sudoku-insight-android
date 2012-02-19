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

  /** Returns the singleton set containing the given location within the given unit. */
  public static UnitSubset singleton(Unit unit, Location loc) {
    return ofBits(unit, bitFor(unit, loc));
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
      if (this.unit == that.unit) return this.bits == that.bits;
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
