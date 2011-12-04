package us.blanshard.sudoku.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One row of a Sudoku grid, numbered from 1 to 9 left to right.
 *
 * @author Luke Blanshard
 */
public final class Row extends Unit {

  /** The row number, in the range 1..9. */
  public final int number;

  /** The row index, one less than the row number. */
  public final int index;

  public static Row of(int number) {
    return instances[number - 1];
  }

  public static Row ofIndex(int index) {
    return instances[index];
  }

  /** All the rows. */
  public static final List<Row> ALL;

  @Override public int unitIndex() {
    return 0 + index;
  }

  @Override public boolean contains(Location loc) {
    return loc.index / 9 == index;
  }

  @Override public Type getType() {
    return Type.ROW;
  }

  @Override public String toString() {
    return "R" + number;
  }

  private Row(int index) {
    this.index = index;
    this.number = index + 1;
    for (int i = 0; i < 9; ++i) {
      this.locations[i] = (byte) (index * 9 + i);
    }
  }

  private static final Row[] instances;
  static {
    instances = new Row[9];
    for (int i = 0; i < 9; ++i) {
      instances[i] = new Row(i);
    }
    ALL = Collections.unmodifiableList(Arrays.asList(instances));
  }
}
