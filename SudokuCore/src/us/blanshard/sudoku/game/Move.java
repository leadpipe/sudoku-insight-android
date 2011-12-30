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

import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

/**
 * A single move in a Sudoku game.  Has nested classes for all defined moves.
 *
 * @author Luke Blanshard
 */
public abstract class Move {

  /** When the move took place, in elapsed millis. */
  public final long timestamp;

  /** The trail ID this applies to, or -1 for the whole state. */
  public final int id;

  protected Move(long timestamp, int id) {
    this.timestamp = timestamp;
    this.id = id;
  }

  /** Performs this move on the given game, returns true if it worked. */
  abstract boolean apply(Sudoku game);

  public Sudoku.State getState(Sudoku game) {
    return id < 0 ? game.getState() : game.getTrail(id);
  }

  public abstract Location getLocation();

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

    @Override boolean apply(Sudoku game) {
      return getState(game).actuallySet(loc, num);
    }

    @Override public Location getLocation() {
      return loc;
    }
  }

  public static class Clear extends Move {
    public final Location loc;

    public Clear(long timestamp, Location loc) {
      this(timestamp, -1, loc);
    }

    public Clear(long timestamp, int id, Location loc) {
      super(timestamp, id);
      this.loc = checkNotNull(loc);
    }

    @Override boolean apply(Sudoku game) {
      return getState(game).actuallyClear(loc);
    }

    @Override public Location getLocation() {
      return loc;
    }
  }
}
