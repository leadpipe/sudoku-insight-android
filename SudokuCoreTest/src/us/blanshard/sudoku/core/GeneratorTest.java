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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Sets;

import org.junit.Test;

import java.util.Random;

public class GeneratorTest {

  private final Random random = new Random(123);

  @Test public void random() {
    ensureBasicProperties(Generator.RANDOM.generate(random));
  }

  @Test public void classic() {
    Grid grid = Generator.CLASSIC.generate(random);
    ensureBasicProperties(grid);
    for (Location loc : Location.ALL) {
      assertEquals(grid.containsKey(loc), grid.containsKey(Location.of(80 - loc.index)));
    }
  }

  @Test public void mirror() {
    Grid grid = Generator.MIRROR.generate(random);
    ensureBasicProperties(grid);
    for (Location loc : Location.ALL) {
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices(loc.row.index, 9 - loc.column.number)));
    }
  }

  @Test public void doubleMirror() {
    Grid grid = Generator.DOUBLE_MIRROR.generate(random);
    ensureBasicProperties(grid);
    for (Location loc : Location.ALL) {
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices(loc.row.index, 9 - loc.column.number)));
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices(9 - loc.row.number, loc.column.index)));
    }
  }

  @Test public void diagonal() {
    Grid grid = Generator.DIAGONAL.generate(random);
    ensureBasicProperties(grid);
    for (Location loc : Location.ALL) {
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices(loc.column.index, loc.row.index)));
    }
  }

  @Test public void blockwise() {
    Grid grid = Generator.BLOCKWISE.generate(random);
    ensureBasicProperties(grid);
    for (Location loc : Location.ALL) {
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices((loc.row.index + 3) % 9,
                                              (loc.column.index + 3) % 9) ));
    }
  }

  private void ensureBasicProperties(Grid grid) {
    assertEquals(true, grid.size() >= 17);
    assertEquals(true, Sets.newHashSet(grid.values()).size() >= 8);
    assertEquals(true, Marks.builder().assignAll(grid));
  }
}
