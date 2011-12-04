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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generates Sudoku starting grids.  They may not be solvable, or have unique
 * solutions.
 *
 * <p> The basic algorithm is due to Peter Norvig.
 *
 * @author Luke Blanshard
 */
public final class Generator {
  private Generator() {}

  /**
   * Don't stop working until at least this many locations have been assigned
   * values.
   */
  private static final int MIN_ASSIGNED = 17;

  public static Strategy chooseStrategy(Random random) {
    return Strategy.values()[random.nextInt(Strategy.values().length)];
  }

  public static Grid generate(Strategy strategy, Random random) {
    List<Location> locs = Lists.newArrayList(Location.ALL);
    Collections.shuffle(locs, random);
    Grid.Builder gridBuilder = Grid.builder();
    Marks.Builder marksBuilder = Marks.builder();
    int assigned = 0, index = 0;
    NumSet numerals = NumSet.of();  // The numerals assigned so far

    while (assigned < MIN_ASSIGNED || numerals.size() < 8) {
      Location nextRandom = locs.get(index++);
      for (Location loc : Iterables.concat(Collections.singleton(nextRandom),
                                           strategy.additional(nextRandom))) {
        if (gridBuilder.containsKey(loc)) continue;
        NumSet possible = marksBuilder.get(loc);
        Numeral num = possible.get(random.nextInt(possible.size()));
        if (!marksBuilder.assign(loc, num))
          return generate(strategy, random);  // Recurse: this attempt has failed.
        gridBuilder.put(loc, num);
        numerals = numerals.or(NumSet.of(num));
        ++assigned;
      }
    }

    return gridBuilder.build();
  }

  /**
   * Strategies for picking additional locations to fill after picking a first
   * location.
   */
  public enum Strategy {
    /** No pattern to the givens. */
    RANDOM {
      @Override Iterable<Location> additional(Location loc) {
        return Collections.emptyList();
      }
    },

    /** The classic Sudoku rotational symmetry. */
    CLASSIC {
      @Override Iterable<Location> additional(Location loc) {
        return loc.index == 40
          ? Collections.<Location>emptyList()
          : Collections.singleton(Location.of(80 - loc.index));
      }
    },

    /** A left-right mirror symmetry. */
    MIRROR {
      @Override Iterable<Location> additional(Location loc) {
        return loc.column.number == 5
          ? Collections.<Location>emptyList()
          : Collections.singleton(Location.of(loc.row, Column.ofIndex(9 - loc.column.number)));
      }
    },

    /** Mirrors left-right and top-bottom. */
    DOUBLE_MIRROR {
      @Override Iterable<Location> additional(Location loc) {
        if (loc.row.number == 5) return MIRROR.additional(loc);
        Location vert = Location.of(Row.ofIndex(9 - loc.row.number), loc.column);
        return Iterables.concat(
            MIRROR.additional(loc),
            Arrays.asList(vert),
            MIRROR.additional(vert));
      }
    },

    /** Repeats locations in diagonally adjacent blocks. */
    BLOCKWISE {
      @Override Iterable<Location> additional(Location loc) {
        int withinBlock = loc.unitSubsets.get(Unit.Type.BLOCK).getIndex(0);
        int row = loc.block.rowIndex();
        int col = loc.block.columnIndex();
        return Arrays.asList(Block.ofIndices((row + 1) % 3, (col + 1) % 3).get(withinBlock),
                             Block.ofIndices((row + 2) % 3, (col + 2) % 3).get(withinBlock));
      }
    };

    abstract Iterable<Location> additional(Location loc);
  }
}
