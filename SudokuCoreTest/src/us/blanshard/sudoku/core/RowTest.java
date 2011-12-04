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
