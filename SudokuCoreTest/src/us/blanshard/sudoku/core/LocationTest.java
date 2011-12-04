package us.blanshard.sudoku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class LocationTest {

  @Test public void of() {
    assertEquals(81, Location.COUNT);
    for (int i = 0; i < Location.COUNT; ++i) {
      Location loc = Location.of(i);
      assertEquals(i, loc.index);
      assertSame(loc, Location.of(loc.row, loc.column));
    }
  }

  @Test public void string() {
    for (Location loc : Location.ALL)
      assertEquals("(" + loc.row.number + ", " + loc.column.number + ")", loc.toString());
  }

  @Test public void unitSubsets() {
    for (Location loc : Location.ALL) {
      assertEquals(UnitSubset.of(loc.row, loc), loc.unitSubsets.get(Unit.Type.ROW));
      assertEquals(UnitSubset.of(loc.column, loc), loc.unitSubsets.get(Unit.Type.COLUMN));
      assertEquals(UnitSubset.of(loc.block, loc), loc.unitSubsets.get(Unit.Type.BLOCK));
      for (UnitSubset subset : loc.unitSubsets.values()) {
        assertEquals(true, subset.contains(loc));
        assertEquals(1, subset.size());
        for (Location peer : loc.peers) {
          assertEquals(false, subset.contains(peer));
        }
      }
    }
  }

  @Test public void peers() {
    for (Location loc : Location.ALL) {
      for (Location peer : loc.peers) {
        assertEquals(false, loc == peer);
        assertEquals(true, (loc.row == peer.row || loc.column == peer.column
                            || loc.block == peer.block));
      }
    }
  }

  @Test public void compare() {
    for (Location l1 : Location.ALL) {
      for (Location l2 : Location.ALL) {
        if (l1.index < l2.index) assertEquals(true, l1.compareTo(l2) < 0);
        else if (l1.index > l2.index) assertEquals(true, l1.compareTo(l2) > 0);
        else assertEquals(true, l1.compareTo(l2) == 0);
      }
    }
  }
}
