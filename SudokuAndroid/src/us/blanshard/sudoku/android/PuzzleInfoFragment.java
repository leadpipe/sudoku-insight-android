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

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;

import us.blanshard.sudoku.android.Database.Element;
import us.blanshard.sudoku.android.Database.Game;
import us.blanshard.sudoku.android.Database.GameState;
import us.blanshard.sudoku.android.Database.Puzzle;

import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

import com.google.inject.Inject;

/**
 * @author Luke Blanshard
 */
public class PuzzleInfoFragment extends RoboFragment {
  @InjectView(R.id.info_title) TextView mTitle;
  @InjectView(R.id.info_grid) SudokuView mGrid;
  @InjectView(R.id.info_content) WebView mContent;
  @Inject Database mDb;

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    setHasOptionsMenu(false);
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
    long puzzleId = getActivity().getIntent().getExtras().getLong("puzzleId");
    new FetchPuzzle(this).execute(puzzleId);
  }

  private void setPuzzle(Database.Puzzle puzzle) {
    mTitle.setText(getString(R.string.text_info_title, puzzle._id));
    mGrid.setPuzzle(puzzle.puzzle);
    mContent.loadData(makeContentHtml(puzzle), "text/html; charset=UTF-8", null);
  }

  private String makeContentHtml(Puzzle puzzle) {
    StringBuilder sb = new StringBuilder("<ul>");
    for (Database.Game game : puzzle.games)
      appendGameHtml(game, sb.append("<li>"));
    sb.append("</ul><ul>");
    for (Database.Element element : puzzle.elements)
      appendElementHtml(element, sb.append("<li>"));
    sb.append("</ul>");
    return sb.toString();
  }

  private void appendGameHtml(Game game, StringBuilder sb) {
    sb.append(ToText.gameSummaryHtml(getActivity(), game))
        .append(getActivity().getString(R.string.text_sentence_end));
    if (game.gameState != GameState.UNSTARTED) {
      sb.append("<br>")
          .append(TextUtils.htmlEncode(getActivity().getString(
              R.string.text_game_start_time, ToText.relativeDateTime(getActivity(), game.startTime))))
          .append(getActivity().getString(R.string.text_sentence_end));
    }
    sb.append("<br><a href='us.blanshard.sudoku://replay/").append(game._id).append("'>")
        .append(TextUtils.htmlEncode(getActivity().getString(R.string.text_game_replay)))
        .append("</a>");
  }

  private void appendElementHtml(Element element, StringBuilder sb) {
    sb.append(ToText.collectionNameAndTimeHtml(getActivity(), element))
        .append(getActivity().getString(R.string.text_sentence_end));
    if (element.generatorParams != null) {
      sb.append("<br>")
          .append(TextUtils.htmlEncode(
              getActivity().getString(R.string.text_generator_params, element.generatorParams)))
          .append(getActivity().getString(R.string.text_sentence_end));
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
}
