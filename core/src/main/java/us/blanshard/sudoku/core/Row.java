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
 * One row of a Sudoku grid, numbered from 1 to 9 left to right.
 *
 * @author Luke Blanshard
 */
@Immutable
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
  public static final List<Row> all() {
    return ALL;
  }

  @Override public String toString() {
    return "R" + number;
  }

  @Override protected int getOverlappingBits(Unit that) {
    switch (that.type) {
      case ROW:
        return this == that ? UnitSubset.ALL_BITS : 0;
      case BLOCK: {
        Block block = (Block) that;
        if (block.rowIndex() != index / 3) return 0;
        return blockBits[block.columnIndex()];
      }
      case COLUMN: {
        Column col = (Column) that;
        return 1 << col.index;
      }
      default: throw new IllegalArgumentException();
    }
  }

  private Row(int index) {
    super(Type.ROW, index);
    this.index = index;
    this.number = index + 1;
    for (int i = 0; i < 9; ++i) {
      this.locations[i] = (byte) (index * 9 + i);
    }
  }

  private static final int[] blockBits = {0007, 0070, 0700};
  private static final Row[] instances;
  private static final List<Row> ALL;
  static {
    instances = new Row[9];
    for (int i = 0; i < 9; ++i) {
      instances[i] = new Row(i);
    }
    ALL = Collections.unmodifiableList(Arrays.asList(instances));
  }
}
