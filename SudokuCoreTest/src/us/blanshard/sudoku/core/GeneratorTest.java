package us.blanshard.sudoku.core;

import static org.junit.Assert.assertEquals;
import static us.blanshard.sudoku.core.Generator.generate;

import java.util.Random;

import org.junit.Test;

import us.blanshard.sudoku.core.Generator.Strategy;

import com.google.common.collect.Sets;

public class GeneratorTest {

  private final Random random = new Random(123);

  @Test public void random() {
    ensureBasicProperties(generate(Strategy.RANDOM, random));
  }

  @Test public void classic() {
    Grid grid = generate(Strategy.CLASSIC, random);
    ensureBasicProperties(grid);
    for (Location loc : Location.ALL) {
      assertEquals(grid.containsKey(loc), grid.containsKey(Location.of(80 - loc.index)));
    }
  }

  @Test public void mirror() {
    Grid grid = generate(Strategy.MIRROR, random);
    ensureBasicProperties(grid);
    for (Location loc : Location.ALL) {
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices(loc.row.index, 9 - loc.column.number)));
    }
  }

  @Test public void doubleMirror() {
    Grid grid = generate(Strategy.DOUBLE_MIRROR, random);
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

  @Test public void blockwise() {
    Grid grid = generate(Strategy.BLOCKWISE, random);
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
