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
package us.blanshard.sudoku.stats;

import static us.blanshard.sudoku.game.GameJson.HISTORY_TYPE;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Date;
import java.util.List;

/**
 * Gathers information about an attempt to solve a Sudoku.
 */
public class AttemptInfo {
  public static final Gson GSON = GameJson.register(new GsonBuilder()).create();

  public final String installationId;
  public final Grid clues;
  public final List<Move> history;
  public final Date stopTime;
  public final double elapsedMinutes;
  public final boolean won;

  public AttemptInfo(
      String installationId, String cluesString, String historyString, Date stopTime) {
    this.installationId = installationId;
    this.clues = Grid.fromString(cluesString);
    this.history = GSON.fromJson(historyString, HISTORY_TYPE);
    this.stopTime = stopTime;

    long elapsedMs = 0;
    if (history.size() > 0)
      elapsedMs = history.get(history.size() - 1).timestamp;
    Sudoku game = new Sudoku(clues, Sudoku.nullRegistry(), history, elapsedMs);

    this.elapsedMinutes = elapsedMs / 60000.0;
    this.won = game.getState().getGrid().getState() == Grid.State.SOLVED;
  }
}
