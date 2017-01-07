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

import static com.google.common.base.Preconditions.checkNotNull;
import static us.blanshard.sudoku.game.GameJson.JOINER;
import static us.blanshard.sudoku.game.GameJson.SPLITTER;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import java.util.Iterator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A single move in a Sudoku game.  Has nested classes for all defined moves.
 *
 * @author Luke Blanshard
 */
@Immutable
public abstract class Move {

  /** When the move took place, in elapsed millis. */
  public final long timestamp;

  /** The trail ID this applies to, or -1 for the whole state. */
  public final int trailId;

  /** Converts this move to a command usable by UndoStack. */
  public abstract MoveCommand toCommand(Sudoku game);

  protected Move(long timestamp, int trailId) {
    this.timestamp = timestamp;
    this.trailId = trailId;
  }

  /** Performs this move on the given game, returns true if it worked. */
  abstract boolean apply(Sudoku game);

  /** Renders this move as a string for json.  Can be reversed by {@link #fromJsonValue}. */
  abstract String toJsonValue();

  static Move fromJsonValue(String value) {
    Iterator<String> it = SPLITTER.split(value).iterator();
    long timestamp = Long.parseLong(it.next());
    int id = Integer.parseInt(it.next());
    Location loc = Location.of(Integer.parseInt(it.next()));
    return it.hasNext() ? new Set(timestamp, id, loc, Numeral.of(Integer.parseInt(it.next())))
        : new Clear(timestamp, id, loc);
  }

  public abstract Location getLocation();
  @Nullable public abstract Numeral getNumeral();
  @Nullable public abstract Assignment getAssignment();

  public static Move make(Location loc, @Nullable Numeral num, long timestamp, int stateId) {
    return num == null
        ? new Move.Clear(timestamp, stateId, loc)
        : new Move.Set(timestamp, stateId, loc, num);
  }

  @Immutable
  public static class Set extends Move {
    public final Location loc;
    public final Numeral num;

    public Set(long timestamp, Location loc, Numeral num) {
      this(timestamp, -1, loc, num);
    }

    public Set(long timestamp, int id, Location loc, Numeral num) {
      super(timestamp, id);
      this.loc = checkNotNull(loc);
      this.num = checkNotNull(num);
    }

    @Override public MoveCommand toCommand(Sudoku game) {
      return new MoveCommand(game.getState(trailId), loc, num);
    }

    @Override boolean apply(Sudoku game) {
      return game.getState(trailId).actuallySet(loc, num);
    }

    @Override String toJsonValue() {
      return JOINER.join(timestamp, trailId, loc.index, num.number);
    }

    @Override public Location getLocation() {
      return loc;
    }

    @Override public Numeral getNumeral() {
      return num;
    }

    @Override public Assignment getAssignment() {
      return Assignment.of(loc, num);
    }
  }

  @Immutable
  public static class Clear extends Move {
    public final Location loc;

    public Clear(long timestamp, Location loc) {
      this(timestamp, -1, loc);
    }

    public Clear(long timestamp, int id, Location loc) {
      super(timestamp, id);
      this.loc = checkNotNull(loc);
    }

    @Override public MoveCommand toCommand(Sudoku game) {
      return new MoveCommand(game.getState(trailId), loc, null);
    }

    @Override boolean apply(Sudoku game) {
      return game.getState(trailId).actuallyClear(loc);
    }

    @Override String toJsonValue() {
      return JOINER.join(timestamp, trailId, loc.index);
    }

    @Override public Location getLocation() {
      return loc;
    }

    @Override @Nullable public Numeral getNumeral() {
      return null;
    }

    @Override @Nullable public Assignment getAssignment() {
      return null;
    }
  }
}