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

import static com.google.common.base.Preconditions.checkArgument;

import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.Unit;

import com.google.common.collect.ImmutableMap;
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
    @Override public String getName() {
      return "none";
    }
  },

  /** The classic Sudoku 180-degree rotational symmetry. */
  CLASSIC {
    @Override public Iterable<Location> expand(Location loc) {
      return loc.index == 40
        ? Collections.singleton(loc)
        : Arrays.asList(loc, Location.of(80 - loc.index));
    }
    @Override public String getName() {
      return "classic";
    }
  },

  /** A left-right mirror symmetry. */
  MIRROR {
    @Override public Iterable<Location> expand(Location loc) {
      return loc.column.number == 5
        ? Collections.singleton(loc)
        : Arrays.asList(loc, Location.of(loc.row, Column.ofIndex(9 - loc.column.number)));
    }
    @Override public String getName() {
      return "mirror";
    }
  },

  /** Mirrors left-right and top-bottom. */
  DOUBLE_MIRROR {
    @Override public Iterable<Location> expand(Location loc) {
      if (loc.row.number == 5) return MIRROR.expand(loc);
      Location vert = Location.of(Row.ofIndex(9 - loc.row.number), loc.column);
      return Iterables.concat(MIRROR.expand(loc), MIRROR.expand(vert));
    }
    @Override public String getName() {
      return "double mirror";
    }
  },

  /** A mirror symmetry across one of the main diagonals. */
  DIAGONAL {
    @Override public Iterable<Location> expand(Location loc) {
      return loc.column.number == loc.row.number
        ? Collections.singleton(loc)
        : Arrays.asList(loc, Location.of(loc.column.number, loc.row.number));
    }
    @Override public String getName() {
      return "diagonal";
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
    @Override public String getName() {
      return "rotational";
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
    @Override public String getName() {
      return "blockwise";
    }
  };

  private static final Symmetry[] values = values();
  private static final ImmutableMap<String, Symmetry> names;
  static {
    ImmutableMap.Builder<String, Symmetry> builder = ImmutableMap.builder();
    for (Symmetry s : values)
      builder.put(s.getName(), s);
    names = builder.build();
  }

  /**
   * Chooses one of the symmetries at random.
   */
  public static Symmetry choose(Random random) {
    return values[random.nextInt(values.length)];
  }

  /**
   * Returns the symmetry whose {@linkplain #getName() name} is given.
   *
   * @param name   the name as returned by {@link #getName()}
   * @return   the corresponding Symmetry
   * @throws IllegalArgumentException   if the name doesn't match a Symmetry
   */
  public static Symmetry byName(String name) {
    checkArgument(names.containsKey(name), "No symmetry named %s", name);
    return names.get(name);
  }

  /**
   * Expands the given location into the set of locations that accompany it in
   * this symmetry's pattern. The given location is included in the resulting
   * set.
   */
  public abstract Iterable<Location> expand(Location loc);

  /**
   * Returns a human-readable English name for this symmetry.
   */
  public abstract String getName();

  /**
   * Tells whether this symmetry describes the layout of clues in the given
   * grid.
   */
  public boolean describes(Grid grid) {
    for (Location loc : Location.ALL) {
      boolean hasClue = grid.containsKey(loc);
      for (Location exp : expand(loc))
        if (hasClue != grid.containsKey(exp))
          return false;
    }
    return true;
  }

  /**
   * Tells to what degree this symmetry describes the layout of clues in the
   * given grid, with 0 meaning not at all and 1 meaning completely.
   */
  public double measure(Grid grid) {
    int matchingCount = 0;
    LocSet seen = new LocSet();
    for (Location loc : Location.ALL) {
      if (seen.contains(loc)) continue;
      int nClues = 0, nBlanks = 0;
      for (Location exp : expand(loc)) {
        seen.add(exp);
        if (grid.containsKey(exp)) ++nClues; else ++nBlanks;
      }
      matchingCount += Math.max(nClues, nBlanks);
    }
    return (double) matchingCount / Location.COUNT;
  }
}
