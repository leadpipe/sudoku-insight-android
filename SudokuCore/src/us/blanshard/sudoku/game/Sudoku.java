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

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

/**
 * The Sudoku game state.  Not thread safe.
 *
 * @author Luke Blanshard
 */
public final class Sudoku {

  /** The clock. */
  private final Clock clock;

  /** The initial clues. */
  private final Grid puzzle;

  /** The current state. */
  private final State state;

  /** The complete history of moves made in this game. */
  private final List<Move> history;

  public Sudoku(Grid puzzle) {
    this(puzzle, ImmutableList.<Move>of());
  }

  public Sudoku(Grid puzzle, List<Move> history) {
    this(Clock.SYSTEM, puzzle, history);
  }

  public Sudoku(Clock clock, Grid puzzle, List<Move> history) {
    this.clock = clock;
    this.puzzle = puzzle;
    this.state = new State();
    this.history = Lists.newArrayList(history);

    for (Move move : history) {
      if (!move.apply(state))
        throw new IllegalArgumentException("Bad move: " + move);
    }
  }

  public Grid getPuzzle() {
    return puzzle;
  }

  public State getState() {
    return state;
  }

  public List<Move> getHistory() {
    return Collections.unmodifiableList(history);
  }

  /** Applies the given move to this puzzle, adds it to the history if it worked. */
  public boolean move(Move move) {
    if (!move.apply(state)) return false;
    history.add(move);
    return true;
  }

  /**
   * Shared base class for State and Trail.
   */
  public abstract class Base {
    protected final Grid.Builder gridBuilder;

    private Base() {
      gridBuilder = puzzle.asBuilder();
    }

    /** Returns the set numeral, or null. */
    public Numeral get(Location loc) {
      return gridBuilder.get(loc);
    }

    public Grid getGrid() {
      return gridBuilder.build();
    }

    /**
     * Sets or clears the given location, tells whether it worked.  It only
     * doesn't work if the location contains one of the puzzle's original clues.
     */
    public abstract boolean set(Location loc, Numeral num);

    /**
     * Resets the state to just the original clues.
     */
    public abstract void reset();

    boolean actuallySet(Location loc, Numeral num) {
      if (puzzle.containsKey(loc)) return false;
      gridBuilder.put(loc, num);
      return true;
    }

    boolean actuallyClear(Location loc) {
      if (puzzle.containsKey(loc)) return false;
      gridBuilder.remove(loc);
      return true;
    }

    boolean actuallyReset() {
      gridBuilder.reset(puzzle);
      return true;
    }
  }

  /**
   * The current state of play of a Sudoku puzzle.
   */
  public final class State extends Base {
    private final List<Trail> trails = Lists.newArrayList();

    @Override public boolean set(Location loc, Numeral num) {
      return move(num == null ? new Move.Clear(clock, loc) : new Move.Set(clock, loc, num));
    }

    @Override public void reset() {
      move(new Move.Reset(clock));
    }

    public int getNumTrails() {
      return trails.size();
    }

    /** Starts a new trail. */
    public Trail newTrail() {
      Trail trail = new Trail(trails.size());
      trails.add(trail);
      return trail;
    }

    /** Returns the trail with the given ID, creating it if need be. */
    public Trail getTrail(int id) {
      while (id >= trails.size()) newTrail();
      return trails.get(id);
    }
  }

  /**
   * A trail within a puzzle.
   */
  public final class Trail extends Base {
    private final int id;
    private Location first;

    private Trail(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    /** Returns the first location set in this trail, or null if none yet. */
    public Location getFirst() {
      return first;
    }

    /** Sets or clears the given location, tells whether it worked. */
    @Override public boolean set(Location loc, Numeral num) {
      return move(num == null ? new Move.Clear(clock, id, loc) : new Move.Set(clock, id, loc, num));
    }

    @Override public void reset() {
      move(new Move.Reset(clock, id));
    }

    @Override boolean actuallySet(Location loc, Numeral num) {
      if (loc == first) return false;
      boolean answer = super.actuallySet(loc, num);
      if (answer && first == null) first = loc;
      return answer;
    }

    @Override boolean actuallyClear(Location loc) {
      if (loc == first) return false;
      return super.actuallyClear(loc);
    }

    @Override boolean actuallyReset() {
      first = null;
      return super.actuallyReset();
    }
  }
}
