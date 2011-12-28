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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * A stack of {@linkplain Command commands} that can be undone or redone.
 *
 * @author Luke Blanshard
 */
public class UndoStack {

  private final List<Command> commands = Lists.newArrayList();
  private int pointer;

  /** Pushes the given command on the stack, and executes it. */
  public void doCommand(Command command) throws CommandException {
    command.redo();  // Execute first, so an exception won't affect the stack.
    if (pointer < commands.size()) commands.subList(pointer, commands.size()).clear();
    commands.add(command);
    pointer = commands.size();
  }

  /** Tells whether there is a command available to be undone. */
  public boolean canUndo() {
    return pointer > 0;
  }

  /** Undoes the previous command. */
  public void undo() throws CommandException {
    checkState(pointer > 0);
    commands.get(pointer - 1).undo();
    // Decrement after undoing so an exception won't affect the stack.
    --pointer;
  }

  /** Tells whether there is a command available to be redone. */
  public boolean canRedo() {
    return pointer < commands.size();
  }

  /** Redoes the next command. */
  public void redo() throws CommandException {
    checkState(pointer < commands.size());
    commands.get(pointer).redo();
    // Increment after redoing so an exception won't affect the stack.
    ++pointer;
  }
}
