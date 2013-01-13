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
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;
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

  /** A Type to use with {@link Gson} for game histories. */
  @SuppressWarnings("serial")
  public static final Type HISTORY_TYPE = new TypeToken<List<Move>>(){}.getType();

  /** A convenience for reading/writing history. */
  public static final Gson HISTORY_GSON = registerHistory(new GsonBuilder()).create();

  /**
   * Registers type adapters in the given builder so that history lists can be
   * serialized and deserialized.
   */
  public static GsonBuilder registerHistory(GsonBuilder builder) {
    builder.registerTypeHierarchyAdapter(Move.class, new TypeAdapter<Move>() {
      @Override public void write(JsonWriter out, Move value) throws IOException {
        out.value(value.toJsonValue());
      }
      @Override public Move read(JsonReader in) throws IOException {
        return Move.fromJsonValue(in.nextString());
      }
    });
    return builder;
  }

  /**
   * Registers type adapters in the given builder so that history lists and undo
   * stacks can be serialized and deserialized.
   */
  public static GsonBuilder registerAll(GsonBuilder builder, Sudoku game) {
    return registerAll(builder, new CommandFactory(game));
  }

  /**
   * Registers type adapters in the given builder so that history lists and undo
   * stacks can be serialized and deserialized.
   */
  public static GsonBuilder registerAll(GsonBuilder builder, final CommandFactory factory) {

    registerHistory(builder);

    final TypeAdapter<Command> commandAdapter = new TypeAdapter<Command>() {
      @Override public void write(JsonWriter out, Command value) throws IOException {
        out.value(value.toJsonValue());
      }
      @Override public Command read(JsonReader in) throws IOException {
        Iterator<String> values = SPLITTER.split(in.nextString()).iterator();
        String type = values.next();
        return factory.toCommand(type, values);
      }
    };
    builder.registerTypeHierarchyAdapter(Command.class, commandAdapter);

    builder.registerTypeAdapter(UndoStack.class, new TypeAdapter<UndoStack>() {
      @Override public void write(JsonWriter out, UndoStack value) throws IOException {
        out.beginObject();
        out.name("position").value(value.getPosition());
        out.name("commands").beginArray();
        for (Command c : value.commands)
          commandAdapter.write(out, c);
        out.endArray();
        out.endObject();
      }
      @Override public UndoStack read(JsonReader in) throws IOException {
        int position = -1;
        List<Command> commands = null;
        in.beginObject();
        while (in.hasNext()) {
          String name = in.nextName();
          if (name.equals("position")) {
            position = in.nextInt();
          } else if (name.equals("commands")) {
            commands = Lists.newArrayList();
            in.beginArray();
            while (in.hasNext())
              commands.add(commandAdapter.read(in));
            in.endArray();
          } else {
            in.skipValue();
          }
        }
        in.endObject();
        return new UndoStack(commands, position);
      }
    });

    return builder;
  }

  private static JsonArray fromHistory(List<Move> moves) {
    JsonArray array = new JsonArray();
    for (Move move : moves) {
      array.add(new JsonPrimitive(move.toJsonValue()));
    }
    return array;
  }

  private static List<Move> toHistory(String json) {
    if (json == null) return Collections.<Move>emptyList();
    return toHistory(new JsonParser().parse(json).getAsJsonArray());
  }

  private static List<Move> toHistory(JsonArray array) {
    List<Move> moves = Lists.newArrayList();
    for (int i = 0; i < array.size(); ++i) {
      moves.add(Move.fromJsonValue(array.get(i).getAsString()));
    }
    return moves;
  }

  private static JsonObject fromUndoStack(UndoStack stack) {
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

  private static UndoStack toUndoStack(String json, CommandFactory factory) {
    if (json == null) return new UndoStack();
    return toUndoStack(new JsonParser().parse(json).getAsJsonObject(), factory);
  }

  private static UndoStack toUndoStack(JsonObject object, CommandFactory factory) {
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
