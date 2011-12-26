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
package us.blanshard.sudoku.game;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import us.blanshard.sudoku.core.Generator;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Symmetry;

import org.junit.Test;

import java.util.Random;

public class SudokuTest {
  Random random = new Random(0);
  Grid puzzle = Generator.SIMPLE.generate(random, Symmetry.BLOCKWISE);

  @Test public void state() {
    Sudoku game = new Sudoku(puzzle);
    assertSame(puzzle, game.getPuzzle());
    assertEquals(0, game.getHistory().size());
    assertEquals(puzzle, game.getState().getGrid());

    for (Location loc : Location.ALL) {
      boolean canChange = game.getState().get(loc) == null;
      assertEquals(canChange, game.getState().set(loc, Numeral.of(1)));
      assertEquals(canChange, game.getState().set(loc, null));
    }

    assertEquals(2 * (81 - puzzle.size()), game.getHistory().size());
    assertEquals(puzzle, game.getState().getGrid());

    game.getState().reset();

    assertEquals(1 + 2 * (81 - puzzle.size()), game.getHistory().size());
    assertEquals(puzzle, game.getState().getGrid());

    Sudoku game2 = new Sudoku(puzzle, game.getHistory());
    assertEquals(puzzle, game2.getState().getGrid());
    assertEquals(game.getHistory().size(), game2.getHistory().size());
  }

  @Test public void trails() {
    Sudoku game = new Sudoku(puzzle);
    Sudoku.Trail trail = game.getState().newTrail();

    Location first = null, last = null;

    for (Location loc : Location.ALL) {
      boolean canChange = trail.get(loc) == null;
      if (canChange) {
        if (first == null) first = loc;
        else last = loc;
      }
      assertEquals(canChange, trail.set(loc, Numeral.of(9)));
    }

    assertSame(first, trail.getFirst());
    assertEquals(81, trail.getGrid().size());
    assertEquals(Grid.State.BROKEN, trail.getGrid().getState());

    assertEquals(false, trail.set(first, null));
    assertEquals(false, trail.set(first, Numeral.of(3)));

    assertEquals(true, trail.set(last, null));

    Sudoku game2 = new Sudoku(puzzle, game.getHistory());
    assertSame(first, game2.getState().getTrail(0).getFirst());
    assertEquals(trail.getGrid(), game2.getState().getTrail(0).getGrid());

    assertEquals(1, game.getState().getNumTrails());
    assertEquals(0, trail.getId());

    trail.reset();
    assertEquals(puzzle, trail.getGrid());
    assertNull(trail.getFirst());
  }

  @Test(expected = IllegalArgumentException.class)
  public void badHistory() {
    Location given = puzzle.keySet().iterator().next();
    new Sudoku(puzzle, asList((Move) new Move.Set(Clock.SYSTEM, given, Numeral.of(1))));
  }
}
