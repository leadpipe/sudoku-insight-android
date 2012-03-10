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

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * The Sudoku game state.  Not thread safe.
 *
 * @author Luke Blanshard
 */
public final class Sudoku {

  /** The initial clues. */
  private final Grid puzzle;

  /** Our listeners. */
  private final Registry registry;

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
    this(puzzle, newRegistry());
  }

  public Sudoku(Grid puzzle, Registry registry) {
    this(puzzle, registry, ImmutableList.<Move>of(), 0);
  }

  public Sudoku(Sudoku that) {
    this(that.puzzle, that.registry, that.history, that.elapsedMillis());
  }

  public Sudoku(Grid puzzle, Registry registry, List<Move> history, long initialMillis) {
    this(puzzle, registry, history, initialMillis, Ticker.systemTicker());
  }

  Sudoku(Grid puzzle, Registry registry, List<Move> history, long initialMillis, Ticker ticker) {
    this.puzzle = checkNotNull(puzzle);
    this.registry = checkNotNull(registry);
    this.history = Lists.newArrayList(history);
    this.initialMillis = initialMillis;
    this.stopwatch = new Stopwatch(ticker);
    this.state = new State();

    for (Move move : history) {
      if (!move.apply(this))
        throw new IllegalArgumentException("Bad move: " + move);
    }

    registry.asListener().gameCreated(this);
  }

  public Grid getPuzzle() {
    return puzzle;
  }

  public Registry getListenerRegistry() {
    return registry;
  }

  /** Returns a listener registry that refuses to take listeners. */
  public static Registry nullRegistry() {
    return NULL_REGISTRY;
  }

  /** Creates a registry that does the normal thing. */
  public static Registry newRegistry() {
    return new NormalRegistry();
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

  /** Suspends the puzzle.  No moves are possible while the puzzle is suspended. */
  public Sudoku suspend() {
    if (stopwatch.isRunning()) {
      stopwatch.stop();
      registry.asListener().gameSuspended(this);
    }
    return this;
  }

  /** Resumes the puzzle from a suspended state. */
  public Sudoku resume() {
    if (!stopwatch.isRunning()) {
      stopwatch.start();
      registry.asListener().gameResumed(this);
    }
    return this;
  }

  public State getState() {
    return state;
  }

  public State getState(int id) {
    return id < 0 ? state : getTrail(id);
  }

  /** Starts a new trail. */
  public Trail newTrail() {
    Trail trail = new Trail(trails.size());
    trails.add(trail);
    registry.asListener().trailCreated(this, trail);
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
    registry.asListener().moveMade(this, move);
    return true;
  }

  /** Does every location in the state have a numeral in it? */
  public boolean isFull() {
    return state.gridBuilder.size() == Location.COUNT;
  }

  /**
   * A callback interface for interested parties to find out what's going on in
   * a Sudoku.
   */
  public interface Listener {
    /** Called when a new Sudoku instance is created. */
    void gameCreated(Sudoku game);

    /** Called when a Sudoku has been suspended. */
    void gameSuspended(Sudoku game);

    /** Called when a Sudoku has been resumed. */
    void gameResumed(Sudoku game);

    /** Called when a move has been successfully made in a Sudoku. */
    void moveMade(Sudoku game, Move move);

    /** Called when a new trail has been created. */
    void trailCreated(Sudoku game, Trail trail);
  }

  /**
   * A null implementation of {@link Listener} so you can have a listener
   * without having to implement every method.
   */
  public static class Adapter implements Listener {
    @Override public void gameCreated(Sudoku game) {}
    @Override public void gameSuspended(Sudoku game) {}
    @Override public void gameResumed(Sudoku game) {}
    @Override public void moveMade(Sudoku game, Move move) {}
    @Override public void trailCreated(Sudoku game, Trail trail) {}
  }

  /**
   * An object that keeps track of the {@linkplain Listener listeners} on behalf
   * of one or more Sudokus.
   */
  public abstract static class Registry {

    /** Adds a listener to the registry. */
    public abstract void addListener(Listener listener);

    /** Removes a listener from the registry. */
    public abstract void removeListener(Listener listener);

    /**
     * Exposes the registry as a listener itself, so the Sudoku has a single
     * instance to address.
     */
    protected abstract Listener asListener();
  }

  /**
   * The current state of play of a Sudoku puzzle.
   */
  public class State {
    protected final Grid.Builder gridBuilder;

    private State() {
      gridBuilder = puzzle.toBuilder();
    }

    /** This state's ID within the game.  Reversed by {@link #getState(int)}. */
    public int getId() {
      return -1;
    }

    public Sudoku getGame() {
      return Sudoku.this;
    }

    /** Returns the set numeral, or null. */
    public Numeral get(Location loc) {
      return gridBuilder.get(loc);
    }

    public Grid getGrid() {
      return gridBuilder.build();
    }

    /**
     * Returns the number of locations in this State that have been set, not
     * counting the puzzle's clue locations.
     */
    public int getSetCount() {
      return gridBuilder.size() - puzzle.size();
    }

    /**
     * Sets or clears the given location, tells whether it worked. It usually
     * only doesn't work if the location contains one of the puzzle's original
     * clues.
     */
    public boolean set(Location loc, Numeral num) {
      return move(num == null ? new Move.Clear(elapsedMillis(), loc)
                  : new Move.Set(elapsedMillis(), loc, num));
    }

    /** Tells whether a call to {@link #set} will succeed. */
    public boolean canModify(Location loc) {
      return !puzzle.containsKey(loc);
    }

    boolean actuallySet(Location loc, Numeral num) {
      if (canModify(loc)) {
        gridBuilder.put(loc, num);
        return true;
      }
      return false;
    }

    boolean actuallyClear(Location loc) {
      if (canModify(loc)) {
        gridBuilder.remove(loc);
        return true;
      }
      return false;
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

    @Override public int getId() {
      return id;
    }

    @Override public Grid getGrid() {
      return state.getGrid().toBuilder().putAll(gridBuilder.entrySet()).build();
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

    @Override public boolean canModify(Location loc) {
      return super.canModify(loc) && (loc != first || getSetCount() == 1);
    }

    @Override boolean actuallySet(Location loc, Numeral num) {
      boolean answer = super.actuallySet(loc, num);
      if (answer && first == null) first = loc;
      return answer;
    }

    @Override boolean actuallyClear(Location loc) {
      boolean answer = super.actuallyClear(loc);
      if (answer && loc == first) first = null;
      return answer;
    }
  }

  private static final Listener NULL_LISTENER = new Adapter();
  private static final Registry NULL_REGISTRY = new NullRegistry();

  private static class NullRegistry extends Registry {
    @Override public void addListener(Listener listener) {
      throw new UnsupportedOperationException();
    }
    @Override public void removeListener(Listener listener) {
      throw new UnsupportedOperationException();
    }
    @Override protected Listener asListener() { return NULL_LISTENER; }
  }

  private static class NormalRegistry extends Registry implements Listener {
    private final List<Listener> listeners = new LinkedList<Listener>();

    @Override public void addListener(Listener listener) {
      listeners.add(listener);
    }

    @Override public void removeListener(Listener listener) {
      listeners.remove(listener);
    }

    @Override protected Listener asListener() {
      return this;
    }

    @Override public void gameCreated(Sudoku game) {
      for (Listener listener : listeners)
        listener.gameCreated(game);
    }

    @Override public void gameSuspended(Sudoku game) {
      for (Listener listener : listeners)
        listener.gameSuspended(game);
    }

    @Override public void gameResumed(Sudoku game) {
      for (Listener listener : listeners)
        listener.gameResumed(game);
    }

    @Override public void moveMade(Sudoku game, Move move) {
      for (Listener listener : listeners)
        listener.moveMade(game, move);
    }

    @Override public void trailCreated(Sudoku game, Trail trail) {
      for (Listener listener : listeners)
        listener.trailCreated(game, trail);
    }
  }
}
