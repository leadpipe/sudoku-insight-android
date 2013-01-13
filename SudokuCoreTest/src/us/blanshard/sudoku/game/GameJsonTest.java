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
import static us.blanshard.sudoku.game.GameJson.GSON;
import static us.blanshard.sudoku.game.GameJson.HISTORY_TYPE;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Test;

import java.util.List;

public class GameJsonTest {
  @Test public void fromHistory_noHistoryShouldGetEmptyArray() throws Exception {
    // given
    Sudoku game = makeGame(puzzle);

    // when
    JsonArray array = GSON.toJsonTree(game.getHistory()).getAsJsonArray();

    // then
    assertEquals(0, array.size());
  }

  @Test public void fromHistory_arrayContentsShouldMatchMainStateMoves() throws Exception {
    // given
    Sudoku game = makeGame(puzzle);
    game.getState().set(openLocation, Numeral.of(1));
    game.getState().set(openLocation, null);

    // when
    JsonArray array = GSON.toJsonTree(game.getHistory()).getAsJsonArray();

    // then
    assertEquals(2, array.size());
    assertEquals(new Move.Set(1, openLocation, Numeral.of(1)).toJsonValue(), array.get(0).getAsString());
    assertEquals(new Move.Clear(2, openLocation).toJsonValue(), array.get(1).getAsString());
  }

  @Test public void fromHistory_arrayContentsShouldMatchTrailMoves() throws Exception {
    // given
    Sudoku game = makeGame(puzzle);
    game.getTrail(0).set(openLocation, Numeral.of(1));
    game.getTrail(0).set(openLocation, null);

    // when
    JsonArray array = GSON.toJsonTree(game.getHistory()).getAsJsonArray();

    // then
    assertEquals(2, array.size());
    assertEquals(new Move.Set(1, 0, openLocation, Numeral.of(1)).toJsonValue(), array.get(0).getAsString());
    assertEquals(new Move.Clear(2, 0, openLocation).toJsonValue(), array.get(1).getAsString());
  }

  @Test public void toHistory_emptyArrayShouldYieldEmptyHistory() throws Exception {
    // given
    JsonArray array = new JsonArray();

    // when
    List<Move> history = GSON.fromJson(array, HISTORY_TYPE);

    // then
    assertEquals(0, history.size());
  }

  @Test public void toHistory_movesShouldMatchArray() throws Exception {
    // given
    JsonArray array = new JsonArray();
    array.add(new JsonPrimitive("1,-1,22,1"));
    array.add(new JsonPrimitive("2,3,45"));

    // when
    List<Move> history = GSON.fromJson(array, HISTORY_TYPE);

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
    JsonObject object = GSON.toJsonTree(stack).getAsJsonObject();

    // then
    assertEquals(0, object.get("position").getAsInt());
    assertEquals(0, object.get("commands").getAsJsonArray().size());
  }

  @Test public void fromUndoStack_commandsShouldMatchArray() throws Exception {
    // given
    UndoStack stack = new UndoStack();
    Sudoku game = makeGame(Grid.BLANK);
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), Numeral.of(1)));
    stack.doCommand(new MoveCommand(game.getState(), Location.of(0), null));
    stack.undo();

    // when
    JsonObject object = GSON.toJsonTree(stack).getAsJsonObject();

    // then
    assertEquals(1, object.get("position").getAsInt());
    assertEquals(2, object.get("commands").getAsJsonArray().size());
    assertEquals("move,-1,0,1,0", object.get("commands").getAsJsonArray().get(0).getAsString());
    assertEquals("move,-1,0,0,1", object.get("commands").getAsJsonArray().get(1).getAsString());
  }

  @Test public void toUndoStack_emptyArrayShouldYieldEmptyStack() throws Exception {
    // given
    JsonObject object = new JsonObject();
    object.addProperty("position", 0);
    object.add("commands", new JsonArray());

    // when
    UndoStack stack = GSON.fromJson(object, UndoStack.class);

    // then
    assertEquals(0, stack.getPosition());
    assertEquals(0, stack.commands.size());
  }

  @Test public void toUndoStack_serializedCommandsShouldYieldWorkingStack() throws Exception {
    // given
    JsonObject object = new JsonObject();
    object.addProperty("position", 1);
    object.add("commands", new JsonArray());
    object.get("commands").getAsJsonArray().add(new JsonPrimitive("move,-1,0,1,0"));
    object.get("commands").getAsJsonArray().add(new JsonPrimitive("move,0,0,1,0"));
    Sudoku game = makeGame(Grid.BLANK);
    GameJson.setFactory(game);

    // when
    UndoStack stack = GSON.fromJson(object, UndoStack.class);
    GameJson.clearFactory();

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
