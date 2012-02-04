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

/**
 * A component of an {@link UndoStack}: an action that can be undone and redone.
 *
 * @author Luke Blanshard
 */
public interface Command {
  /** Executes the command, again or for the first time. */
  void redo() throws CommandException;

  /** Puts things back the way they were before the command was done. */
  void undo() throws CommandException;

  /** Renders this command as a string for json. */
  String toJsonValue();
}
