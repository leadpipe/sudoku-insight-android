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

import static org.junit.Assert.assertEquals;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author Luke Blanshard
 */
@RunWith(JUnit4.class)
@SuppressWarnings("deprecation")
public class PatternTest {
  static Grid puzzle = Grid.fromString(
      " . . . | . . . | 7 . 9" +
      " 6 . . | 4 . 7 | . 5 3" +
      " . . . | 1 . 8 | 6 4 2" +
      "-------+-------+------" +
      " 5 3 8 | 7 . . | 9 6 ." +
      " . . . | . . . | . 7 ." +
      " 4 6 7 | . . . | . . 5" +
      "-------+-------+------" +
      " . 5 6 | 8 . . | 4 . 7" +
      " 2 4 . | 6 7 5 | . . 8" +
      " . . 3 | . . . | 5 . 6");

  @Test public void barredLoc() {
    Grid grid = puzzle.toBuilder().put(Location.of(5, 5), Numeral.of(4)).build();
    Pattern.BarredLoc pattern = Pattern.barredLocationOrNull(grid, Location.of(5, 9));
    assertEquals(1, pattern.getNumImplicit());
    assertEquals(4, pattern.getNumInLinesOnly());
    assertEquals(1, pattern.getNumInMinorLineOnly());
    assertEquals("BarredLoc:1,4,1", pattern.toString());
  }


  @SuppressWarnings("unused")
  private static final Grid hardPuzzle = Grid.fromString(
      " . 1 . | 2 . . | . . ." +
      " . . . | 6 8 4 | . . ." +
      " 7 . . | . 9 . | 8 . ." +
      "-------+-------+------" +
      " . 5 4 | . . . | . . 7" +
      " 3 8 . | . . . | . 9 1" +
      " 6 . . | . . . | 4 5 ." +
      "-------+-------+------" +
      " . . 5 | . . . | 2 . 6" +
      " . . . | 4 6 3 | . . ." +
      " . . . | . . . | . 4 .");

}
