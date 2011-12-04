package us.blanshard.sudoku.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A number from one to nine: the contents of a Sudoku square.
 *
 * @author Luke Blanshard
 */
public final class Numeral implements Comparable<Numeral> {

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
