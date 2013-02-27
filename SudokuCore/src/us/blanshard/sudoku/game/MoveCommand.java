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

import static com.google.common.base.Preconditions.checkNotNull;
import static us.blanshard.sudoku.core.Numeral.number;
import static us.blanshard.sudoku.core.Numeral.numeral;
import static us.blanshard.sudoku.game.GameJson.JOINER;

import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import java.util.Iterator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * An undoable {@link Command} that tracks a single move in a {@link Sudoku}
 * game.  Note that undoing a MoveCommand does not remove the original {@link
 * Move} from the game's history.
 *
 * @author Luke Blanshard
 */
@NotThreadSafe
public class MoveCommand implements Command {
  final Sudoku.State state;
  final Location loc;
  final Numeral num;
  final Numeral prevNum;

  public MoveCommand(Sudoku.State state, Location loc, @Nullable Numeral num) {
    this(state, loc, num, state.get(loc));
  }

  MoveCommand(Sudoku.State state, Location loc, @Nullable Numeral num, @Nullable Numeral prevNum) {
    this.state = checkNotNull(state);
    this.loc = checkNotNull(loc);
    this.num = num;
    this.prevNum = prevNum;
  }

  static MoveCommand fromJsonValues(Iterator<String> values, Sudoku game) {
    int id = Integer.parseInt(values.next());
    Location loc = Location.of(Integer.parseInt(values.next()));
    Numeral num = numeral(Integer.parseInt(values.next()));
    Numeral prevNum = numeral(Integer.parseInt(values.next()));
    return new MoveCommand(game.getState(id), loc, num, prevNum);
  }

  public Location getLocation() {
    return loc;
  }

  @Override public void redo() throws CommandException {
    set(num);
  }

  @Override public void undo() throws CommandException {
    set(prevNum);
  }

  @Override public String toJsonValue() {
    return JOINER.join("move", state.getId(), loc.index, number(num), number(prevNum));
  }

  private void set(Numeral num) throws CommandException {
    if (!state.set(loc, num)) {
      throw new CommandException("Unable to modify location " + loc);
    }
  }
}
