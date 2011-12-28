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

import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import javax.annotation.Nullable;

/**
 * An undoable {@link Command} that tracks a single move in a {@link Sudoku}
 * game.  Note that undoing a MoveCommand does not remove the original {@link
 * Move} from the game's history.
 *
 * @author Luke Blanshard
 */
public class MoveCommand implements Command {
  private final Sudoku.State state;
  private final Location loc;
  private final Numeral num;
  private final Numeral prevNum;

  public MoveCommand(Sudoku.State state, Location loc, @Nullable Numeral num) {
    this.state = checkNotNull(state);
    this.loc = checkNotNull(loc);
    this.num = num;
    this.prevNum = state.get(loc);
  }

  @Override public void redo() throws CommandException {
    set(num);
  }

  @Override public void undo() throws CommandException {
    set(prevNum);
  }

  private void set(Numeral num) throws CommandException {
    if (!state.set(loc, num)) {
      throw new CommandException("Unable to modify location " + loc);
    }
  }
}
