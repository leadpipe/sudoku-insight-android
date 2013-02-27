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
package us.blanshard.sudoku.game;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static us.blanshard.sudoku.game.Fixtures.fakeTicker;
import static us.blanshard.sudoku.game.Fixtures.makeGame;
import static us.blanshard.sudoku.game.Fixtures.openLocation;
import static us.blanshard.sudoku.game.Fixtures.puzzle;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SudokuTest {
  @Mock Sudoku.Listener listener;

  @Test public void state() {
    Sudoku game = makeGame(puzzle);
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
    Sudoku game = makeGame(puzzle);
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
    // It's now the last loc left, can modify it
    assertEquals(true, trail2.set(first, Numeral.of(3)));
    assertEquals(true, trail2.set(first, null));
    assertSame(null, trail2.getTrailhead());

    Sudoku game2 = new Sudoku(game);
    assertSame(first, game2.getTrail(0).getTrailhead());
    assertEquals(trail.getGrid(), game2.getTrail(0).getGrid());

    assertEquals(2, game.getNumTrails());
    assertEquals(0, trail.getId());
  }

  @Test public void stopwatch() {
    Sudoku game = makeGame(puzzle, 123, fakeTicker());
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

  // Tests for listeners

  @Test public void shouldCallListenerOnGameCreated() {
    // given
    Sudoku.Registry registry = Sudoku.newRegistry();
    registry.addListener(listener);

    // when
    Sudoku game = makeGame(puzzle, registry);

    // then
    verify(listener).gameCreated(game);
  }

  @Test public void shouldNotCallListenerAfterListenerRemoval() {
    // given
    Sudoku.Registry registry = Sudoku.newRegistry();
    registry.addListener(listener);
    registry.removeListener(listener);

    // when
    Sudoku game = makeGame(puzzle, registry);

    // then
    verify(listener, never()).gameCreated(game);
  }

  @Test public void shouldCallListenerOnGameSuspended() {
    // given
    Sudoku game = makeGame(puzzle, Sudoku.newRegistry());
    game.getListenerRegistry().addListener(listener);

    // when
    game.suspend();

    // then
    verify(listener).gameSuspended(game);
  }

  @Test public void shouldNotCallListenerOnGameAlreadySuspended() {
    // given
    Sudoku game = makeGame(puzzle, Sudoku.newRegistry());
    game.suspend();
    game.getListenerRegistry().addListener(listener);

    // when
    game.suspend();

    // then
    verify(listener, never()).gameSuspended(game);
  }

  @Test public void shouldCallListenerOnGameResumed() {
    // given
    Sudoku game = makeGame(puzzle, Sudoku.newRegistry());
    game.suspend();
    game.getListenerRegistry().addListener(listener);

    // when
    game.resume();

    // then
    verify(listener).gameResumed(game);
  }

  @Test public void shouldNotCallListenerOnGameAlreadyResumed() {
    // given
    Sudoku game = makeGame(puzzle, Sudoku.newRegistry());
    game.getListenerRegistry().addListener(listener);

    // when
    game.resume();

    // then
    verify(listener, never()).gameResumed(game);
  }

  @Test public void shouldCallListenerOnMoveMade() {
    // given
    Sudoku game = makeGame(puzzle, Sudoku.newRegistry());
    game.getListenerRegistry().addListener(listener);

    // when
    game.getState().set(openLocation, Numeral.of(1));

    // then
    ArgumentCaptor<Move> captor = ArgumentCaptor.forClass(Move.class);
    verify(listener).moveMade(same(game), captor.capture());
    Move.Set move = (Move.Set) captor.getValue();
    assertSame(openLocation, move.loc);
    assertSame(Numeral.of(1), move.num);
  }

  @Test public void shouldNotCallListenerOnMoveNotMade() {
    // given
    Sudoku game = makeGame(puzzle, Sudoku.newRegistry());
    game.getListenerRegistry().addListener(listener);
    game.suspend();

    // when
    game.getState().set(openLocation, Numeral.of(1));

    // then
    verify(listener, never()).moveMade(same(game), any(Move.class));
  }

  @Test public void shouldCallListenerOnTrailCreated() {
    // given
    Sudoku game = makeGame(puzzle, Sudoku.newRegistry());
    game.getListenerRegistry().addListener(listener);

    // when
    Sudoku.Trail trail = game.getTrail(0);

    // then
    verify(listener).trailCreated(game, trail);
  }

  @Test public void shouldNotCallListenerOnTrailNotCreated() {
    // given
    Sudoku game = makeGame(puzzle, Sudoku.newRegistry());
    game.getTrail(0);
    game.getListenerRegistry().addListener(listener);

    // when
    Sudoku.Trail trail = game.getTrail(0);

    // then
    verify(listener, never()).trailCreated(game, trail);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void shouldThrowOnAddingToNullRegistry() {
    // given
    Sudoku.Registry registry = Sudoku.nullRegistry();

    // when
    registry.addListener(listener);

    // then: throw expected exception
  }

  @Test(expected = UnsupportedOperationException.class)
  public void shouldThrowOnRemovingFromNullRegistry() {
    // given
    Sudoku.Registry registry = Sudoku.nullRegistry();

    // when
    registry.removeListener(listener);

    // then: throw expected exception
  }

  @Test(expected = IllegalArgumentException.class)
  public void badHistory() {
    Location given = puzzle.keySet().iterator().next();
    new Sudoku(puzzle, Sudoku.nullRegistry(), asList((Move) new Move.Set(0, given, Numeral.of(1))), 0);
  }
}
