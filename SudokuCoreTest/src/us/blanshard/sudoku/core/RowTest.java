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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Row;

public class RowTest {

  @Test public void all() {
    assertEquals(9, Row.ALL.size());
    int index = 0;
    for (Row row : Row.ALL) {
      assertEquals(index, row.index);
      assertSame(row, Row.ofIndex(index));
      ++index;
      assertEquals(index, row.number);
      assertSame(row, Row.of(index));
      int count = 0;
      for (Location loc : row) {
        assertSame(row, loc.row);
        assertEquals(true, row.contains(loc));
        ++count;
      }
      assertEquals(9, count);
    }
  }

  @Test public void contains() {
    int count = 0;
    for (Location loc : Location.ALL) {
      ++count;
      for (Row row : Row.ALL) {
        assertEquals(row == loc.row, row.contains(loc));
      }
    }
    assertEquals(Location.COUNT, count);
    assertEquals(81, count);
  }

  @Test public void string() {
    for (Row row : Row.ALL)
      assertEquals("R" + row.number, row.toString());
  }
}
