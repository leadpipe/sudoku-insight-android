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
import static us.blanshard.sudoku.core.NumSetTest.set;

import us.blanshard.sudoku.core.Unit.Type;

import org.junit.Test;

public class UnitTest {  // What're the odds?

  public static Location loc(int row, int col) {
    return Location.of(Row.of(row), Column.of(col));
  }

  @Test public void allUnits() {
    assertEquals(27, Unit.COUNT);
    assertEquals(27, Unit.allUnits().size());
    int count = 0;
    for (Unit unit : Unit.allUnits()) {
      assertEquals(count, unit.index);
      ++count;
    }
    assertEquals(Unit.COUNT, count);
  }

  @Test public void ofIndex() {
    assertSame(Block.ofIndex(3), Unit.ofIndex(3));
    assertSame(Row.ofIndex(3), Unit.ofIndex(12));
    assertSame(Column.ofIndex(3), Unit.ofIndex(21));
    for (Unit u : Unit.allUnits())
      assertSame(u, Unit.ofIndex(u.index));
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

  @Test public void intersectAndSubtract() {
    int blockOverlapCount = 0;
    for (Unit u1 : Unit.allUnits())
      for (Unit u2 : Unit.allUnits()) {
        UnitSubset i = u1.intersect(u2);
        assertEquals(i, u2.intersect(u1));
        assertEquals(LocSet.intersect(u1, u2), i);
        UnitSubset s1 = u1.subtract(u2);
        UnitSubset s2 = u2.subtract(u1);
        assertEquals(s1.size(), s2.size());
        assertEquals(LocSet.subtract(u1, u2), s1);
        assertEquals(LocSet.subtract(u2, u1), s2);
        if (u1 == u2) {
          assertEquals(9, i.size());
          assertEquals(0, s1.size());
        } else if (u1.type == u2.type) {
          assertEquals(0, i.size());
          assertEquals(9, s1.size());
        } else if (u1.type == Type.BLOCK || u2.type == Type.BLOCK) {
          if (i.isEmpty()) {
            assertEquals(9, s1.size());
          } else {
            ++blockOverlapCount;
            assertEquals(3, i.size());
            assertEquals(6, s1.size());
          }
        } else {
          assertEquals(1, i.size());
          assertEquals(8, s1.size());
          Location loc = i.get(0);
          assertEquals(loc.unit(u1.type), u1);
          assertEquals(loc.unit(u2.type), u2);
        }
      }
    assertEquals(2 * 9 * 6, blockOverlapCount);
  }

  @Test public void indexOf() {
    for (Unit u : Unit.allUnits())
      for (Location loc : Location.all()) {
        int i = u.indexOf(loc);
        assertEquals(i >= 0, loc.unit(u.type) == u);
        if (i >= 0) {
          assertEquals(loc, u.get(i));
        }
      }
  }
}
