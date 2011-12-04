package us.blanshard.sudoku.core;

import static org.junit.Assert.assertEquals;
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

    marks = new Marks(builder.build());
  }

  @Test public void get() {
    assertEquals(set(), marks.get(loc(4, 1)));
    assertEquals(set(3, 5, 8), marks.get(loc(4, 2)));
  }

  @Test public void equals() {
    Marks m2 = new Marks(builder.build());
    builder.remove(loc(6, 3));
    Marks m3 = new Marks(builder.build());

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
    int start = 0;
    for (Row row : Row.ALL) {
      int index = start;
      for (Location loc : row) {
        assertEquals(true, builder.assign(loc, Numeral.ofIndex(index % 9)));
        ++index;
      }
      start = start + 3 + (row.number % 3 == 0 ? 1 : 0);
    }

    Marks marks = builder.build();
    Grid grid = marks.asGrid();
    String s = "123456789456789123789123456234567891567891234891234567345678912678912345912345678";
    assertEquals(s, grid.toFlatString());
    assertEquals(grid.toString(), marks.toString());
  }
}
