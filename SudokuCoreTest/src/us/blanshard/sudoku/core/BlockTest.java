package us.blanshard.sudoku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Location;

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
