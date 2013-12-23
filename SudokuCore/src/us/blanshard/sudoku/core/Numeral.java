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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * A number from one to nine: the contents of a Sudoku square.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class Numeral implements Comparable<Numeral> {
  /** The number of Numerals. */
  public static final int COUNT = 9;

  /** The number, in the range 1..9. */
  public final int number;

  /** The index, one less than the number. */
  public final int index;

  /** The bit corresponding to the number, 1 &lt;&lt; index. */
  public final short bit;

  public static Numeral of(int number) {
    return instances[number - 1];
  }

  public static Numeral ofIndex(int index) {
    return instances[index];
  }

  /** Converts 0 to null, 1-9 to the corresponding numeral. */
  public static Numeral numeral(int number) {
    return number == 0 ? null : of(number);
  }

  /** Converts null to 0, non-null to the corresponding number. */
  public static int number(Numeral num) {
    return num == null ? 0 : num.number;
  }

  public NumSet asSet() {
    return NumSet.ofBits(bit);
  }

  /** All the numerals. */
  public static final List<Numeral> ALL;

  @Override public int compareTo(Numeral that) {
    return this.index - that.index;
  }

  @Override public String toString() {
    return Integer.toString(number);
  }

  @Override public boolean equals(Object o) {
    return this == o;
  }

  @Override public int hashCode() {
    return number;  // Relied upon by NumSet
  }

  private Numeral(int index) {
    this.index = index;
    this.number = index + 1;
    this.bit = (short) (1 << index);
  }

  private static final Numeral[] instances;
  static {
    instances = new Numeral[9];
    for (int i = 0; i < 9; ++i) {
      instances[i] = new Numeral(i);
    }
    ALL = Collections.unmodifiableList(Arrays.asList(instances));
  }
}
