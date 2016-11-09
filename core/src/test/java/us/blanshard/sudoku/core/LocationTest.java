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
    for (Location loc : Location.all())
      assertEquals("(" + loc.row.number + ", " + loc.column.number + ")", loc.toString());
  }

  @Test public void unitSubsets() {
    for (Location loc : Location.all()) {
      assertEquals(UnitSubset.singleton(loc.row, loc), loc.unitSubsets.get(Unit.Type.ROW));
      assertEquals(UnitSubset.singleton(loc.column, loc), loc.unitSubsets.get(Unit.Type.COLUMN));
      assertEquals(UnitSubset.singleton(loc.block, loc), loc.unitSubsets.get(Unit.Type.BLOCK));
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
    for (Location loc : Location.all()) {
      for (Location peer : loc.peers) {
        assertEquals(false, loc == peer);
        assertEquals(true, (loc.row == peer.row || loc.column == peer.column
                            || loc.block == peer.block));
      }
    }
  }

  @Test public void compare() {
    for (Location l1 : Location.all()) {
      for (Location l2 : Location.all()) {
        if (l1.index < l2.index) assertEquals(true, l1.compareTo(l2) < 0);
        else if (l1.index > l2.index) assertEquals(true, l1.compareTo(l2) > 0);
        else assertEquals(true, l1.compareTo(l2) == 0);
      }
    }
  }
}
