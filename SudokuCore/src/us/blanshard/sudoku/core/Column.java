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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One column of a Sudoku grid, numbered from 1 to 9 top to bottom.
 *
 * @author Luke Blanshard
 */
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
  public static final List<Column> ALL;

  @Override public int unitIndex() {
    return 9 + index;
  }

  @Override public boolean contains(Location loc) {
    return loc.index % 9 == index;
  }

  @Override public Type getType() {
    return Type.COLUMN;
  }

  @Override public String toString() {
    return "C" + number;
  }

  private Column(int index) {
    this.index = index;
    this.number = index + 1;
    for (int i = 0; i < 9; ++i) {
      this.locations[i] = (byte) (index + i * 9);
    }
  }

  private static final Column[] instances;
  static {
    instances = new Column[9];
    for (int i = 0; i < 9; ++i) {
      instances[i] = new Column(i);
    }
    ALL = Collections.unmodifiableList(Arrays.asList(instances));
  }
}
