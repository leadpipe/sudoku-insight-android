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
package us.blanshard.sudoku.insight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static us.blanshard.sudoku.core.NumSetTest.set;
import static us.blanshard.sudoku.core.UnitTest.loc;

import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;

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
    b.assignAll(builder.build());
    assertFalse(b.hasErrors());
    return b.build();
  }

  @Test public void get() {
    assertEquals(set(7), marks.getSet(loc(4, 1)));
    assertEquals(set(3, 5, 8), marks.getSet(loc(4, 2)));
    assertEquals(UnitSubset.singleton(Row.of(4), loc(4, 1)), marks.getSet(UnitNumeral.of(Row.of(4), Numeral.of(7))));
    assertEquals(UnitSubset.ofBits(Row.of(4), 0xe4), marks.getSet(UnitNumeral.of(Row.of(4), Numeral.of(4))));
  }

  @Test public void assign_failure() {
    Marks.Builder builder = marks.toBuilder();
    builder.assign(loc(1, 2), Numeral.of(1));
    assertTrue(builder.hasErrors());
    assertEquals(0, builder.get(loc(1, 2)).size());
    assertEquals(0, builder.get(UnitNumeral.of(Block.of(1), Numeral.of(4))).size());
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
    String s =
        "  6!   37   1!  |  2    3    9   |  78   4    5  \n" +
        "  2!  379   8!  |  15   1    4   |  7    39   6  \n" +
        "  34  3459 345  |  7    6    8   |  19  139   2  \n" +
        "----------------+----------------+----------------\n" +
        "  5    6    4   |  9    47   1   |  3    2    8  \n" +
        "  8    12   9   |  6    57   23  |  4    15   7  \n" +
        "  34   12   7   |  8    45   23  | 159   6    19 \n" +
        "----------------+----------------+----------------\n" +
        "  9    3    6   |  1    2    7   |  58   8    4  \n" +
        "  7    45   45  |  3    8    6   |  2    19   19 \n" +
        "  1    8    2   |  4    9    5   |  6    7    3  \n";
    Marks marks = Marks.fromString(s);
    assertEquals(s, marks.toString());

    assertEquals("6.1......2.8.....................................................................",
        marks.toGrid().toFlatString());

    assertEquals(set(4,5), marks.getSet(Location.of(8,2)));
    assertEquals(set(3,8).bits, marks.getBits(UnitNumeral.of(Column.of(2), Numeral.of(4))));
    assertEquals(set(3,8).bits, marks.getBits(UnitNumeral.of(Column.of(2), Numeral.of(5))));


  }
}
