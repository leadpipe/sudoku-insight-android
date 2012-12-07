/*
Copyright 2012 Google Inc.

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
import static us.blanshard.sudoku.game.Fixtures.makeGame;
import static us.blanshard.sudoku.game.Fixtures.openLocation;
import static us.blanshard.sudoku.game.Fixtures.puzzle;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class GameJsonTest {
  @Test public void fromHistory_noHistoryShouldGetEmptyArray() throws Exception {
    // given
    Sudoku game = makeGame(puzzle);

    // when
    JSONArray array = GameJson.fromHistory(game.getHistory());

    // then
    assertEquals(0, array.length());
  }

  @Test public void fromHistory_arrayContentsShouldMatchMainStateMoves() throws Exception {
    // given
    Sudoku game = makeGame(puzzle);
    game.getState().set(openLocation, Numeral.of(1));
    game.getState().set(openLocation, null);

    // when
    JSONArray array = GameJson.fromHistory(game.getHistory());

    // then
    assertEquals(2, array.length());
    assertEquals(new Move.Set(1, openLocation, Numeral.of(1)).toJsonValue(), array.getString(0));
    assertEquals(new Move.Clear(2, openLocation).toJsonValue(), array.getString(1));
  }

  @Test public void fromHistory_arrayContentsShouldMatchTrailMoves() throws Exception {
    // given
    Sudoku game = makeGame(puzzle);
    game.getTrail(0).set(openLocation, Numeral.of(1));
    game.getTrail(0).set(openLocation, null);

    // when
    JSONArray array = GameJson.fromHistory(game.getHistory());

    // then
    assertEquals(2, array.length());
    assertEquals(new Move.Set(1, 0, openLocation, Numeral.of(1)).toJsonValue(), array.getString(0));
    assertEquals(new Move.Clear(2, 0, openLocation).toJsonValue(), array.getString(1));
  }

  @Test public void toHistory_emptyArrayShouldYieldEmptyHistory() throws Exception {
    // given
    JSONArray array = new JSONArray();

    // when
    List<Move> history = GameJson.toHistory(array);

    // then
    assertEquals(0, history.size());
  }

  @Test public void toHistory_movesShouldMatchArray() throws Exception {
    // given
    JSONArray array = new JSONArray();
    array.put("1,-1,22,1");
    array.put("2,3,45");

    // when
    List<Move> history = GameJson.toHistory(array);

    // then
    assertEquals(2, history.size());

    Move.Set m0 = (Move.Set) history.get(0);
    assertEquals(1, m0.timestamp);
    assertEquals(-1, m0.trailId);
    assertEquals(Location.of(22), m0.loc);
    assertEquals(Numeral.of(1), m0.num);

    Move.Clear m1 = (Move.Clear) history.get(1);
    assertEquals(2, m1.timestamp);
    assertEquals(3, m1.trailId);
    assertEquals(Location.of(45), m1.loc);
  }

  @Test public void fromUndoStack_emptyStackShouldYieldEmptyCommandsArray() throws Exception {
    // given
    UndoStack stack = new UndoStack();

    // when
    JSONObject object = GameJson.fromUndoStack(stack);

    // then
    assertEquals(0, object.getInt("position"));
    assertEquals(0, object.getJSONArray("commands").length());
  }

  @Test public void fromUndoStack_commandsShouldMatchArray() throws Exception {
    // given
    UndoStack stack = new UndoStack();
    Sudoku game = makeGame(Grid.BLANK);
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), Numeral.of(1)));
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), null));
    stack.undo();

    // when
    JSONObject object = GameJson.fromUndoStack(stack);

    // then
    assertEquals(1, object.getInt("position"));
    assertEquals(2, object.getJSONArray("commands").length());
    assertEquals("move,-1,0,1,0", object.getJSONArray("commands").getString(0));
    assertEquals("move,-1,0,0,1", object.getJSONArray("commands").getString(1));
  }

  @Test public void toUndoStack_emptyArrayShouldYieldEmptyStack() throws Exception {
    // given
    JSONObject object = new JSONObject();
    object.put("position", 0);
    object.put("commands", new JSONArray());
    Sudoku game = makeGame(Grid.BLANK);

    // when
    UndoStack stack = GameJson.toUndoStack(object, new GameJson.CommandFactory(game));

    // then
    assertEquals(0, stack.getPosition());
    assertEquals(0, stack.commands.size());
  }

  @Test public void toUndoStack_serializedCommandsShouldYieldWorkingStack() throws Exception {
    // given
    JSONObject object = new JSONObject();
    object.put("position", 1);
    object.put("commands", new JSONArray());
    object.getJSONArray("commands").put("move,-1,0,1,0");
    object.getJSONArray("commands").put("move,0,0,1,0");
    Sudoku game = makeGame(Grid.BLANK);

    // when
    UndoStack stack = GameJson.toUndoStack(object, new GameJson.CommandFactory(game));

    // then
    assertEquals(1, stack.getPosition());
    assertEquals(2, stack.commands.size());

    MoveCommand c0 = (MoveCommand) stack.commands.get(0);
    assertSame(game.getState(), c0.state);
    assertEquals(Location.of(0), c0.loc);
    assertEquals(Numeral.of(1), c0.num);
    assertEquals(null, c0.prevNum);

    MoveCommand c1 = (MoveCommand) stack.commands.get(1);
    assertSame(game.getTrail(0), c1.state);
    assertEquals(Location.of(0), c1.loc);
    assertEquals(Numeral.of(1), c1.num);
    assertEquals(null, c1.prevNum);
  }
}
