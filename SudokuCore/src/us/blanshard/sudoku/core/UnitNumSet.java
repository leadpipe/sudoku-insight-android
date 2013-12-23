/*
Copyright 2013 Luke Blanshardopyright 2013 Luke Blanshard

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
 * A mutable set of UnitNumerals.
 *
 * @author Luke Blanshard
 */
@NotThreadSafe
public final class UnitNumSet extends AbstractSet<UnitNumeral> implements Cloneable {

  private long bits0, bits1, bits2, bits3;
  private int size;  // redundant

  public UnitNumSet() {}

  public UnitNumSet(Collection<UnitNumeral> unitNums) {
    addAll(unitNums);
  }

  public static UnitNumSet union(Collection<UnitNumeral> c1, Collection<UnitNumeral> c2) {
    UnitNumSet answer = new UnitNumSet(c1);
    answer.addAll(c2);
    return answer;
  }

  public static UnitNumSet intersect(Collection<UnitNumeral> c1, Collection<UnitNumeral> c2) {
    UnitNumSet answer = new UnitNumSet(c1);
    answer.retainAll(c2);
    return answer;
  }

  public static UnitNumSet subtract(Collection<UnitNumeral> c1, Collection<UnitNumeral> c2) {
    UnitNumSet answer = new UnitNumSet(c1);
    answer.removeAll(c2);
    return answer;
  }

  public static UnitNumSet all() {
    UnitNumSet answer = new UnitNumSet();
    answer.bits0 = -1;
    answer.bits1 = -1;
    answer.bits2 = -1;
    int diff = 256 - UnitNumeral.COUNT;
    answer.bits3 = (1L << (64 - diff)) - 1;
    answer.size = UnitNumeral.COUNT;
    return answer;
  }

  public static UnitNumSet of(Collection<? extends Unit> units, Numeral num) {
    UnitNumSet answer = new UnitNumSet();
    for (Unit unit : units)
      answer.add(UnitNumeral.of(unit, num));
    return answer;
  }

  public static UnitNumSet of(Unit unit, Collection<Numeral> nums) {
    UnitNumSet answer = new UnitNumSet();
    for (Numeral num : nums)
      answer.add(UnitNumeral.of(unit, num));
    return answer;
  }

  public UnitNumSet or(Collection<UnitNumeral> that) {
    return union(this, that);
  }

  public UnitNumSet and(Collection<UnitNumeral> that) {
    return intersect(this, that);
  }

  public UnitNumSet minus(Collection<UnitNumeral> that) {
    return subtract(this, that);
  }

  @Override public UnitNumSet clone() {
    try {
      return (UnitNumSet) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError(e);
    }
  }

  public boolean contains(UnitNumeral unitNum) {
    return (bits(unitNum) & bit(unitNum)) != 0;
  }

  @Override public boolean contains(Object o) {
    if (o instanceof UnitNumeral) {
      return contains((UnitNumeral) o);
    }
    return false;
  }

  @Override public Iterator<UnitNumeral> iterator() {
    return new Iter();
  }

  @Override public int size() {
    return size;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof UnitNumSet) {
      UnitNumSet that = (UnitNumSet) o;
      return this.bits0 == that.bits0
          && this.bits1 == that.bits1
          && this.bits2 == that.bits2
          && this.bits3 == that.bits3;
    }
    return super.equals(o);
  }

  @Override public int hashCode() {
    // Must match Set's contract.
    return super.hashCode();
  }

  @Override public boolean add(UnitNumeral unitNum) {
    long bits = bits(unitNum);
    long bit = bit(unitNum);
    boolean answer = (bits & bit) == 0;
    if (answer) {
      setBits(unitNum, bits | bit);
      ++size;
    }
    return answer;
  }

  public boolean remove(UnitNumeral unitNum) {
    long bits = bits(unitNum);
    long bit = bit(unitNum);
    boolean answer = (bits & bit) != 0;
    if (answer) {
      setBits(unitNum, bits & ~bit);
      --size;
    }
    return answer;
  }

  @Override public boolean remove(Object o) {
    if (o instanceof UnitNumeral) return remove((UnitNumeral) o);
    return false;
  }

  @Override public boolean containsAll(Collection<?> c) {
    if (!(c instanceof UnitNumSet)) return super.containsAll(c);
    UnitNumSet that = (UnitNumSet) c;
    return (~this.bits0 & that.bits0) == 0
        && (~this.bits1 & that.bits1) == 0
        && (~this.bits2 & that.bits2) == 0
        && (~this.bits3 & that.bits3) == 0;
  }

  @Override public boolean addAll(Collection<? extends UnitNumeral> c) {
    if (!(c instanceof UnitNumSet)) return super.addAll(c);
    UnitNumSet that = (UnitNumSet) c;
    this.bits0 |= that.bits0;
    this.bits1 |= that.bits1;
    this.bits2 |= that.bits2;
    this.bits3 |= that.bits3;
    return fixSize();
  }

  @Override public boolean removeAll(Collection<?> c) {
    if (!(c instanceof UnitNumSet)) return super.removeAll(c);
    UnitNumSet that = (UnitNumSet) c;
    this.bits0 &= ~that.bits0;
    this.bits1 &= ~that.bits1;
    this.bits2 &= ~that.bits2;
    this.bits3 &= ~that.bits3;
    return fixSize();
  }

  @Override public boolean retainAll(Collection<?> c) {
    if (!(c instanceof UnitNumSet)) return super.retainAll(c);
    UnitNumSet that = (UnitNumSet) c;
    this.bits0 &= that.bits0;
    this.bits1 &= that.bits1;
    this.bits2 &= that.bits2;
    this.bits3 &= that.bits3;
    return fixSize();
  }

  @Override public void clear() {
    bits0 = bits1 = bits2 = bits3 = 0;
    size = 0;
  }

  private long bits(UnitNumeral unitNum) {
    return bits(unitNum.index >> 6);
  }

  private long bits(int index) {
    switch (index) {
      case 0: return bits0;
      case 1: return bits1;
      case 2: return bits2;
      default: return bits3;
    }
  }

  private long bit(UnitNumeral unitNum) {
    return 1L << unitNum.index;  // << only uses bottom 6 bits
  }

  private void setBits(UnitNumeral unitNum, long bits) {
    switch (unitNum.index >> 6) {
      case 0: bits0 = bits; break;
      case 1: bits1 = bits; break;
      case 2: bits2 = bits; break;
      default: bits3 = bits; break;
    }
  }

  private boolean fixSize() {
    int size = Long.bitCount(bits0) + Long.bitCount(bits1) + Long.bitCount(bits2) + Long.bitCount(bits3);
    boolean answer = size != this.size;
    if (answer) this.size = size;
    return answer;
  }

  private class Iter implements Iterator<UnitNumeral> {
    private long remaining;
    private int numDone;
    private UnitNumeral last;

    Iter() {
      remaining = bits0;
    }

    @Override public boolean hasNext() {
      while (remaining == 0 && ++numDone < 4) {
        remaining = bits(numDone);
      }
      return remaining != 0;
    }

    @Override public UnitNumeral next() {
      if (!hasNext()) throw new NoSuchElementException();
      long nextBit = remaining & -remaining;
      remaining -= nextBit;
      return last = UnitNumeral.of(Long.numberOfTrailingZeros(nextBit) + (numDone << 6));
    }

    @Override public void remove() {
      if (last == null) throw new IllegalStateException();
      UnitNumSet.this.remove(last);
      last = null;
    }
  }
}
