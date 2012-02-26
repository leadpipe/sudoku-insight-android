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
package us.blanshard.sudoku.insight;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Marks;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.game.Sudoku;

import java.util.Set;

/**
 * Analyzes a Sudoku game state, producing a series of insights about it.  Also
 * has the capability to rate a puzzle.
 *
 * @author Luke Blanshard
 */
public class Analyzer {

  /**
   * The phases that {@link #analyze} may go through, and that are reported to
   * the callback as they are finished.
   */
  public enum Phase {
    ERRORS,
    SINGLETONS,
    COMPLETE,
    INTERRUPTED;
  }

  /**
   * Called by {@link #analyze} for each insight found and for each phase
   * traversed.
   */
  public interface Callback {
    void take(Insight insight);
    void phase(Phase phase);
  }

  private final Grid puzzle;
  private final Grid work;

  public Analyzer(Sudoku.State gameState) {
    this(gameState.getGame().getPuzzle(), gameState.getGrid());
  }

  private Analyzer(Grid puzzle) {
    this(puzzle, puzzle);
  }

  private Analyzer(Grid puzzle, Grid work) {
    this.puzzle = puzzle;
    this.work = work;
  }

  /**
   * Analyzes the game state, providing found insights to the given callback.
   * This may be a time-consuming operation; if run as a background thread it
   * can be stopped early by interrupting the thread.
   */
  public void analyze(Callback callback) throws InterruptedException {
    Marks.Builder builder = Marks.builder();
    boolean ok = builder.assignAll(work);
    Marks marks = builder.build();

    try {
      findErrors(marks, ok, callback);
      callback.phase(Phase.ERRORS);

      findSingletonLocations(marks, callback);
      findSingletonNumerals(marks, callback);
      callback.phase(Phase.SINGLETONS);

      callback.phase(Phase.COMPLETE);
    } catch (InterruptedException e) {
      callback.phase(Phase.INTERRUPTED);
      throw e;
    }
  }

  /**
   * Rates the given puzzle, returning a floating point number between 0 and 1,
   * where 0 means trivially easy and 1 means exceptionally difficult.  This may
   * be a time-consuming operation, and may be cut short by interrupting the
   * thread running it.
   */
  public static double rate(Grid puzzle) throws InterruptedException {
    return 0;
  }

  private static void checkInterruption() throws InterruptedException {
    if (Thread.interrupted()) throw new InterruptedException();
  }

  private void findErrors(Marks marks, boolean ok, Callback callback) throws InterruptedException {
    checkInterruption();
    if (!ok) {
      Set<Location> broken = work.getBrokenLocations();
      if (broken.size() > 0)
        callback.take(new IllegalMove(broken));
    }
  }

  private void findSingletonLocations(Marks marks, Callback callback) throws InterruptedException {
    checkInterruption();
    for (Unit unit : Unit.allUnits())
      for (Numeral num : Numeral.ALL) {
        UnitSubset set = marks.get(unit, num);
        if (set.size() == 1) {
          Location loc = set.get(0);
          if (!work.containsKey(loc))
            callback.take(new ForcedLocation(unit, num, loc));
        }
      }
  }

  private void findSingletonNumerals(Marks marks, Callback callback) throws InterruptedException {
    checkInterruption();
    for (Location loc : Location.ALL)
      if (!work.containsKey(loc)) {
        NumSet set = marks.get(loc);
        if (set.size() == 1)
          callback.take(new ForcedNumeral(loc, set.get(0)));
      }
  }
}
