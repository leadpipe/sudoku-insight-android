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

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.game.CommandException;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.UndoStack;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Implication;
import us.blanshard.sudoku.insight.Insight;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;

import org.json.JSONException;

import java.util.Collection;
import java.util.List;


/**
 * @author Luke Blanshard
 */
public class ReplayActivity extends ActivityBase implements View.OnClickListener, ReplayView.OnSelectListener {
  private static final String TAG = "ReplayActivity";
  private static final long CYCLE_MILLIS = 500;
  private ReplayView mReplayView;
  private ViewGroup mControls;
  private ViewGroup mPauseControls;
  private TextView mTimer;
  private TextView mInsightsText;
  private Sudoku mGame;
  private final Sudoku.Registry mRegistry = Sudoku.newRegistry();
  private List<Move> mHistory;
  private int mHistoryPosition;
  private UndoStack mUndoStack = new UndoStack();
  private boolean mRunning;
  private boolean mForward;
  private Analyze mAnalyze;
  private Insights mInsights;

  private final Runnable cycler = new Runnable() {
    @Override public void run() {
      if (mGame != null) stepReplay(false);
    }
  };

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!getIntent().hasExtra(Extras.GAME_ID)) {
      Log.e(TAG, "No game ID");
      finish();
      return;
    }

    new FetchGame(this).execute(getIntent().getLongExtra(Extras.GAME_ID, 0));

    setContentView(R.layout.replay);

    mReplayView = (ReplayView) findViewById(R.id.replay_view);
    mControls = (ViewGroup) findViewById(R.id.replay_controls);
    mPauseControls = (ViewGroup) findViewById(R.id.replay_pause_controls);
    mTimer = (TextView) findViewById(R.id.timer);
    mInsightsText = (TextView) findViewById(R.id.insights);

    mReplayView.setOnSelectListener(this);

    setUpButton(R.id.play);
    setUpButton(R.id.pause);
    setUpButton(R.id.back);
    setUpButton(R.id.next);
    setUpButton(R.id.previous);

    mRegistry.addListener(new Sudoku.Adapter() {
      @Override public void moveMade(Sudoku game, Move move) {
        if (game != mGame) return;
        mReplayView.invalidateLocation(move.getLocation());
      }
    });
  }

  private void setUpButton(int id) {
    Button b = (Button) findViewById(id);
    b.setOnClickListener(this);
  }

  void setGame(Database.Game dbGame) {
    mGame = new Sudoku(dbGame.puzzle, mRegistry).resume();
    mReplayView.setGame(mGame);
    mReplayView.setEditable(false);
    try {
      mHistory = GameJson.toHistory(dbGame.history);
    } catch (JSONException e) {
      Log.e(TAG, "Unable to restore history for game #" + dbGame._id, e);
      mHistory = Lists.newArrayList();
    }
    mHistoryPosition = 0;
    Button play = (Button) findViewById(R.id.play);
    play.performClick();
  }

  void setInsights(Insights insights) {
    mInsights = insights;
    mReplayView.setSelectable(insights.assignments.keySet());
    mAnalyze = null;
  }

  @Override public void onClick(View v) {
    switch (v.getId()) {
      case R.id.play:
      case R.id.back:
        mControls.setVisibility(View.GONE);
        mPauseControls.setVisibility(View.VISIBLE);
        mRunning = true;
        mForward = (v.getId() == R.id.play);
        mReplayView.postDelayed(cycler, CYCLE_MILLIS);
        break;

      case R.id.pause:
        mControls.setVisibility(View.VISIBLE);
        mPauseControls.setVisibility(View.GONE);
        mRunning = false;
        break;

      case R.id.next:
      case R.id.previous:
        mRunning = false;
        mForward = (v.getId() == R.id.next);
        stepReplay(true);
        break;
    }
  }

  @Override public void onSelect(Location loc) {
    List<Insight> insights = mInsights.assignments.get(loc);
    mInsightsText.setText(Html.fromHtml(Joiner.on("<br>").join(insights)));
  }

  void stepReplay(boolean evenIfNotRunning) {
    if (mRunning || evenIfNotRunning) {
      boolean worked = false;
      if (mForward) {
        if (mHistoryPosition < mHistory.size()) {
          Move move = mHistory.get(mHistoryPosition);
          try {
            mUndoStack.doCommand(move.toCommand(mGame));
            ++mHistoryPosition;
            worked = true;
          } catch (CommandException e) {
            Log.e(TAG, "Unable to apply move " + move, e);
          }
        }
      } else {
        if (mUndoStack.canUndo()) {
          try {
            mUndoStack.undo();
            --mHistoryPosition;
            worked = true;
          } catch (CommandException e) {
            Log.e(TAG, "Can't undo", e);
          }
        }
      }
      if (worked) {
        String time = "";
        Location loc = null;
        if (mHistoryPosition > 0) {
          Move move = mHistory.get(mHistoryPosition - 1);
          time = ToText.elapsedTime(move.timestamp);
          updateTrail(move.trailId);
          if (move instanceof Move.Set) loc = move.getLocation();
        } else updateTrail(-1);
        mTimer.setText(time);
        if (mAnalyze == null && loc != null) mReplayView.setSelected(loc);
        if (mAnalyze != null) mAnalyze.interrupt();
        mAnalyze = new Analyze(this);
        mAnalyze.execute(mReplayView.getInputState().getGrid());
      } else {
        Button pause = (Button) findViewById(R.id.pause);
        pause.performClick();
      }
    }
    if (mRunning) mReplayView.postDelayed(cycler, CYCLE_MILLIS);
  }

  private void updateTrail(int stateId) {
    if (mReplayView.getInputState().getId() != stateId) {
      boolean isTrail = stateId >= 0;
      ImmutableList<TrailItem> trails = ImmutableList.of();
      if (isTrail)
        trails = ImmutableList.of(new TrailItem(mGame.getTrail(stateId), Color.DKGRAY, Color.LTGRAY, true));
      mReplayView.setTrails(trails);
      mReplayView.setTrailActive(isTrail);
    }
  }

  private static class FetchGame extends WorkerFragment.ActivityTask<
      ReplayActivity, Long, Void, Database.Game> {
    private final Database mDb;

    FetchGame(ReplayActivity activity) {
      super(activity);
      mDb = activity.mDb;
    }

    @Override protected Database.Game doInBackground(Long... params) {
      Database.Game answer = mDb.getGame(params[0]);
      mDb.noteReplay(answer._id);
      return answer;
    }

    @Override protected void onPostExecute(ReplayActivity activity, Database.Game game) {
      activity.setGame(game);
    }
  }

  private static class Insights {
    final ListMultimap<Location, Insight> assignments = ArrayListMultimap.create();
    final Collection<Insight> errors = Lists.newArrayList();
    boolean interrupted;
  }

  private abstract static class Interruptible<I, P, O> extends WorkerFragment.ActivityTask<ReplayActivity, I, P, O> {
    private Thread mThread;
    private boolean mInterrupted;

    Interruptible(ReplayActivity activity) {
      super(activity);
    }

    public synchronized void interrupt() {
      mInterrupted = true;
      if (mThread != null) mThread.interrupt();
    }

    protected synchronized void setUpInterrupt() {
      mThread = Thread.currentThread();
      if (mInterrupted) mThread.interrupt();
    }

    protected synchronized void tearDownInterrupt() {
      mThread = null;
      Thread.interrupted();
    }
  }

  private static class Analyze extends Interruptible<Grid, Void, Insights> {

    Analyze(ReplayActivity activity) {
      super(activity);
    }

    @Override protected Insights doInBackground(final Grid... params) {
      final Insights answer = new Insights();
      setUpInterrupt();
      try {
        boolean done = Analyzer.analyze(params[0], false, new Analyzer.Callback() {
          @Override public void take(Insight insight) {
            if (insight instanceof Implication
                && (insight.isError() || !answer.assignments.containsKey(
                    Iterables.getFirst(insight.getAssignments(), null).location))) {
              insight = Analyzer.minimizeImplication(params[0], (Implication) insight);
            }
            if (insight.isError()) answer.errors.add(insight);
            else for (Assignment assignment : insight.getAssignments())
                answer.assignments.put(assignment.location, insight);
          }
        });
        if (!done)
          answer.interrupted = true;
      } finally {
        tearDownInterrupt();
      }
      return answer;
    }

    @Override protected void onPostExecute(ReplayActivity activity, Insights insights) {
      activity.setInsights(insights);
    }
  }
}
