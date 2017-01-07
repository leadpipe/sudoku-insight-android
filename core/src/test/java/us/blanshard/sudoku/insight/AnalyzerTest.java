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
package us.blanshard.sudoku.insight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static us.blanshard.sudoku.core.NumSetTest.set;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSetTest;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.game.Sudoku;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

/**
 * @author Luke Blanshard
 */
@RunWith(MockitoJUnitRunner.class)
public class AnalyzerTest {

  @Mock Analyzer.Callback callback;

  static class Collector implements Analyzer.Callback {
    public final List<Insight> taken = Lists.newArrayList();
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

    boolean complete = Analyzer.analyze(Marks.fromGrid(state), callback);

    assertTrue(complete);
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

    boolean complete = Analyzer.analyze(Marks.fromGrid(state), callback);

    assertTrue(complete);
    verify(callback, never()).take(isA(Conflict.class));
    verify(callback, never()).take(isA(BarredNum.class));
    verify(callback, never()).take(isA(BarredLoc.class));
  }

  @Test public void correctSets() {
    Marks marks = Marks.fromString(
        "  6!   37   1!  |  2!   3    9!  |  78   4!   5!  " +
        "  2!  379   8!  |  15   1    4!  |  7    39   6!  " +
        "  34  3459 345  |  7!   6!   8!  |  19  139   2!  " +
        "----------------+----------------+----------------" +
        "  5!   6!   4   |  9!   47   1!  |  3!   2!   8!  " +
        "  8!   12   9!  |  6!   57   23  |  4!   15   7   " +
        "  34   12   7!  |  8!   45   23  | 159   6!   19  " +
        "----------------+----------------+----------------" +
        "  9!   3    6!  |  1    2!   7!  |  58   8    4   " +
        "  7!   45   45  |  3!   8!   6!  |  2!   19   19  " +
        "  1!   8!   2!  |  4!   9!   5!  |  6!   7!   3!  ");

    Collector collector = new Collector();

    Analyzer.findOverlapsAndSets(marks, collector);

    Unit c2 = Column.of(2);
    assertTrue(collector.taken.contains(new LockedSet(set(1,2), locs(c2, 5, 6), false)));
    assertTrue(collector.taken.contains(new LockedSet(set(4,5), locs(c2, 3, 8), false)));
  }

  @Test public void correctSets2() {
    Grid grid = Grid.fromString(
        " . 1 4 | . 9 . | . 5 . " +
        " . . . | 6 . . | . . 1 " +
        " 2 9 6 | 5 1 4 | 7 8 3 " +
        "-------+-------+-------" +
        " . 4 . | . 5 1 | 3 6 . " +
        " 8 . 1 | 3 . 2 | . 7 . " +
        " . . 3 | . 7 . | . 1 . " +
        "-------+-------+-------" +
        " . . 5 | . . . | 1 . 7 " +
        " . . 9 | 1 . 5 | . . . " +
        " 1 3 . | . . . | 5 . . ");

    Collector collector = new Collector();
    Marks marks = Marks.fromGrid(grid);

    Analyzer.analyze(marks, collector);

    Unit b3 = Block.of(3);
    assertTrue(collector.taken.contains(new LockedSet(set(4,9), locs(b3, 4, 5), false)));
    LockedSet set26 = new LockedSet(set(2,6), locs(b3, 1, 3), true);
    assertTrue(collector.taken.contains(set26));
    LockedSet set378 = new LockedSet(set(3,7,8), locs(Row.of(1), 1, 4, 6), false);
    assertTrue(collector.taken.contains(set378));
    Implication imp = (Implication) Iterables.find(collector.taken, Predicates.instanceOf(Implication.class));
    assertEquals(new ForcedLoc(Block.of(2), Numeral.of(2), Location.of(2, 5)), imp.getConsequent());

    Implication imp2 = (Implication) Analyzer.minimize(marks, imp);
    assertEquals(Lists.newArrayList(set26), imp2.getAntecedents());
  }

