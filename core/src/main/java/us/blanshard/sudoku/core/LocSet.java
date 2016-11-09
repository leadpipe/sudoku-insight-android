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

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A mutable set of Locations.
 *
 * @author Luke Blanshard
 */
@NotThreadSafe
public final class LocSet extends AbstractSet<Location> implements Cloneable {

  private long bits0, bits1;
  private int size;  // redundant

  public LocSet() {}

  public LocSet(Collection<Location> locs) {
    addAll(locs);
  }

  public static LocSet union(Collection<Location> c1, Collection<Location> c2) {
    LocSet answer = new LocSet(c1);
    answer.addAll(c2);
    return answer;
  }

  public static LocSet intersect(Collection<Location> c1, Collection<Location> c2) {
    LocSet answer = new LocSet(c1);
    answer.retainAll(c2);
    return answer;
  }

  public static LocSet subtract(Collection<Location> c1, Collection<Location> c2) {
    LocSet answer = new LocSet(c1);
    answer.removeAll(c2);
    return answer;
  }

  public static LocSet all() {
    LocSet answer = new LocSet();
    answer.bits0 = -1;
    answer.bits1 = 0x1ffff;  // +17 bits
    answer.size = 81;
    return answer;
  }

  public LocSet or(Collection<Location> that) {
    return union(this, that);
  }

  public LocSet and(Collection<Location> that) {
    return intersect(this, that);
  }

  public LocSet minus(Collection<Location> that) {
    return subtract(this, that);
  }

  @Override public LocSet clone() {
    try {
      return (LocSet) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError(e);
    }
  }

  public boolean contains(Location loc) {
    return (bits(loc) & bit(loc)) != 0;
  }

  @Override public boolean contains(Object o) {
    if (o instanceof Location) {
      return contains((Location) o);
    }
    return false;
  }

  @Override public Iterator<Location> iterator() {
    return new Iter();
  }

  @Override public int size() {
    return size;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof LocSet) {
      LocSet that = (LocSet) o;
      return this.bits0 == that.bits0
          && this.bits1 == that.bits1;
    }
    return super.equals(o);
  }

  @Override public int hashCode() {
    // Must match Set's contract.
    return super.hashCode();
  }

  @Override public boolean add(Location loc) {
    long bits = bits(loc);
    long bit = bit(loc);
    boolean answer = (bits & bit) == 0;
    if (answer) {
      setBits(loc, bits | bit);
      ++size;
    }
    return answer;
  }

  public boolean remove(Location loc) {
    long bits = bits(loc);
    long bit = bit(loc);
    boolean answer = (bits & bit) != 0;
    if (answer) {
      setBits(loc, bits & ~bit);
      --size;
    }
    return answer;
  }

  @Override public boolean remove(Object o) {
    if (o instanceof Location) return remove((Location) o);
    return false;
  }

  @Override public boolean containsAll(Collection<?> c) {
    if (!(c instanceof LocSet)) return super.containsAll(c);
    LocSet that = (LocSet) c;
    return (~this.bits0 & that.bits0) == 0
        && (~this.bits1 & that.bits1) == 0;
  }

  @Override public boolean addAll(Collection<? extends Location> c) {
    if (!(c instanceof LocSet)) return super.addAll(c);
    LocSet that = (LocSet) c;
    this.bits0 |= that.bits0;
    this.bits1 |= that.bits1;
    return fixSize();
  }

  @Override public boolean removeAll(Collection<?> c) {
    if (!(c instanceof LocSet)) return super.removeAll(c);
    LocSet that = (LocSet) c;
    this.bits0 &= ~that.bits0;
    this.bits1 &= ~that.bits1;
    return fixSize();
  }

  @Override public boolean retainAll(Collection<?> c) {
    if (!(c instanceof LocSet)) return super.retainAll(c);
    LocSet that = (LocSet) c;
    this.bits0 &= that.bits0;
    this.bits1 &= that.bits1;
    return fixSize();
  }

  @Override public void clear() {
    bits0 = bits1 = 0;
    size = 0;
  }

  private long bits(Location loc) {
    return loc.index < 64 ? bits0 : bits1;
  }

  private long bit(Location loc) {
    return 1L << loc.index;  // << only uses bottom 6 bits
  }

  private void setBits(Location loc, long bits) {
    if (loc.index < 64) bits0 = bits;
    else bits1 = bits;
  }

  private boolean fixSize() {
    int size = Long.bitCount(bits0) + Long.bitCount(bits1);
    boolean answer = size != this.size;
    if (answer) this.size = size;
    return answer;
  }

  private class Iter implements Iterator<Location> {
    private long remaining;
    private boolean zeroDone;
    private Location last;

    Iter() {
      remaining = bits0;
    }

    @Override public boolean hasNext() {
      if (remaining == 0 && !zeroDone) {
        remaining = bits1;
        zeroDone = true;
      }
      return remaining != 0;
    }

    @Override public Location next() {
      if (!hasNext()) throw new NoSuchElementException();
      long nextBit = remaining & -remaining;
      remaining -= nextBit;
      return last = Location.of(Long.numberOfTrailingZeros(nextBit) + (zeroDone ? 64 : 0));
    }

    @Override public void remove() {
      if (last == null) throw new IllegalStateException();
      LocSet.this.remove(last);
      last = null;
    }
  }
}
