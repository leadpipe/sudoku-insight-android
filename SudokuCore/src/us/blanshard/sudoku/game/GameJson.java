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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

  public static JSONArray fromHistory(List<Move> moves) {
    JSONArray array = new JSONArray();
    for (Move move : moves) {
      array.put(move.toJsonValue());
    }
    return array;
  }

  public static List<Move> toHistory(String json) throws JSONException {
    if (json == null) return Collections.<Move>emptyList();
    return toHistory(new JSONArray(json));
  }

  public static List<Move> toHistory(JSONArray array) throws JSONException {
    List<Move> moves = Lists.newArrayList();
    for (int i = 0; i < array.length(); ++i) {
      moves.add(Move.fromJsonValue(array.getString(i)));
    }
    return moves;
  }

  public static JSONObject fromUndoStack(UndoStack stack) throws JSONException {
    JSONObject object = new JSONObject();
    object.put("position", stack.getPosition());
    JSONArray array = new JSONArray();
    for (Command c : stack.commands) {
      array.put(c.toJsonValue());
    }
    object.put("commands", array);
    return object;
  }

  public static UndoStack toUndoStack(String json, Sudoku game) throws JSONException {
    if (json == null) return new UndoStack();
    return toUndoStack(new JSONObject(json), game);
  }

  public static UndoStack toUndoStack(JSONObject object, Sudoku game) throws JSONException {
    int position = object.getInt("position");
    List<Command> commands = Lists.newArrayList();
    JSONArray array = object.getJSONArray("commands");
    for (int i = 0; i < array.length(); ++i) {
      Iterator<String> values = SPLITTER.split(array.getString(i)).iterator();
      String type = values.next();
      if (type.equals("move")) {
        commands.add(MoveCommand.fromJsonValues(values, game));
      } else {
        // TODO(leadpipe): if and when we have other kinds of commands, revisit this.
        throw new IllegalArgumentException("Unrecognized command type " + type);
      }
    }
    return new UndoStack(commands, position);
  }

  // Static methods only.
  private GameJson() {}
}
