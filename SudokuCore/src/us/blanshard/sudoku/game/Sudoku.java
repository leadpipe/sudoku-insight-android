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

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
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

  /** The initial clues. */
  private final Grid puzzle;

  /** The complete history of moves made in this game. */
  private final List<Move> history;

  /** The initial elapsed milliseconds; non-zero for restored games. */
  private final long initialMillis;

  /** The object keeping track of further elapsed time. */
  private final Stopwatch stopwatch;

  /** The current state. */
  private final State state;

  /** Trails we've created. */
  private final List<Trail> trails = Lists.newArrayList();

  public Sudoku(Grid puzzle) {
    this(puzzle, ImmutableList.<Move>of(), 0);
  }

  public Sudoku(Sudoku that) {
    this(that, Ticker.systemTicker());
  }

  public Sudoku(Sudoku that, Ticker ticker) {
    this(that.puzzle, that.history, that.elapsedMillis(), ticker);
  }

  public Sudoku(Grid puzzle, List<Move> history, long initialMillis) {
    this(puzzle, history, initialMillis, Ticker.systemTicker());
  }

  public Sudoku(Grid puzzle, List<Move> history, long initialMillis, Ticker ticker) {
    this.puzzle = puzzle;
    this.history = Lists.newArrayList(history);
    this.initialMillis = initialMillis;
    this.stopwatch = new Stopwatch(ticker);
    this.state = new State();

    for (Move move : history) {
      if (!move.apply(this))
        throw new IllegalArgumentException("Bad move: " + move);
    }

    stopwatch.start();
  }

  public Grid getPuzzle() {
    return puzzle;
  }

  public List<Move> getHistory() {
    return Collections.unmodifiableList(history);
  }

  /** Returns the total elapsed time in milliseconds. */
  public long elapsedMillis() {
    return initialMillis + stopwatch.elapsedMillis();
  }

  /** Tells whether the puzzle is in its normal running state. */
  public boolean isRunning() {
    return stopwatch.isRunning();
  }

  /** Pauses the puzzle.  No moves are possible while the puzzle is paused. */
  public void pause() {
    if (stopwatch.isRunning()) stopwatch.stop();
  }

  /** Resumes the puzzle from a paused state. */
  public void resume() {
    if (!stopwatch.isRunning()) stopwatch.start();
  }

  public State getState() {
    return state;
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

  public int getNumTrails() {
    return trails.size();
  }

  /** Applies the given move to this puzzle, adds it to the history if it worked. */
  public boolean move(Move move) {
    if (!isRunning()) return false;
    if (!move.apply(this)) return false;
    history.add(move);
    return true;
  }

  /**
   * The current state of play of a Sudoku puzzle.
   */
  public class State {
    protected final Grid.Builder gridBuilder;

    private State() {
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
    public boolean set(Location loc, Numeral num) {
      return move(num == null ? new Move.Clear(elapsedMillis(), loc)
                  : new Move.Set(elapsedMillis(), loc, num));
    }

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
  }

  /**
   * A trail within a puzzle.
   */
  public final class Trail extends State {
    private final int id;
    private Location first;

    private Trail(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    /** Returns the first location set in this trail, or null if none yet. */
    public Location getTrailhead() {
      return first;
    }

    /** Sets or clears the given location, tells whether it worked. */
    @Override public boolean set(Location loc, Numeral num) {
      return move(num == null ? new Move.Clear(elapsedMillis(), id, loc)
                  : new Move.Set(elapsedMillis(), id, loc, num));
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
  }
}
