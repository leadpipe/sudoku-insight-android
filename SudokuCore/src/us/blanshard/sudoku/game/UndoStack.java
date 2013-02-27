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
import static com.google.common.base.Preconditions.checkPositionIndex;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A stack of {@linkplain Command commands} that can be undone or redone.
 *
 * @author Luke Blanshard
 */
@NotThreadSafe
public class UndoStack {

  final List<Command> commands;
  private int position;

  public UndoStack() {
    this(Lists.<Command>newArrayList(), 0);
  }

  UndoStack(List<Command> commands, int position) {
    this.commands = checkNotNull(commands);
    this.position = checkPositionIndex(position, commands.size());
  }

  public List<Command> getCommands() {
    return Collections.unmodifiableList(commands);
  }

  public int getPosition() {
    return position;
  }

  /** Returns the command last performed, either redone (if forward) or undone. */
  public Command getLastCommand(boolean forward) {
    checkState(forward ? canUndo() : canRedo());
    return commands.get(forward ? position - 1 : position);
  }

  /** Pushes the given command on the stack, and executes it. */
  public void doCommand(Command command) throws CommandException {
    command.redo();  // Execute first, so an exception won't affect the stack.
    if (position < commands.size()) commands.subList(position, commands.size()).clear();
    commands.add(command);
    position = commands.size();
  }

  /** Tells whether there is a command available to be undone. */
  public boolean canUndo() {
    return position > 0;
  }

  /** Undoes the previous command. */
  public void undo() throws CommandException {
    checkState(canUndo());
    commands.get(position - 1).undo();
    // Decrement after undoing so an exception won't affect the stack.
    --position;
  }

  /** Tells whether there is a command available to be redone. */
  public boolean canRedo() {
    return position < commands.size();
  }

  /** Redoes the next command. */
  public void redo() throws CommandException {
    checkState(canRedo());
    commands.get(position).redo();
    // Increment after redoing so an exception won't affect the stack.
    ++position;
  }
}
