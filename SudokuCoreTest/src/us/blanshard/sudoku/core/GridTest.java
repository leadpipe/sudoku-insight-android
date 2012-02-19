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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

public class GridTest {

  @Test public void clear() {
    Grid.Builder builder = Grid.builder()
        .put(Location.of(34), Numeral.of(6));
    assertEquals(1, builder.size());
    builder.clear();
    assertEquals(0, builder.size());
    assertEquals(Grid.BLANK, builder.build());
  }

  @Test public void build() {
    Grid.Builder builder = Grid.builder()
        .put(Location.of(34), Numeral.of(6));
    Grid g1 = builder.build();
    builder.put(Location.of(77), Numeral.of(1));
    Grid g2 = builder.build();
    builder.remove(Location.of(77));
    Grid g3 = builder.build();
    assertEquals(g1, g3);
    assertEquals(g3, g1);
    assertEquals(false, g1.equals(g2));
    assertEquals(1, g1.size());
    assertEquals(2, g2.size());
  }

  @Test public void containsKey() {
    Grid.Builder builder = Grid.builder();
    assertEquals(false, builder.containsKey(Location.of(23)));
    builder.put(Location.of(23), Numeral.of(4));
    assertEquals(true, builder.containsKey(Location.of(23)));
    assertEquals(true, builder.build().containsKey(Location.of(23)));
    assertEquals(false, builder.build().containsKey(builder));
  }

  @Test public void containsValue() {
    Grid.Builder builder = Grid.builder();
    assertEquals(false, builder.build().containsValue(Numeral.of(4)));
    builder.put(Location.of(23), Numeral.of(4));
    assertEquals(true, builder.build().containsValue(Numeral.of(4)));
    assertEquals(false, builder.build().containsValue(builder));
  }

  @Test public void equals() {
    Grid.Builder builder = Grid.builder()
        .put(Location.of(7), Numeral.of(1));
    Grid g1 = builder.build();
    builder.remove(Location.of(0));
    Grid g2 = builder.build();
    assertEquals(false, g1 == g2);
    assertEquals(g1, g2);
    assertEquals(g2, g1);
    assertEquals(g2.hashCode(), g1.hashCode());
    assertEquals(1, g2.size());
  }

  @Test public void get() {
    Grid.Builder builder = Grid.builder()
        .put(Location.of(7), Numeral.of(1));
    assertSame(Numeral.of(1), builder.get(Location.of(7)));
    assertNull(builder.get(Location.of(0)));
  }

  @Test public void remove() {
    Grid.Builder builder = Grid.builder();
    assertEquals(0, builder.size());
    assertEquals(true, builder.build().isEmpty());
    builder.put(Location.of(55), Numeral.of(8));
    assertEquals(1, builder.size());
    assertEquals(false, builder.build().isEmpty());

    builder.remove(Location.of(0));
    assertEquals(1, builder.size());

    builder.remove(Location.of(55));
    assertEquals(0, builder.size());
  }

  @Test public void views() {
    Grid grid = Grid.builder()
        .put(Location.of(10), Numeral.of(1))
        .put(Location.of(20), Numeral.of(2))
        .put(Location.of(30), Numeral.of(3))
        .put(Location.of(40), Numeral.of(4))
        .put(Location.of(50), Numeral.of(5))
        .put(Location.of(60), Numeral.of(6))
        .put(Location.of(70), Numeral.of(7))
        .build();

    assertEquals(7, grid.size());
    assertEquals(7, grid.entrySet().size());
    assertEquals(7, grid.keySet().size());
    assertEquals(7, grid.values().size());

    Iterator<Entry<Location, Numeral>> it = grid.entrySet().iterator();
    for (int i = 1; i <= 7; ++i) {
      assertEquals(true, it.hasNext());
      Entry<Location, Numeral> e = it.next();
      assertEquals(10 * i, e.getKey().index);
      assertEquals(i, e.getValue().number);
    }
    assertEquals(false, it.hasNext());

    assertEquals(new HashSet<Location>(
        asList(Location.of(10), Location.of(20), Location.of(30), Location.of(40),
               Location.of(50), Location.of(60), Location.of(70))), grid.keySet());
    assertEquals(
        asList(Numeral.of(1), Numeral.of(2), Numeral.of(3), Numeral.of(4), Numeral.of(5),
               Numeral.of(6), Numeral.of(7)), new ArrayList<Numeral>(grid.values()));
  }

  @Test public void strings() {
    String s = "...8.9..6.23.........6.8...7....1..2...45...9......6......7......1.46.....3......";
    Grid g = Grid.fromString(s);
    assertEquals(s, g.toFlatString());
    Grid g2 = Grid.fromString(g.toString());
    assertEquals(s, g2.toFlatString());
    assertEquals(g.toString(), g2.toString());
    assertEquals(g, g2);
  }

  @Test public void getBrokenLocations() {
    // given
    Grid grid = Grid.builder()
      .put(Location.of(1, 1), Numeral.of(1))
      .put(Location.of(1, 9), Numeral.of(1))
      .put(Location.of(9, 1), Numeral.of(1))
      .put(Location.of(7, 3), Numeral.of(1))
      .build();

    // then
    assertEquals(grid.keySet(), grid.getBrokenLocations());
  }
}
