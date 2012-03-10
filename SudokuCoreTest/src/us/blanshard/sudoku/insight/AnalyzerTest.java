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

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.game.Sudoku;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Luke Blanshard
 */
@RunWith(MockitoJUnitRunner.class)
public class AnalyzerTest {

  @Mock Analyzer.Callback callback;

  @Test public void tooAggressiveErrors() {
    Grid puzzle = Grid.fromString(
        " . . 8 | . 6 . | . 4 ." +
        " . . . | . . 2 | 7 9 ." +
        " 2 . . | 5 . . | . . ." +
        "-------+-------+------" +
        " . . 9 | . 3 . | . . 5" +
        " 5 . . | . . 8 | . . ." +
        " . 8 . | . . . | 1 . ." +
        "-------+-------+------" +
        " . 2 . | . . 5 | 8 . ." +
        " 4 1 . | . . . | . . 9" +
        " . . . | 3 . . | 4 7 .");

    Grid state = Grid.fromString(
        " . . . | . . 3 | 5 . 2" +
        " . . . | . . . | . . ." +
        " . . . | . . . | 3 . ." +
        "-------+-------+------" +
        " . . . | 2 . . | 6 8 ." +
        " . . . | . . . | 9 . ." +
        " . . . | . 5 . | . . ." +
        "-------+-------+------" +
        " . . . | . . . | . . 3" +
        " . . 3 | . . . | 2 5 ." +
        " 8 . . | . 2 . | . . .");

    Grid trail = Grid.fromString(
        " . . . | . . . | . . ." +
        " . . . | . . . | . . ." +
        " . . . | . . . | . . ." +
        "-------+-------+------" +
        " . . . | . . . | . . ." +
        " . . . | . . . | . . ." +
        " . . . | . . . | . . ." +
        "-------+-------+------" +
        " . . . | . . . | . 1 ." +
        " . . . | . . . | . . ." +
        " . . . | . . . | . . .");

    Sudoku game = new Sudoku(puzzle, Sudoku.nullRegistry()).resume();
    setAll(game.getState(), state);
    setAll(game.getTrail(0), trail);

    Analyzer analyzer = new Analyzer(game, callback);
    analyzer.setAnalysisTargetId(0);

    analyzer.analyze();

    verify(callback, never()).take(isA(IllegalMove.class));
    verify(callback, never()).take(isA(BlockedUnitNumeral.class));
    verify(callback, never()).take(isA(BlockedLocation.class));
  }

  @Test public void unexpectedMoveInsight() {
    Grid puzzle = Grid.fromString(
        " . . . | . . . | 7 . ." +
        " 6 . . | 4 . . | . 5 3" +
        " . . . | 1 . 8 | . . 2" +
        "-------+-------+------" +
        " 5 3 8 | . . . | 9 6 ." +
        " . . . | . . . | . . ." +
        " 4 6 7 | . . . | . . 5" +
        "-------+-------+------" +
        " . . . | 8 . . | . . 7" +
        " 2 4 . | 6 . 5 | . . 8" +
        " . . 3 | . . . | . . .");

    Grid state = Grid.fromString(
        " . . . | . . . | . . 9" +
        " . . . | . . 7 | . . ." +
        " . . . | . . . | 6 4 ." +
        "-------+-------+------" +
        " . . . | 7 . . | . . ." +
        " . . . | . . . | . 7 ." +
        " . . . | . . . | . . ." +
        "-------+-------+------" +
        " . 5 6 | . . . | 4 . ." +
        " . . . | . 7 . | . . ." +
        " . . . | . . . | 5 . 6");

    Sudoku game = new Sudoku(puzzle, Sudoku.nullRegistry()).resume();
    setAll(game.getState(), state);

    Analyzer analyzer = new Analyzer(game, callback);

    analyzer.analyze();

    verify(callback, never()).take(isA(IllegalMove.class));
    verify(callback, never()).take(isA(BlockedUnitNumeral.class));
    verify(callback, never()).take(isA(BlockedLocation.class));
  }

  private void setAll(Sudoku.State state, Grid grid) {
    for (Location loc : grid.keySet())
      state.set(loc, grid.get(loc));
  }
}
