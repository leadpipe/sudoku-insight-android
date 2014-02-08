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

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Tracks the moves made to a game and tells you if a move is an undo or a redo.
 */
@NotThreadSafe
public class UndoDetector {
  private final List<MoveCommand> stack = Lists.newArrayList();
  private int position = 0;

  public UndoDetector(Sudoku game) {
    if (game.getHistory().size() > 0)
      throw new IllegalArgumentException("Only unstarted games can be used");
    game.getListenerRegistry().addListener(new Sudoku.Adapter() {
      private MoveCommand command;
      @Override public void moveComing(Sudoku game, Move move) {
        command = move.toCommand(game);
      }
      @Override public void moveMade(Sudoku game, Move move) {
        apply(move, command);
      }
    });
  }

  /**
   * Tells whether the given move, if made next, would be equivalent to an undo
   * or redo of a previous move.
   */
  public boolean isUndoOrRedo(Move move) {
    return isUndo(move) || isRedo(move);
  }

  /**
   * Tells whether the given move, if made next, would be equivalent to an undo
   * of a previous move.
   */
  public boolean isUndo(Move move) {
    if (position == 0) return false;
    MoveCommand prev = stack.get(position - 1);
    return prev.getStateId() == move.trailId
        && prev.getLocation().equals(move.getLocation())
        && Objects.equal(prev.getPreviousNumeral(), move.getNumeral());
  }

  /**
   * Tells whether the given move, if made next, would be equivalent to a redo
   * of a previous move.
   */
  public boolean isRedo(Move move) {
    if (position >= stack.size()) return false;
    MoveCommand next = stack.get(position);
    return next.getStateId() == move.trailId
        && next.getLocation().equals(move.getLocation())
        && Objects.equal(next.getNextNumeral(), move.getNumeral());
  }

  // For testing
  int getPosition() {
    return position;
  }

  // For testing
  int getStackSize() {
    return stack.size();
  }

  private void apply(Move move, MoveCommand command) {
    if (isUndo(move)) {
      --position;
    } else if (isRedo(move)) {
      ++position;
    } else {
      stack.subList(position, stack.size()).clear();
      stack.add(command);
      position = stack.size();
    }
  }
}