  @Test public void correctImplication() {
    Grid grid = Grid.fromString(
        " 9 . 7 | . 3 . | 4 1 5 " +
        " . . . | . 7 1 | . 9 . " +
        " 3 1 . | . 5 9 | 2 6 7 " +
        "-------+-------+-------" +
        " 1 . . | 9 . 4 | 6 7 . " +
        " 4 9 2 | . . 7 | . . . " +
        " 6 7 . | 1 . . | . 4 . " +
        "-------+-------+-------" +
        " 5 . 9 | 7 . . | . . . " +
        " . 4 1 | . . 6 | . . . " +
        " . . . | . . . | . . . ");

    Collector collector = new Collector();
    Marks marks = Marks.fromGrid(grid);

    Analyzer.analyze(marks, collector, new Analyzer.Options(false, false));

    boolean found = false;
    for (Insight i : collector.taken) {
      if (i.getImpliedAssignment() == Assignment.of(Location.of(2, 1), Numeral.of(2))) {
        Implication imp = (Implication) Analyzer.minimize(marks, i);
        if (imp.getConsequent().type == Insight.Type.FORCED_NUMERAL) {
          found = true;
          assertEquals(Arrays.asList(
                  new Overlap(Block.of(3), Numeral.of(8), Row.of(2).intersect(Block.of(3)))),
              imp.getAntecedents());
        }
      }
    }

    assertTrue(found);
  }

  @Test public void overlappingUnit() {
    Unit u = Block.of(2);
    assertNull(Analyzer.findOverlappingUnit(locs(u,1,6)));
    assertEquals(Row.of(1), Analyzer.findOverlappingUnit(locs(u,1,2)));
    assertEquals(Row.of(2), Analyzer.findOverlappingUnit(locs(u,4,6)));
    assertEquals(Row.of(3), Analyzer.findOverlappingUnit(locs(u,8,9)));
    assertEquals(Row.of(1), Analyzer.findOverlappingUnit(locs(u,1,2,3)));
    assertEquals(Column.of(4), Analyzer.findOverlappingUnit(locs(u,1,4)));
    assertEquals(Column.of(5), Analyzer.findOverlappingUnit(locs(u,5,8)));
    assertEquals(Column.of(6), Analyzer.findOverlappingUnit(locs(u,3,9)));
    assertEquals(Column.of(5), Analyzer.findOverlappingUnit(locs(u,2,5,8)));

    u = Row.of(4);
    assertNull(Analyzer.findOverlappingUnit(locs(u,3,4)));
    assertEquals(Block.of(4), Analyzer.findOverlappingUnit(locs(u,1,3)));
    assertEquals(Block.of(5), Analyzer.findOverlappingUnit(locs(u,5,6)));
    assertEquals(Block.of(6), Analyzer.findOverlappingUnit(locs(u,7,8)));
    assertEquals(Block.of(6), Analyzer.findOverlappingUnit(locs(u,7,8,9)));

    u = Column.of(7);
    assertNull(Analyzer.findOverlappingUnit(locs(u,3,4)));
    assertEquals(Block.of(3), Analyzer.findOverlappingUnit(locs(u,2,3)));
    assertEquals(Block.of(6), Analyzer.findOverlappingUnit(locs(u,4,5)));
    assertEquals(Block.of(9), Analyzer.findOverlappingUnit(locs(u,7,9)));
    assertEquals(Block.of(6), Analyzer.findOverlappingUnit(locs(u,4,5,6)));
  }

  private void setAll(Sudoku.State state, Grid grid) {
    for (Location loc : grid.keySet())
      state.set(loc, grid.get(loc));
  }

  private static UnitSubset locs(Unit unit, int... nums) {
    return UnitSubset.ofBits(unit, NumSetTest.set(nums).bits);
  }
}