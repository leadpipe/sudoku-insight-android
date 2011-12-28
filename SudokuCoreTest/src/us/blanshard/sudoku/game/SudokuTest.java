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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import us.blanshard.sudoku.core.Generator;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Symmetry;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;

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

    Sudoku game2 = new Sudoku(game);
    assertEquals(puzzle, game2.getState().getGrid());
    assertEquals(game.getHistory().size(), game2.getHistory().size());
  }

  @Test public void trails() {
    Sudoku game = new Sudoku(puzzle);
    Sudoku.Trail trail = game.newTrail();

    Location first = null, last = null;

    for (Location loc : Location.ALL) {
      boolean canChange = trail.get(loc) == null;
      if (canChange) {
        if (first == null) first = loc;
        else last = loc;
      }
      assertEquals(canChange, trail.set(loc, Numeral.of(9)));
    }

    assertSame(first, trail.getTrailhead());
    assertEquals(81, trail.getGrid().size());
    assertEquals(Grid.State.BROKEN, trail.getGrid().getState());

    assertEquals(false, trail.set(first, null));
    assertEquals(false, trail.set(first, Numeral.of(3)));

    assertEquals(true, trail.set(last, null));

    // Ensure the trailhead can be cleared iff it's the last loc set
    Sudoku.Trail trail2 = game.newTrail();
    assertEquals(true, trail2.set(first, Numeral.of(1)));
    assertEquals(true, trail2.set(last, Numeral.of(1)));
    assertEquals(false, trail2.set(first, Numeral.of(2)));
    assertEquals(false, trail2.set(first, null));
    assertEquals(true, trail2.set(last, null));
    // It's now the last loc left
    assertEquals(false, trail2.set(first, Numeral.of(3)));  // Still can't set
    assertEquals(true, trail2.set(first, null));  // But can clear
    assertSame(null, trail2.getTrailhead());

    Sudoku game2 = new Sudoku(game);
    assertSame(first, game2.getTrail(0).getTrailhead());
    assertEquals(trail.getGrid(), game2.getTrail(0).getGrid());

    assertEquals(2, game.getNumTrails());
    assertEquals(0, trail.getId());
  }

  @Test public void stopwatch() {
    final long oneMs = MILLISECONDS.toNanos(1);
    // A ticker that advances by one millisecond each time it's read.
    Ticker ticker = new Ticker() {
      long value = 0;
      @Override public long read() { return value += oneMs; }
    };

    Sudoku game = new Sudoku(puzzle, ImmutableList.<Move>of(), 123, ticker);
    assertEquals(124, game.elapsedMillis());
    assertEquals(true, game.isRunning());

    game.resume();  // Has no effect
    assertEquals(true, game.isRunning());

    game.suspend();
    assertEquals(125, game.elapsedMillis());
    assertEquals(false, game.isRunning());

    game.suspend();  // Has no effect
    assertEquals(125, game.elapsedMillis());
    assertEquals(false, game.isRunning());

    Location first = null;
    for (Location loc : Location.ALL) {
      assertEquals(false, game.getState().set(loc, Numeral.of(1)));
      if (first == null && puzzle.get(loc) == null) first = loc;
    }

    assertEquals(125, game.elapsedMillis());

    game.resume();
    assertEquals(126, game.elapsedMillis());
    assertEquals(true, game.isRunning());

    assertEquals(true, game.getState().set(first, Numeral.of(1)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void badHistory() {
    Location given = puzzle.keySet().iterator().next();
    new Sudoku(puzzle, asList((Move) new Move.Set(0, given, Numeral.of(1))), 0);
  }
}
