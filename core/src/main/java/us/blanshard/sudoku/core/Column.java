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
 * One column of a Sudoku grid, numbered from 1 to 9 top to bottom.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class Column extends Unit {

  /** The column number, in the range 1..9. */
  public final int number;

  /** The column index, one less than the column number. */
  public final int index;

  public static Column of(int number) {
    return instances[number - 1];
  }

  public static Column ofIndex(int index) {
    return instances[index];
  }

  /** All the columns. */
  public static final List<Column> all() {
    return ALL;
  }

  @Override public String toString() {
    return "C" + number;
  }

  @Override protected int getOverlappingBits(Unit that) {
    switch (that.type) {
      case COLUMN:
        return this == that ? UnitSubset.ALL_BITS : 0;
      case BLOCK: {
        Block block = (Block) that;
        if (block.columnIndex() != index / 3) return 0;
        return blockBits[block.rowIndex()];
      }
      case ROW: {
        Row row = (Row) that;
        return 1 << row.index;
      }
      default: throw new IllegalArgumentException();
    }
  }

  private Column(int index) {
    super(Type.COLUMN, index);
    this.index = index;
    this.number = index + 1;
    for (int i = 0; i < 9; ++i) {
      this.locations[i] = (byte) (index + i * 9);
    }
  }

  private static final int[] blockBits = {0007, 0070, 0700};
  private static final Column[] instances;
  private static final List<Column> ALL;
  static {
    instances = new Column[9];
    for (int i = 0; i < 9; ++i) {
      instances[i] = new Column(i);
    }
    ALL = Collections.unmodifiableList(Arrays.asList(instances));
  }
}