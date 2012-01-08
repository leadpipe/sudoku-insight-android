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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static us.blanshard.sudoku.core.NumSetTest.set;
import static us.blanshard.sudoku.core.UnitTest.loc;

import org.junit.Before;
import org.junit.Test;

public class MarksTest {

  private final Grid.Builder builder = Grid.builder();
  private Marks marks;

  @Before public void setUp() {
    builder.put(loc(1, 2), Numeral.of(4));
    builder.put(loc(4, 1), Numeral.of(7));
    builder.put(loc(6, 3), Numeral.of(6));
    builder.put(loc(4, 9), Numeral.of(2));
    builder.put(loc(3, 9), Numeral.of(4));
    builder.put(loc(4, 4), Numeral.of(1));
    builder.put(loc(4, 5), Numeral.of(9));

    marks = build();
  }

  private Marks build() {
    Marks.Builder b = Marks.builder();
    assertTrue(b.assignAll(builder.build()));
    return b.build();
  }

  @Test public void get() {
    assertEquals(set(7), marks.get(loc(4, 1)));
    assertEquals(set(3, 5, 8), marks.get(loc(4, 2)));
    assertEquals(UnitSubset.of(Row.of(4), loc(4, 1)), marks.get(Row.of(4), Numeral.of(7)));
    assertEquals(UnitSubset.of(Row.of(4), loc(4, 3), loc(4, 6), loc(4, 7), loc(4, 8)),
        marks.get(Row.of(4), Numeral.of(4)));
  }

  @Test public void assign_failure() {
    Marks.Builder builder = marks.asBuilder();
    assertEquals(false, builder.assign(loc(1, 2), Numeral.of(1)));
    assertEquals(0, builder.get(loc(1, 2)).size());
    assertEquals(0, builder.get(Block.of(1), Numeral.of(4)).size());
  }

  @Test public void equals() {
    Marks m2 = build();
    builder.remove(loc(6, 3));
    Marks m3 = build();

    assertEquals(marks, marks);
    assertEquals(marks, m2);
    assertEquals(m2, marks);
    assertEquals(marks.hashCode(), m2.hashCode());

    assertEquals(false, m2.equals(m3));
    assertEquals(false, m3.equals(m2));
    assertEquals(false, m2.hashCode() == m3.hashCode());
  }

  @Test public void strings() {
    Marks.Builder builder = Marks.builder();
    Marks.Builder builder2 = Marks.builder();
    int start = 0;
    for (Row row : Row.ALL) {
      int index = start;
      for (Location loc : row) {
        assertEquals(true, builder.assignRecursively(loc, Numeral.ofIndex(index % 9)));
        assertEquals(true, builder2.assign(loc, Numeral.ofIndex(index % 9)));
        ++index;
      }
      start = start + 3 + (row.number % 3 == 0 ? 1 : 0);
    }

    Marks marks = builder.build();
    Grid grid = marks.asGrid();
    String s = "123456789456789123789123456234567891567891234891234567345678912678912345912345678";
    assertEquals(s, grid.toFlatString());
    assertEquals(grid.toString(), marks.toString());
    assertEquals(marks, builder2.build());
    assertEquals(grid, builder2.asGrid());
  }
}
