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

  /** When the move took place, in system millis. */
  public final long timestamp;

  /** The trail ID this applies to, or -1 for the whole state. */
  public final int id;

  protected Move(Clock clock, int id) {
    this.timestamp = clock.now();
    this.id = id;
  }

  /** Performs this move on the given game state, returns true if it worked. */
  abstract boolean apply(Sudoku.State state);

  public Sudoku.Base getBaseState(Sudoku.State state) {
    return id < 0 ? state : state.getTrail(id);
  }

  public static class Set extends Move {
    public final Location loc;
    public final Numeral num;

    public Set(Clock clock, Location loc, Numeral num) {
      this(clock, -1, loc, num);
    }

    public Set(Clock clock, int id, Location loc, Numeral num) {
      super(clock, id);
      this.loc = checkNotNull(loc);
      this.num = checkNotNull(num);
    }

    @Override boolean apply(Sudoku.State state) {
      return getBaseState(state).actuallySet(loc, num);
    }
  }

  public static class Clear extends Move {
    public final Location loc;

    public Clear(Clock clock, Location loc) {
      this(clock, -1, loc);
    }

    public Clear(Clock clock, int id, Location loc) {
      super(clock, id);
      this.loc = checkNotNull(loc);
    }

    @Override boolean apply(Sudoku.State state) {
      return getBaseState(state).actuallyClear(loc);
    }
  }

  public static class Reset extends Move {
    public Reset(Clock clock) {
      this(clock, -1);
    }

    public Reset(Clock clock, int id) {
      super(clock, id);
    }

    @Override boolean apply(Sudoku.State state) {
      return getBaseState(state).actuallyReset();
    }
  }
}
