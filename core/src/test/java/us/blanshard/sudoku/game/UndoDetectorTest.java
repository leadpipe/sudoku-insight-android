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
package us.blanshard.sudoku.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import org.junit.Test;

import javax.annotation.Nullable;

public class UndoDetectorTest {
  Sudoku game = new Sudoku(Grid.BLANK).resume();
  UndoDetector detector = new UndoDetector(game);
  long timestamp;

  private void check(int row, int col, int numOrZero, int stateId, boolean isUndo, boolean isRedo) {
    Location loc = Location.of(row, col);
    @Nullable Numeral num = Numeral.numeral(numOrZero);
    Move move = Move.make(loc, num, ++timestamp, stateId);
    assertEquals(isUndo, detector.isUndo(move));
    assertEquals(isRedo, detector.isRedo(move));
    assertEquals(isUndo | isRedo, detector.isUndoOrRedo(move));
    int pos = detector.getPosition();
    int size = detector.getStackSize();

    assertTrue(game.move(move));

    if (isUndo) assertEquals(pos - 1, detector.getPosition());
    if (isRedo) assertEquals(pos + 1, detector.getPosition());
    if (isUndo | isRedo)
      assertEquals(size, detector.getStackSize());
    else {
      assertEquals(pos + 1, detector.getPosition());
      assertEquals(pos + 1, detector.getStackSize());
    }
  }

  @Test public void test() {
    check(1, 1, 1, -1, false, false);
    check(1, 1, 0, -1, true, false);
    check(1, 1, 1, -1, false, true);
    check(1, 1, 0, 0, false, false);
    check(1, 1, 5, -1, false, false);
    check(1, 1, 1, -1, true, false);
    check(1, 1, 0, 0, true, false);
    check(1, 1, 0, -1, true, false);
  }
}
