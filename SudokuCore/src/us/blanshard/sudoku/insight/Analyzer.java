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

import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Marks;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.game.Sudoku;

import java.util.Random;
import java.util.Set;

/**
 * Analyzes a Sudoku game state, producing a series of insights about it.  Also
 * has the capability to {@linkplain #rate rate a puzzle}.
 *
 * @author Luke Blanshard
 */
public class Analyzer {

  /**
   * The phases that {@link #analyze} may go through, and that are reported to
   * the callback as they are finished.
   */
  public enum Phase {
    START,
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
    void phase(Phase phase);
    void take(Insight insight);
  }

  private final Sudoku game;
  private final Callback callback;
  private final Grid solution;
  private volatile Grid analysisTarget;

  public Analyzer(Sudoku game, Callback callback) {
    this.game = checkNotNull(game);
    this.callback = checkNotNull(callback);
    this.solution = checkNotNull(Solver.solve(game.getPuzzle(), new Random()).solution);
    setAnalysisTargetId(-1);
  }

  /**
   * Takes a snapshot of the game's current progress in the given state for use
   * by {@link #analyze} the next time it's called.
   */
  public void setAnalysisTargetId(int stateId) {
    analysisTarget = game.getState(stateId).getGrid();
  }

  /**
   * Analyzes the {@linkplain #setAnalysisTargetId current target}, providing
   * found insights to the callback.  This may be a time-consuming operation; if
   * run as a background thread it can be stopped early by interrupting the
   * thread.
   */
  public void analyze() {
    Grid work = this.analysisTarget;
    callback.phase(Phase.START);

    Marks.Builder builder = Marks.builder();
    boolean ok = builder.assignAll(work);
    Marks marks = builder.build();

    try {
      findErrors(work, marks, ok);
      callback.phase(Phase.ERRORS);

      findSingletonLocations(work, marks);
      findSingletonNumerals(work, marks);
      callback.phase(Phase.SINGLETONS);

      // TODO(leadpipe): additional phases: locked sets, unit overlap, contradictions

      callback.phase(Phase.COMPLETE);
    } catch (InterruptedException e) {
      callback.phase(Phase.INTERRUPTED);
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

  private void findErrors(Grid work, Marks marks, boolean ok) throws InterruptedException {
    checkInterruption();
    if (!ok) {
      Set<Location> broken = work.getBrokenLocations();
      if (broken.size() > 0)
        callback.take(new IllegalMove(broken));

      for (Unit unit : Unit.allUnits())
        for (Numeral num : Numeral.ALL) {
          UnitSubset set = marks.get(unit, num);
          if (set.isEmpty())
            callback.take(new BlockedUnitNumeral(unit, num));
        }

      for (Location loc : Location.ALL) {
        NumSet set = marks.get(loc);
        if (set.isEmpty())
          callback.take(new BlockedLocation(loc));
      }
    }
  }

  private void findSingletonLocations(Grid work, Marks marks) throws InterruptedException {
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

  private void findSingletonNumerals(Grid work, Marks marks) throws InterruptedException {
    checkInterruption();
    for (Location loc : Location.ALL)
      if (!work.containsKey(loc)) {
        NumSet set = marks.get(loc);
        if (set.size() == 1)
          callback.take(new ForcedNumeral(loc, set.get(0)));
      }
  }
}
