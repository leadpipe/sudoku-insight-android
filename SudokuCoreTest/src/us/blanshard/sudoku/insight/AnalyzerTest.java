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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static us.blanshard.sudoku.core.NumSetTest.set;

import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Marks;
import us.blanshard.sudoku.core.NumSetTest;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.game.Sudoku;

import com.google.common.collect.Lists;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

/**
 * @author Luke Blanshard
 */
@RunWith(MockitoJUnitRunner.class)
public class AnalyzerTest {

  @Mock Analyzer.Callback callback;

  static class Collector implements Analyzer.Callback {
    public final List<Insight> taken = Lists.newArrayList();
    @Override public void phase(Analyzer.Phase phase) {}
    @Override public void take(Insight insight) {
      taken.add(insight);
    }
  }

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

    verify(callback, never()).take(isA(Conflict.class));
    verify(callback, never()).take(isA(BarredNum.class));
    verify(callback, never()).take(isA(BarredLoc.class));
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

    verify(callback, never()).take(isA(Conflict.class));
    verify(callback, never()).take(isA(BarredNum.class));
    verify(callback, never()).take(isA(BarredLoc.class));
  }

  @Test public void correctSets() {
    Grid grid = Grid.fromString(
        " 6 . 1 | 2 . 9 | . 4 5 " +
        " 2 . 8 | . . 4 | . . 6 " +
        " . . . | 7 6 8 | . . 2 " +
        "-------+-------+-------" +
        " 5 6 . | 9 . 1 | 3 2 8 " +
        " 8 . 9 | 6 . . | 4 . . " +
        " . . 7 | 8 . . | . 6 . " +
        "-------+-------+-------" +
        " 9 . 6 | . 2 7 | . . . " +
        " 7 . . | 3 8 6 | 2 . . " +
        " 1 8 2 | 4 9 5 | 6 7 3 ");

    Marks marks = Marks.fromString(
        "  6    37   1   |  2    3    9   |  78   4    5   " +
        "  2   379   8   |  15   1    4   |  7    39   6   " +
        "  34  3459 345  |  7    6    8   |  19  139   2   " +
        "----------------+----------------+----------------" +
        "  5    6    4   |  9    47   1   |  3    2    8   " +
        "  8    12   9   |  6    57   23  |  4    15   7   " +
        "  34   12   7   |  8    45   23  | 159   6    19  " +
        "----------------+----------------+----------------" +
        "  9    3    6   |  1    2    7   |  58   8    4   " +
        "  7    45   45  |  3    8    6   |  2    19   19  " +
        "  1    8    2   |  4    9    5   |  6    7    3   ");

    Collector collector = new Collector();

    Analyzer.findOverlapsAndSets(grid, marks, collector);

    Unit c2 = Column.of(2);
    assertTrue(collector.taken.contains(new LockedSet(grid, set(1,2), locs(c2, 5, 6), false)));
    assertTrue(collector.taken.contains(new LockedSet(grid, set(4,5), locs(c2, 3, 8), false)));
  }

  private void setAll(Sudoku.State state, Grid grid) {
    for (Location loc : grid.keySet())
      state.set(loc, grid.get(loc));
  }

  private static UnitSubset locs(Unit unit, int... nums) {
    return UnitSubset.ofBits(unit, NumSetTest.set(nums).bits);
  }
}
