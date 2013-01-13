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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Static methods that convert various parts of Sudokus to and from json.
 *
 * @author Luke Blanshard
 */
public class GameJson {
  public static final Splitter SPLITTER = Splitter.on(',');
  public static final Joiner JOINER = Joiner.on(',');

  public static JsonArray fromHistory(List<Move> moves) {
    JsonArray array = new JsonArray();
    for (Move move : moves) {
      array.add(new JsonPrimitive(move.toJsonValue()));
    }
    return array;
  }

  public static List<Move> toHistory(String json) {
    if (json == null) return Collections.<Move>emptyList();
    return toHistory(new JsonParser().parse(json).getAsJsonArray());
  }

  public static List<Move> toHistory(JsonArray array) {
    List<Move> moves = Lists.newArrayList();
    for (int i = 0; i < array.size(); ++i) {
      moves.add(Move.fromJsonValue(array.get(i).getAsString()));
    }
    return moves;
  }

  public static JsonObject fromUndoStack(UndoStack stack) {
    JsonObject object = new JsonObject();
    object.addProperty("position", stack.getPosition());
    JsonArray array = new JsonArray();
    for (Command c : stack.commands) {
      array.add(new JsonPrimitive(c.toJsonValue()));
    }
    object.add("commands", array);
    return object;
  }

  public static class CommandFactory {
    protected final Sudoku game;

    public CommandFactory(Sudoku game) {
      this.game = game;
    }

    public Command toCommand(String type, Iterator<String> values) {
      if (type.equals("move"))
        return MoveCommand.fromJsonValues(values, game);
      throw new IllegalArgumentException("Unrecognized command type " + type);
    }
  }

  public static UndoStack toUndoStack(String json, CommandFactory factory) {
    if (json == null) return new UndoStack();
    return toUndoStack(new JsonParser().parse(json).getAsJsonObject(), factory);
  }

  public static UndoStack toUndoStack(JsonObject object, CommandFactory factory) {
    int position = object.get("position").getAsInt();
    List<Command> commands = Lists.newArrayList();
    JsonArray array = object.get("commands").getAsJsonArray();
    for (int i = 0; i < array.size(); ++i) {
      Iterator<String> values = SPLITTER.split(array.get(i).getAsString()).iterator();
      String type = values.next();
      commands.add(factory.toCommand(type, values));
    }
    return new UndoStack(commands, position);
  }

  // Static methods only.
  private GameJson() {}
}
