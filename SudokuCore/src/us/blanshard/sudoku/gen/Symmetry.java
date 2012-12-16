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
package us.blanshard.sudoku.gen;

import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.Unit;

import com.google.common.collect.Iterables;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

/**
 * Symmetries for the clues in Sudoku starting grids.
 *
 * @author Luke Blanshard
 */
public enum Symmetry {

  /** No pattern to the clues.  Actually a lack of symmetry. */
  RANDOM {
    @Override public Iterable<Location> expand(Location loc) {
      return Collections.singleton(loc);
    }
  },

  /** The classic Sudoku 180-degree rotational symmetry. */
  CLASSIC {
    @Override public Iterable<Location> expand(Location loc) {
      return loc.index == 40
        ? Collections.singleton(loc)
        : Arrays.asList(loc, Location.of(80 - loc.index));
    }
  },

  /** A left-right mirror symmetry. */
  MIRROR {
    @Override public Iterable<Location> expand(Location loc) {
      return loc.column.number == 5
        ? Collections.singleton(loc)
        : Arrays.asList(loc, Location.of(loc.row, Column.ofIndex(9 - loc.column.number)));
    }
  },

  /** Mirrors left-right and top-bottom. */
  DOUBLE_MIRROR {
    @Override public Iterable<Location> expand(Location loc) {
      if (loc.row.number == 5) return MIRROR.expand(loc);
      Location vert = Location.of(Row.ofIndex(9 - loc.row.number), loc.column);
      return Iterables.concat(MIRROR.expand(loc), MIRROR.expand(vert));
    }
  },

  /** A mirror symmetry across one of the main diagonals. */
  DIAGONAL {
    @Override public Iterable<Location> expand(Location loc) {
      return loc.column.number == loc.row.number
        ? Collections.singleton(loc)
        : Arrays.asList(loc, Location.of(loc.column.number, loc.row.number));
    }
  },

  /** A 90-degree rotational symmetry. */
  ROTATIONAL {
    @Override public Iterable<Location> expand(Location loc) {
      return loc.index == 40
        ? Collections.singleton(loc)
        : Arrays.asList(loc,
                        Location.of(loc.column.number, 10 - loc.row.number),
                        Location.of(10 - loc.row.number, 10 - loc.column.number),
                        Location.of(10 - loc.column.number, loc.row.number));
    }
  },

  /** Repeats locations in diagonally adjacent blocks. */
  BLOCKWISE {
    @Override public Iterable<Location> expand(Location loc) {
      int withinBlock = loc.unitSubsets.get(Unit.Type.BLOCK).getIndex(0);
      int row = loc.block.rowIndex();
      int col = loc.block.columnIndex();
      return Arrays.asList(loc,
                           Block.ofIndices((row + 1) % 3, (col + 1) % 3).get(withinBlock),
                           Block.ofIndices((row + 2) % 3, (col + 2) % 3).get(withinBlock));
    }
  };

  private static final Symmetry[] values = values();

  /**
   * Chooses one of the symmetries at random.
   */
  public static Symmetry choose(Random random) {
    return values[random.nextInt(values.length)];
  }

  /**
   * Chooses one of the symmetries at random, avoiding RANDOM.
   */
  public static Symmetry choosePleasing(Random random) {
    return values[1 + random.nextInt(values.length - 1)];
  }

  /**
   * Expands the given location into the set of locations that accompany it in
   * this symmetry's pattern. The given location is included in the resulting
   * set.
   */
  public abstract Iterable<Location> expand(Location loc);

  /**
   * Generates a simple puzzle.
   */
  public Grid generate(Random random) {
    return Generator.SIMPLE.generate(random, this);
  }
}
