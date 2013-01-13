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
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Static methods that convert various parts of Sudokus to and from json.
 *
 * @author Luke Blanshard
 */
public class GameJson {
  public static final Splitter SPLITTER = Splitter.on(',');
  public static final Joiner JOINER = Joiner.on(',');

  private static final ThreadLocal<CommandFactory> factorySlot = new ThreadLocal<GameJson.CommandFactory>();

  /** A Type to use with {@link Gson} for game histories. */
  @SuppressWarnings("serial")
  public static final Type HISTORY_TYPE = new TypeToken<List<Move>>(){}.getType();

  /** An instance with all of our type adapters registered. */
  public static final Gson GSON = register(new GsonBuilder()).create();

  /**
   * Registers type adapters in the given builder so that history lists and undo
   * stacks can be serialized and deserialized.  Note that undo stacks require a
   * CommandFactory be established before deserialization; see {@link #setFactory}.
   */
  public static GsonBuilder register(GsonBuilder builder) {

    builder.registerTypeHierarchyAdapter(Move.class, new TypeAdapter<Move>() {
      @Override public void write(JsonWriter out, Move value) throws IOException {
        out.value(value.toJsonValue());
      }
      @Override public Move read(JsonReader in) throws IOException {
        return Move.fromJsonValue(in.nextString());
      }
    });

    final TypeAdapter<Command> commandAdapter = new TypeAdapter<Command>() {
      @Override public void write(JsonWriter out, Command value) throws IOException {
        out.value(value.toJsonValue());
      }
      @Override public Command read(JsonReader in) throws IOException {
        Iterator<String> values = SPLITTER.split(in.nextString()).iterator();
        String type = values.next();
        return factorySlot.get().toCommand(type, values);
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

  /**
   * Parses the given JSON as a list of moves, or returns the empty list if the
   * JSON is null.
   */
  public static List<Move> toHistory(@Nullable String json) {
    if (json == null) return Lists.newArrayList();
    return GSON.fromJson(json, HISTORY_TYPE);
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

  /**
   * Establishes the command factory used by subsequent creation of UndoStacks
   * in this thread.
   */
  public static void setFactory(CommandFactory factory) {
    factorySlot.set(factory);
  }

  /**
   * Establishes a default command factory for the given game.
   */
  public static void setFactory(Sudoku game) {
    setFactory(new CommandFactory(game));
  }

  /** Removes the command factory. */
  public static void clearFactory() {
    setFactory((CommandFactory) null);
  }

  // Static methods only.
  private GameJson() {}
}
