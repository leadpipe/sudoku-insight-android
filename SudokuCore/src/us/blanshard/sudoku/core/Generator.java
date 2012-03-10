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

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generates Sudoku starting grids.  The various enumeration values correspond
 * to different strategies for generating grids.
 *
 * <p> Always produces grids with unique solutions.  The basic approach of
 * starting with the target solution grid is due to David Bau
 * (https://www.assembla.com/spaces/jssudoku/team).
 *
 * @author Luke Blanshard
 */
public enum Generator {

  /**
   * Generates a starting grid that is solved in a single step.
   */
  SIMPLE {
    @Override public Grid generate(Random random, Symmetry symmetry, Grid target) {
      return buildToMaximal(random, symmetry, target, Lists.<Location>newArrayList()).build();
    }
    @Override public boolean honorsSymmetry() { return true; }
  },

  /**
   * Generates a starting grid that is solved in a single step, then subtracts
   * givens until no more can be taken away.
   */
  SUBTRACTIVE {
    @Override public Grid generate(Random random, Symmetry symmetry, Grid target) {
      List<Location> used = Lists.newArrayList();
      Grid.Builder gridBuilder = buildToMaximal(random, symmetry, target, used);
      return subtract(random, symmetry, gridBuilder, used);
    }
    @Override public boolean honorsSymmetry() { return true; }
  },

  /**
   * Like SUBTRACTIVE, but follows it by subtracting individual givens without
   * regard to the symmetry.
   */
  SUBTRACTIVE_RANDOM {
    @Override public Grid generate(Random random, Symmetry symmetry, Grid target) {
      Grid start = SUBTRACTIVE.generate(random, symmetry, target);
      if (symmetry == Symmetry.RANDOM) return start;
      return subtract(
          random, Symmetry.RANDOM, start.toBuilder(), Lists.newArrayList(start.keySet()));
    }
    @Override public boolean honorsSymmetry() { return false; }
  };

  /**
   * Generates a puzzle that is uniquely solvable as the given target grid,
   * which must be completely solved.
   */
  public abstract Grid generate(Random random, Symmetry symmetry, Grid target);

  /**
   * Tells whether the given generator honors the symmetry it is given.  Those
   * generators that do not honor symmetry still make use of it, so there will
   * usually be at least a trace of the symmetry in the resulting puzzles.
   */
  public abstract boolean honorsSymmetry();

  /**
   * Generates a puzzle.
   */
  public final Grid generate(Random random, Symmetry symmetry) {
    return generate(random, symmetry, makeTarget(random));
  }

  /** Creates a completely solved grid. */
  public static Grid makeTarget(Random random) {
    return new Solver(Grid.BLANK, random, Solver.Strategy.SIMPLE).iterator().next();
  }

  /** Returns all locations in random order. */
  public static List<Location> randomLocations(Random random) {
    List<Location> locs = Lists.newArrayList(Location.ALL);
    Collections.shuffle(locs, random);
    return locs;
  }

  /**
   * Creates a starting grid that yields a single solution.  Fills the given
   * list with the locations used to seed the resulting grid.
   */
  public static Grid.Builder buildToMaximal(
      Random random, Symmetry symmetry, Grid target, List<Location> used) {
    List<Location> randomLocs = randomLocations(random);
    Grid.Builder gridBuilder = Grid.builder();
    Marks.Builder marksBuilder = Marks.builder();

    // Assign numerals from the target, if they aren't already implied by
    // previous assignments.
    for (Location randomLoc : randomLocs) {
      if (marksBuilder.get(randomLoc).size() > 1) {
        used.add(randomLoc);
        for (Location loc : symmetry.expand(randomLoc)) {
          gridBuilder.put(loc, target.get(loc));
          marksBuilder.assignRecursively(loc, target.get(loc));
        }
      }
    }

    return gridBuilder;
  }

  public static boolean hasUniqueSolution(Grid start, Random random) {
    return Solver.solve(start, random).numSolutions == 1;
  }

  /**
   * Subtracts as many givens as possible from the given grid, according to the
   * given symmetry, while ensuring the grid remains uniquely solvable.
   */
  public static Grid subtract(Random random, Symmetry symmetry,
                              Grid.Builder gridBuilder, List<Location> used) {
    Collections.shuffle(used, random);
    for (Location usedLoc : used) {
      Grid prev = gridBuilder.build();
      for (Location loc : symmetry.expand(usedLoc))
        gridBuilder.remove(loc);
      if (!hasUniqueSolution(gridBuilder.build(), random))
        gridBuilder = prev.toBuilder();
    }
    return gridBuilder.build();
  }
}
