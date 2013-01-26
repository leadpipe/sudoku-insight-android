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
import static us.blanshard.sudoku.gen.Generator.NAME_KEY;

import us.blanshard.sudoku.android.Database.Attempt;
import us.blanshard.sudoku.android.Database.AttemptState;
import us.blanshard.sudoku.android.Database.Element;
import us.blanshard.sudoku.android.WorkerFragment.Independence;
import us.blanshard.sudoku.android.WorkerFragment.Priority;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.gen.Generator;

import android.annotation.SuppressLint;
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
import android.webkit.WebViewClient;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;

/**
 * @author Luke Blanshard
 */
@SuppressLint("SetJavaScriptEnabled")
public class PuzzleInfoFragment extends FragmentBase implements OnCheckedChangeListener {
  private static final String TAG = "PuzzleInfoFragment";
  private ActivityCallback mCallback;
  private JsonObject mProperties;
  private SudokuView mGrid;
  private TextView mPuzzleId;
  private View mVoteLayout;
  private RadioGroup mVote;
  private WebView mDetails;
  private Database.Puzzle mPuzzle;

  /**
   * Every activity that hosts this fragment must implement this callback.
   */
  public interface ActivityCallback {
    /** Shows the given collection in the puzzle list. */
    void showCollection(long collectionId);

