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
import java.util.Iterator;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable set of Numerals, usually used to keep track of the possible
 * values for a given Sudoku square.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class NumSet extends AbstractSet<Numeral> implements Set<Numeral> {

  /** The numerals in this set expressed as a bit set. */
  public final short bits;

  /** The bit pattern for a set containing every numeral. */
  public static final int ALL_BITS = (1 << 9) - 1;

  private final byte[] nums;

  private NumSet(short bits) {
    this.bits = bits;

    this.nums = new byte[Integer.bitCount(bits)];
    byte num = 1;
    int count = 0;
    for (int bit = 1; bit <= bits; bit = bit << 1, ++num) {
      if ((bits & bit) != 0) {
        nums[count++] = num;
      }
    }
  }

  /** Returns the set corresponding to the given bit set. */
  public static NumSet ofBits(int bits) {
    return instances[bits];
  }

  /** The empty NumSet. */
  public static final NumSet NONE;

  /** The full NumSet. */
  public static final NumSet ALL;

  /** Returns the set containing the given numerals. */
  public static NumSet of(Numeral... nums) {
    int bits = 0;
    for (Numeral n : nums)
      bits |= n.bit;
    return instances[bits];
  }

  /** Returns the union of this set and the singleton of the given numeral. */
  public NumSet with(Numeral num) {
    return instances[this.bits | num.bit];
  }

  /** Returns the difference of this set and the singleton of the given numeral. */
  public NumSet without(Numeral num) {
    return instances[this.bits & (~num.bit)];
  }

  /** Returns the complement of this set. */
  public NumSet not() {
    return instances[ALL_BITS & (~this.bits)];
  }

  /** Returns the intersection of this set and another one. */
  public NumSet and(NumSet that) {
    return instances[this.bits & that.bits];
  }

  /** Returns the union of this set and another one. */
  public NumSet or(NumSet that) {
    return instances[this.bits | that.bits];
  }

  /** Returns the symmetric difference of this set and another one. */
  public NumSet xor(NumSet that) {
    return instances[this.bits ^ that.bits];
  }

  /** Returns the asymmetric difference of this set and another one. */
  public NumSet minus(NumSet that) {
    return instances[this.bits & (~that.bits)];
  }

  public boolean isSubsetOf(NumSet that) {
    return (this.bits & that.bits) == this.bits;
  }

  public boolean isSupersetOf(NumSet that) {
    return (this.bits & that.bits) == that.bits;
  }

  public boolean contains(Numeral num) {
    return num != null && (bits & num.bit) != 0;
  }

  @Override public boolean contains(Object o) {
    if (o instanceof Numeral) {
      return contains((Numeral) o);
    }
    return false;
  }

  /** Returns the numeral at the given index within this set. */
  public Numeral get(int index) {
    return Numeral.of(nums[index]);
  }

  @Override public Iterator<Numeral> iterator() {
    return new Iter();
  }

  @Override public int size() {
    return nums.length;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof NumSet) return bits == ((NumSet) o).bits;
    return super.equals(o);
  }

  @Override public int hashCode() {
    // Must match Set's contract.
    int answer = 0;
    for (byte num : nums) answer += num;
    return answer;
  }

  private class Iter implements Iterator<Numeral> {
    private int nextIndex;

    @Override public boolean hasNext() {
      return nextIndex < nums.length;
    }

    @Override public Numeral next() {
      return Numeral.of(nums[nextIndex++]);
    }

    @Override public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private static final NumSet[] instances;
  static {
    instances = new NumSet[ALL_BITS + 1];
    for (short i = 0; i < instances.length; ++i) {
      instances[i] = new NumSet(i);
    }
    NONE = ofBits(0);
    ALL = NONE.not();
  }
}
