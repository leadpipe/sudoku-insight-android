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
package us.blanshard.sudoku.android;

import static java.util.concurrent.TimeUnit.MINUTES;

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;

import us.blanshard.sudoku.android.Database.Element;
import us.blanshard.sudoku.android.Database.Game;
import us.blanshard.sudoku.android.Database.GameState;
import us.blanshard.sudoku.android.Database.Puzzle;
import us.blanshard.sudoku.android.WorkerFragment.Independence;
import us.blanshard.sudoku.android.WorkerFragment.Priority;
import us.blanshard.sudoku.android.actionbarcompat.ActionBarHelper;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;

import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

import org.json.JSONException;

import java.util.List;

/**
 * @author Luke Blanshard
 */
public class PuzzleInfoFragment extends RoboFragment {
  @InjectView(R.id.info_grid) SudokuView mGrid;
  @InjectView(R.id.info_content) WebView mContent;
  @Inject Database mDb;
  @Inject ActionBarHelper mActionBarHelper;
  private Database.Puzzle mPuzzle;

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    setHasOptionsMenu(true);
    // Sadly, the WebView reads and writes disk when it's created.
    ThreadPolicy policy = StrictMode.allowThreadDiskWrites();
    try {
      return inflater.inflate(R.layout.info, container, true);
    } finally {
      StrictMode.setThreadPolicy(policy);
    }
  }

  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    long puzzleId = getActivity().getIntent().getExtras().getLong(Extras.PUZZLE_ID);
    new FetchPuzzle(this).execute(puzzleId);
  }

  @Override public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
    menuInflater.inflate(R.menu.info, menu);
    super.onCreateOptionsMenu(menu, menuInflater);
  }

  @Override public void onPrepareOptionsMenu(Menu menu) {
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      switch (item.getItemId()) {
        case R.id.menu_play:
          item.setEnabled(mPuzzle != null);
          break;
      }
    }
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_play:
        new Play(this).execute();
        return true;

      default:
        return false;
    }
  }

  private void setPuzzle(Database.Puzzle puzzle) {
    mPuzzle = puzzle;
    getActivity().setTitle(getString(R.string.text_info_title, puzzle._id));
    mGrid.setPuzzle(puzzle.puzzle);
    mContent.loadData(makeContentHtml(puzzle), "text/html; charset=UTF-8", null);
    mContent.setBackgroundColor(0);  // Make the background transparent
    mActionBarHelper.invalidateOptionsMenu();
  }

  private String makeContentHtml(Puzzle puzzle) {
    StringBuilder sb = new StringBuilder("<ul>");
    for (Database.Game game : Lists.reverse(puzzle.games))
      appendGameHtml(game, sb.append("<li>"));
    sb.append("</ul><ul>");
    for (Database.Element element : puzzle.elements)
      appendElementHtml(element, sb.append("<li>"));
    sb.append("</ul>");
    return sb.toString();
  }

  private void appendGameHtml(Game game, StringBuilder sb) {
    sb.append(ToText.gameSummaryHtml(getActivity(), game, true))
        .append(getString(R.string.text_sentence_end));
    if (game.gameState != GameState.UNSTARTED) {
      try {
        List<Move> history = GameJson.toHistory(game.history);
        int maxTrailId = -1;
        for (Move m : history)
          if (m.id > maxTrailId)
            maxTrailId = m.id;
        sb.append("<br>").append(TextUtils.htmlEncode(
            getString(R.string.text_move_and_trail_counts, history.size(), maxTrailId + 1)));
      } catch (JSONException e) {
        Log.e("PuzzleInfoFragment", "Unexpected json problem", e);
      }
      if (game.lastTime - game.startTime > game.elapsedMillis + MINUTES.toMillis(5)) {
        sb.append("<br>")
            .append(TextUtils.htmlEncode(getString(
                R.string.text_game_start_time,
                ToText.relativeDateTime(getActivity(), game.startTime))));
      }
    }
    sb.append("<br><a href='us.blanshard.sudoku://replay/").append(game._id).append("'>")
        .append(TextUtils.htmlEncode(getString(R.string.text_game_replay)))
        .append("</a>");
  }

  private void appendElementHtml(Element element, StringBuilder sb) {
    sb.append(ToText.collectionNameAndTimeHtml(getActivity(), element))
        .append(getString(R.string.text_sentence_end));
    if (element.generatorParams != null) {
      sb.append("<br>")
          .append(TextUtils.htmlEncode(
              getString(R.string.text_generator_params, element.generatorParams)))
          .append(getString(R.string.text_sentence_end));
    }
  }

  private static class FetchPuzzle extends WorkerFragment.Task<PuzzleInfoFragment, Long, Void, Database.Puzzle> {
    private final Database mDb;

    FetchPuzzle(PuzzleInfoFragment fragment) {
      super(fragment);
      this.mDb = fragment.mDb;
    }

    @Override protected Database.Puzzle doInBackground(Long... params) {
      return mDb.getFullPuzzle(params[0]);
    }

    @Override protected void onPostExecute(PuzzleInfoFragment fragment, Database.Puzzle puzzle) {
      fragment.setPuzzle(puzzle);
    }
  }

  private static class Play extends WorkerFragment.Task<PuzzleInfoFragment, Void, Void, Long> {
    private final Database mDb;
    private final Long mPuzzleId;

    Play(PuzzleInfoFragment fragment) {
      super(fragment, Priority.FOREGROUND, Independence.DEPENDENT);
      this.mDb = fragment.mDb;
      this.mPuzzleId = fragment.mPuzzle._id;
    }

    @Override protected Long doInBackground(Void... params) {
      return mDb.getOpenGameForPuzzle(mPuzzleId)._id;
    }

    @Override protected void onPostExecute(PuzzleInfoFragment fragment, Long gameId) {
      Intent intent = new Intent(fragment.getActivity(), SudokuActivity.class);
      intent.putExtra(Extras.GAME_ID, gameId);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      fragment.startActivity(intent);
    }
  }
}
