package us.blanshard.sudoku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static us.blanshard.sudoku.core.NumSetTest.set;

import org.junit.Test;

import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.Unit;

public class UnitTest {  // What're the odds?

  public static Location loc(int row, int col) {
    return Location.of(Row.of(row), Column.of(col));
  }

  @Test public void allUnits() {
    assertEquals(27, Unit.COUNT);
    assertEquals(27, Unit.allUnits().size());
    int count = 0;
    for (Unit unit : Unit.allUnits()) {
      assertEquals(count, unit.unitIndex());
      ++count;
    }
    assertEquals(Unit.COUNT, count);
  }

  @Test public void ofIndex() {
    assertSame(Row.ofIndex(3), Unit.ofIndex(3));
    assertSame(Column.ofIndex(3), Unit.ofIndex(12));
    assertSame(Block.ofIndex(3), Unit.ofIndex(21));
  }

  @Test public void getConflicts() {
    Grid grid = Grid.builder()
        .put(loc(4, 1), Numeral.of(7))
        .put(loc(6, 3), Numeral.of(7))
        .put(loc(4, 9), Numeral.of(2))
        .put(loc(3, 9), Numeral.of(2))
        .put(loc(4, 4), Numeral.of(2))
        .put(loc(4, 5), Numeral.of(7))
        .build();

    assertEquals(set(), Row.of(3).getConflicts(grid));
    assertEquals(set(2, 7), Row.of(4).getConflicts(grid));
    assertEquals(set(), Column.of(1).getConflicts(grid));
    assertEquals(set(2), Column.of(9).getConflicts(grid));
    assertEquals(set(), Block.of(5).getConflicts(grid));
    assertEquals(set(7), Block.of(4).getConflicts(grid));
  }

  @Test public void getMissing() {
    Grid grid = Grid.builder()
        .put(loc(4, 1), Numeral.of(7))
        .put(loc(6, 3), Numeral.of(6))
        .put(loc(4, 9), Numeral.of(2))
        .put(loc(3, 9), Numeral.of(4))
        .put(loc(4, 4), Numeral.of(1))
        .put(loc(4, 5), Numeral.of(9))
        .build();

    assertEquals(set(1, 2, 3, 5, 6, 7, 8, 9), Row.of(3).getMissing(grid));
    assertEquals(set(3, 4, 5, 6, 8), Row.of(4).getMissing(grid));
    assertEquals(set(1, 2, 3, 4, 5, 6, 8, 9), Column.of(1).getMissing(grid));
    assertEquals(set(1, 3, 5, 6, 7, 8, 9), Column.of(9).getMissing(grid));
    assertEquals(set(1, 2, 3, 4, 5, 6, 7, 8, 9), Block.of(9).getMissing(grid));
    assertEquals(set(1, 2, 3, 4, 5, 8, 9), Block.of(4).getMissing(grid));
  }
}
