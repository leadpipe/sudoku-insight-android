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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import org.junit.Test;

public class UndoStackTest {
  Sudoku game = new Sudoku(Grid.BLANK).resume();
  UndoStack stack = new UndoStack();

  @Test public void doCommand() throws CommandException {
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), Numeral.of(1)));
    assertEquals(true, stack.canUndo());
    assertEquals(false, stack.canRedo());
    assertEquals(1, game.getHistory().size());
    assertSame(Numeral.of(1), game.getState().get(Location.of(0)));
  }

  @Test public void undo() throws CommandException {
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), Numeral.of(1)));
    stack.undo();
    assertSame(null, game.getState().get(Location.of(0)));
    assertEquals(false, stack.canUndo());
    assertEquals(true, stack.canRedo());
    assertEquals(2, game.getHistory().size());
  }

  @Test public void redo() throws CommandException {
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), Numeral.of(1)));
    stack.undo();
    stack.redo();
    assertSame(Numeral.of(1), game.getState().get(Location.of(0)));
    assertEquals(true, stack.canUndo());
    assertEquals(false, stack.canRedo());
    assertEquals(3, game.getHistory().size());
  }

  @Test public void doCommand_clear() throws CommandException {
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), Numeral.of(1)));
    stack.doCommand(new MoveCommand(game.getState(), Location.of(1), Numeral.of(1)));
    stack.undo();
    stack.doCommand(new MoveCommand(game.getState(), Location.of(2), Numeral.of(1)));
    assertSame(Numeral.of(1), game.getState().get(Location.of(0)));
    assertSame(null, game.getState().get(Location.of(1)));
    assertSame(Numeral.of(1), game.getState().get(Location.of(2)));
    assertEquals(true, stack.canUndo());
    assertEquals(false, stack.canRedo());
    assertEquals(4, game.getHistory().size());
  }

  @Test public void doCommand_exception() throws CommandException {
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), Numeral.of(1)));
    stack.doCommand(new MoveCommand(game.getState(), Location.of(1), Numeral.of(1)));
    stack.undo();

    game.suspend();  // No moves allowed while game is suspended.
    try {
      stack.doCommand(new MoveCommand(game.getState(), Location.of(2), Numeral.of(1)));
      fail();
    } catch (CommandException e) {
      // Expected
    }

    // Ensure failed command didn't alter the ability to redo.
    assertEquals(true, stack.canRedo());
  }

  @Test public void undo_exception() throws CommandException {
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), Numeral.of(1)));
    game.suspend();
    try {
      stack.undo();
      fail();
    } catch (CommandException e) {
      // Expected
    }
    assertSame(Numeral.of(1), game.getState().get(Location.of(0)));
    assertEquals(true, stack.canUndo());
    assertEquals(false, stack.canRedo());
    assertEquals(1, game.getHistory().size());
  }

  @Test public void redo_exception() throws CommandException {
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), Numeral.of(1)));
    stack.undo();
    game.suspend();
    try {
      stack.redo();
      fail();
    } catch (CommandException e) {
      // Expected
    }
    assertSame(null, game.getState().get(Location.of(0)));
    assertEquals(false, stack.canUndo());
    assertEquals(true, stack.canRedo());
    assertEquals(2, game.getHistory().size());
  }

  @Test(expected = IllegalStateException.class) public void undo_none() throws CommandException {
    stack.undo();
  }

  @Test(expected = IllegalStateException.class) public void redo_none() throws CommandException {
    stack.redo();
  }
}
