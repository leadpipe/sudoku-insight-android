package us.blanshard.sudoku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Location;

public class ColumnTest {

  @Test public void all() {
    assertEquals(9, Column.ALL.size());
    int index = 0;
    for (Column column : Column.ALL) {
      assertEquals(index, column.index);
      assertSame(column, Column.ofIndex(index));
      ++index;
      assertEquals(index, column.number);
      assertSame(column, Column.of(index));
      int count = 0;
      for (Location loc : column) {
        assertSame(column, loc.column);
        assertEquals(true, column.contains(loc));
        ++count;
      }
      assertEquals(9, count);
    }
  }

  @Test public void contains() {
    int count = 0;
    for (Location loc : Location.ALL) {
      ++count;
      for (Column column : Column.ALL) {
        assertEquals(column == loc.column, column.contains(loc));
      }
    }
    assertEquals(Location.COUNT, count);
    assertEquals(81, count);
  }

  @Test public void string() {
    for (Column column : Column.ALL)
      assertEquals("C" + column.number, column.toString());
  }
}