    /** Informs the activity that the user voted on this puzzle. */
    void voted();
  }

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
    mCallback = (ActivityCallback) getActivity();
    mGrid = (SudokuView) getActivity().findViewById(R.id.info_grid);
    mPuzzleId = (TextView) getActivity().findViewById(R.id.info_puzzle_id);
    mVoteLayout = getActivity().findViewById(R.id.vote_layout);
    mVote = (RadioGroup) getActivity().findViewById(R.id.vote_group);
    mVote.setOnCheckedChangeListener(this);
    mDetails = (WebView) getActivity().findViewById(R.id.info_details);
    mDetails.setBackgroundColor(0);  // Makes the background transparent
    mDetails.setWebViewClient(new LinkHandler());
  }

  public void setPuzzleId(long puzzleId) {
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
        if (ImproperDialog.isNeeded(mPrefs, mProperties)) {
          new ImproperDialog() {
            @Override protected void okayed() {
              new Play(PuzzleInfoFragment.this).execute();
            }
          }.show(getFragmentManager());
        } else {
          new Play(this).execute();
        }
        return true;

      default:
        return false;
    }
  }

  @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
    int vote = 0;
    switch (checkedId) {
      case R.id.radio_vote_down: vote = -1; break;
      case R.id.radio_vote_up: vote = 1; break;
    }
    if (canVote()) {
      ThreadPolicy policy = StrictMode.allowThreadDiskWrites();
      try {
        mDb.vote(mPuzzle._id, vote);
      } finally {
        StrictMode.setThreadPolicy(policy);
      }
      mCallback.voted();
    }
  }

  private boolean canVote() {
    if (mPuzzle != null)
      for (Database.Attempt attempt : mPuzzle.attempts)
        if (attempt.attemptState == AttemptState.FINISHED)
          return true;
    return false;
  }

  private void setPuzzle(Database.Puzzle puzzle) {
    mPuzzle = puzzle;
    mProperties = new JsonParser().parse(puzzle.properties).getAsJsonObject();
    mGrid.setPuzzle(puzzle.clues);
    mPuzzleId.setText(makeIdString());
    mVoteLayout.setVisibility(canVote() ? View.VISIBLE : View.GONE);
    mVote.check(puzzle.vote == 0 ? R.id.radio_no_vote
        : puzzle.vote < 0 ? R.id.radio_vote_down : R.id.radio_vote_up);
    mDetails.loadData(makeContentHtml(), "text/html; charset=UTF-8", null);
    mDetails.setBackgroundColor(0);  // Make the background transparent
    getActivity().invalidateOptionsMenu();
  }

  private String makeIdString() {
    StringBuilder sb = new StringBuilder();
    if (mProperties.has(NAME_KEY))
      sb.append(getString(R.string.text_puzzle_full_name,
          mProperties.get(NAME_KEY).getAsString(), mPuzzle._id));
    else
      sb.append(getString(R.string.text_puzzle_full_number, mPuzzle._id));
    if (mPuzzle.source != null)
      sb.append("\n").append(getString(R.string.text_source, mPuzzle.source));
    return sb.toString();
  }

  private String makeContentHtml() {
    StringBuilder sb = new StringBuilder("<ul>");
    for (Database.Attempt attempt : Lists.reverse(mPuzzle.attempts))
      appendAttemptHtml(attempt, sb.append("<li>"));
    sb.append("</ul><ul>");
    for (Database.Element element : mPuzzle.elements)
      appendElementHtml(element, sb.append("<li>"));
    sb.append("</ul><ul>");
    appendOtherProperties(sb);
    sb.append("</ul>");
    return sb.toString();
  }

  private void appendAttemptHtml(Attempt attempt, StringBuilder sb) {
    sb.append(ToText.attemptSummaryHtml(getActivity(), attempt, true))
        .append(getString(R.string.text_sentence_end));
    if (attempt.attemptState != AttemptState.UNSTARTED) {
      List<Move> history = GameJson.toHistory(attempt.history);
      int maxTrailId = -1;
      for (Move m : history)
        if (m.trailId > maxTrailId)
          maxTrailId = m.trailId;
      sb.append("<br>").append(TextUtils.htmlEncode(
          getString(R.string.text_move_and_trail_counts, history.size(), maxTrailId + 1)));
      if (attempt.lastTime - attempt.startTime > attempt.elapsedMillis + MINUTES.toMillis(5)) {
        sb.append("<br>")
            .append(TextUtils.htmlEncode(getString(
                R.string.text_attempt_start_time,
                ToText.relativeDateTime(getActivity(), attempt.startTime))));
      }
    }
    if (attempt.attemptState.isComplete())
      sb.append("<br><a href='" + Uris.REPLAY_URI_PREFIX).append(attempt._id).append("'>")
          .append(TextUtils.htmlEncode(getString(R.string.text_attempt_replay)))
          .append("</a>");
    if (attempt.replayTime > 0)
      sb.append("<br>")
          .append(TextUtils.htmlEncode(getString(
              R.string.text_attempt_last_replayed,
              ToText.relativeDateTime(getActivity(), attempt.replayTime))));
  }

  private void appendElementHtml(Element element, StringBuilder sb) {
    sb.append(ToText.collectionNameAndTimeHtml(getActivity(), element))
        .append(getString(R.string.text_sentence_end));
  }

  private void appendOtherProperties(StringBuilder sb) {
    if (canVote() && mProperties.has(Generator.NUM_SOLUTIONS_KEY))
      sb.append("<li>").append(getString(R.string.text_num_solutions,
          mProperties.get(Generator.NUM_SOLUTIONS_KEY).getAsInt()));
    if (mProperties.has(Generator.SYMMETRY_KEY))
      sb.append("<li>").append(getString(R.string.text_symmetry,
          TextUtils.htmlEncode(mProperties.get(Generator.SYMMETRY_KEY).getAsString())));
    if (mProperties.has(Generator.BROKEN_SYMMETRY_KEY))
      sb.append("<li>").append(getString(R.string.text_broken_symmetry,
          TextUtils.htmlEncode(mProperties.get(Generator.BROKEN_SYMMETRY_KEY).getAsString())));
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
      return mDb.getOpenAttemptForPuzzle(mPuzzleId)._id;
    }

    @Override protected void onPostExecute(PuzzleInfoFragment fragment, Long attemptId) {
      Intent intent = new Intent(fragment.getActivity(), SudokuActivity.class);
      intent.putExtra(Extras.ATTEMPT_ID, attemptId);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      fragment.startActivity(intent);
    }
  }

  private class LinkHandler extends WebViewClient {
    @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
      if (url.startsWith(Uris.LIST_URI_PREFIX)) {
        String tail = url.substring(Uris.LIST_URI_PREFIX.length());
        int slash = tail.indexOf('/');
        long collectionId = Long.parseLong(slash < 0 ? tail : tail.substring(0, slash));
        mCallback.showCollection(collectionId);
      } else if (url.startsWith(Uris.REPLAY_URI_PREFIX)) {
        String tail = url.substring(Uris.REPLAY_URI_PREFIX.length());
        Intent intent = new Intent(getActivity(), ReplayActivity.class);
        intent.putExtra(Extras.ATTEMPT_ID, Long.parseLong(tail));
        startActivity(intent);
      } else {
        Log.e(TAG, "Unexpected link: " + url);
      }
      return true;
    }
  }
}
