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
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class BlockTest {

  @Test public void all() {
    assertEquals(9, Block.ALL.size());
    int index = 0;
    for (Block block : Block.ALL) {
      assertEquals(index, block.index);
      assertSame(block, Block.ofIndex(index));
      ++index;
      assertEquals(index, block.number);
      assertSame(block, Block.of(index));
      int count = 0;
      for (Location loc : block) {
        assertSame(block, loc.block);
        assertEquals(true, block.contains(loc));
        ++count;
      }
      assertEquals(9, count);
    }
  }

  @Test public void contains() {
    int count = 0;
    for (Location loc : Location.ALL) {
      ++count;
      for (Block block : Block.ALL) {
        assertEquals(block == loc.block, block.contains(loc));
      }
    }
    assertEquals(Location.COUNT, count);
    assertEquals(81, count);
  }

  @Test public void string() {
    for (Block block : Block.ALL)
      assertEquals("B" + block.number, block.toString());
  }
}
