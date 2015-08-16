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
package us.blanshard.sudoku.gen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.core.Solver.Result;
import us.blanshard.sudoku.core.SolverMarks;

import com.google.common.collect.Sets;

import org.junit.Test;

import java.util.Random;

public class GenerationStrategyTest {

  private final Random random = new Random(0);

  private Grid generateSimple(Symmetry sym) {
    return GenerationStrategy.SIMPLE.generate(random, sym);
  }

  @Test public void random() {
    ensureBasicProperties(generateSimple(Symmetry.RANDOM));
  }

  @Test public void classic() {
    Grid grid = generateSimple(Symmetry.CLASSIC);
    ensureBasicProperties(grid);
    for (Location loc : Location.all()) {
      assertEquals(grid.containsKey(loc), grid.containsKey(Location.of(80 - loc.index)));
    }
  }

  @Test public void mirror() {
    Grid grid = generateSimple(Symmetry.MIRROR);
    ensureBasicProperties(grid);
    for (Location loc : Location.all()) {
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices(loc.row.index, 9 - loc.column.number)));
    }
  }

  @Test public void doubleMirror() {
    Grid grid = generateSimple(Symmetry.DOUBLE_MIRROR);
    ensureBasicProperties(grid);
    for (Location loc : Location.all()) {
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices(loc.row.index, 9 - loc.column.number)));
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices(9 - loc.row.number, loc.column.index)));
    }
  }

  @Test public void diagonal() {
    Grid grid = generateSimple(Symmetry.DIAGONAL);
    ensureBasicProperties(grid);
    for (Location loc : Location.all()) {
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices(loc.column.index, loc.row.index)));
    }
  }

  @Test public void rotational() {
    Grid grid = generateSimple(Symmetry.ROTATIONAL);
    ensureBasicProperties(grid);
    for (Location loc : Location.all()) {
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices(loc.column.index, 8 - loc.row.index)));
    }
  }

  @Test public void blockwise() {
    Grid grid = generateSimple(Symmetry.BLOCKWISE);
    ensureBasicProperties(grid);
    for (Location loc : Location.all()) {
      assertEquals(
          grid.containsKey(loc),
          grid.containsKey(Location.ofIndices((loc.row.index + 3) % 9,
                                              (loc.column.index + 3) % 9) ));
    }
  }

  @Test public void strategies() {
    for (GenerationStrategy generator : GenerationStrategy.values()) {
      Symmetry symmetry = Symmetry.choose(random);
      Grid grid = generator.generate(random, symmetry);
      ensureBasicProperties(grid);
      if (generator.honorsSymmetry())
        assertTrue(symmetry.describes(grid));
    }
  }

  @Test public void improper() {
    for (GenerationStrategy generator : GenerationStrategy.values()) {
      Symmetry symmetry = Symmetry.choose(random);
      Result result = generator.generate(random, symmetry, 5, 7);
      if (generator.honorsSymmetry())
        assertTrue(symmetry.describes(result.start));
      assertEquals(true, result.numSolutions >= 1 && result.numSolutions <= 5);
      assertEquals(true, (Location.COUNT - result.intersection.size()) <= 7);
    }
  }

  private void ensureBasicProperties(Grid grid) {
    assertEquals(true, grid.size() >= 17);
    assertEquals(true, Sets.newHashSet(grid.values()).size() >= 8);
    assertEquals(true, SolverMarks.builder().assignAllRecursively(grid));
    assertEquals(1, Solver.solve(grid, random).numSolutions);
  }
}
