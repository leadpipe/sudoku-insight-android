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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.json.JSONException;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * @author Luke Blanshard
 */
public class ReplayActivity extends ActivityBase implements View.OnClickListener, ReplayView.OnSelectListener {
  private static final String TAG = "ReplayActivity";
  private static final long SET_CYCLE_MILLIS = 500;
  private static final long CLEAR_CYCLE_MILLIS = 200;
  private ReplayView mReplayView;
  private ProgressBar mProgress;
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
  private boolean mAnalysisRanLong;
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
    mProgress = (ProgressBar) findViewById(R.id.progress);
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
    mAnalyze = null;
    mProgress.setVisibility(View.GONE);
    if (mAnalysisRanLong) {
      mAnalysisRanLong = false;
      stepReplay(true);
    }
    mReplayView.setSelectable(insights.assignments.keySet());
  }

  @Override public void onClick(View v) {
    switch (v.getId()) {
      case R.id.play:
      case R.id.back:
        mControls.setVisibility(View.GONE);
        mPauseControls.setVisibility(View.VISIBLE);
        mRunning = true;
        mForward = (v.getId() == R.id.play);
        mReplayView.postDelayed(cycler, SET_CYCLE_MILLIS);
        startAnalysis();
        break;

      case R.id.pause:
        mControls.setVisibility(View.VISIBLE);
        mPauseControls.setVisibility(View.GONE);
        mRunning = false;
        startAnalysis();
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
    StringBuilder sb = new StringBuilder();
    if (mInsights != null) {
      if (mInsights.assignments.containsKey(loc))
        sb.append(mInsights.assignments.get(loc).insight).append('\n');
      if (!mInsights.errors.isEmpty()) sb.append("Error: ").append(mInsights.errors.get(0).insight);
    }
    mInsightsText.setText(sb.toString());
  }

  void stepReplay(boolean evenIfNotRunning) {
    if (mRunning || evenIfNotRunning) {
      if (mAnalyze != null) {
        mAnalysisRanLong = true;
        maybeCancelAnalysis();
        return;
      }

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

      boolean foundInsight = !mForward;  // Don't worry about it if going backwards.
      if (worked) {
        String time = "";
        Location loc = null;
        if (mHistoryPosition > 0) {
          Move move = mHistory.get(mHistoryPosition - 1);
          time = ToText.elapsedTime(move.timestamp);
          updateTrail(move.trailId);
          loc = move.getLocation();
          if (mInsights.errors.isEmpty() && (move instanceof Move.Clear || mInsights.assignments.containsKey(loc)))
            foundInsight = true;
        } else updateTrail(-1);
        mTimer.setText(time);
        mReplayView.setSelected(loc);
        startAnalysis();
      }

      if (mRunning && (!worked || !foundInsight)) {
        Button pause = (Button) findViewById(R.id.pause);
        pause.performClick();
      }
    }
    if (mRunning) {
      long cycleMillis = nextAssignment() == null ? CLEAR_CYCLE_MILLIS : SET_CYCLE_MILLIS;
      mReplayView.postDelayed(cycler, cycleMillis);
    }
  }

  @Nullable Assignment nextAssignment() {
    if (mHistoryPosition < mHistory.size())
      return mHistory.get(mHistoryPosition).getAssignment();
    return null;
  }

  private void startAnalysis() {
    if (mAnalyze == null) {
      mAnalyze = new Analyze(this);
      mAnalyze.execute(mReplayView.getInputState().getGrid());
      if (!mRunning)
        mProgress.setVisibility(View.VISIBLE);
    }
  }

  void maybeCancelAnalysis() {
    if (mAnalysisRanLong && mAnalyze.mCancelable)
      mAnalyze.cancel();
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

  private static class InsightMin {
    volatile Insight insight;
    boolean minimized;

    InsightMin(Insight insight) {
      this.insight = insight;
      this.minimized = !(insight instanceof Implication);
    }

    boolean minimize(Grid grid) {
      if (!minimized) {
        Implication imp = Analyzer.minimizeImplication(grid, (Implication) insight);
        minimized = (imp != insight);
        insight = imp;
      }
      return minimized;
    }
  }

  private static class Insights {
    final Grid grid;
    final Map<Location, InsightMin> assignments = Maps.newHashMap();
    final List<InsightMin> errors = Lists.newArrayList();

    Insights(Grid grid) {
      this.grid = grid;
    }
  }

  private static class Analyze extends WorkerFragment.ActivityTask<ReplayActivity, Grid, Void, Insights> {

    private final Assignment mTarget;
    boolean mCancelable;

    Analyze(ReplayActivity activity) {
      super(activity);
      mTarget = activity.mRunning && activity.mForward ? activity.nextAssignment() : null;
    }

    @Override protected Insights doInBackground(final Grid... params) {
      if (mTarget == null) publishProgress();  // allow cancellation right away
      final Insights answer = new Insights(params[0]);
      Analyzer.analyze(answer.grid, new Analyzer.Callback() {
        private boolean mAiming = mTarget != null;
        @Override public void take(Insight insight) {
          if (insight.isError()) {
            answer.errors.add(new InsightMin(insight));
            hit();
          } else if (insight.isAssignment()) {
            Assignment assignment = insight.getAssignment();
            if (!answer.assignments.containsKey(assignment.location)
                && (mTarget == null || assignment.equals(mTarget))) {
              answer.assignments.put(assignment.location, new InsightMin(insight));
              hit();
            }
          }
        }
        private void hit() {
          if (mAiming) {
            mAiming = false;
            publishProgress();
          }
        }
      });
      return answer;
    }

    @Override protected void onProgressUpdate(ReplayActivity activity, Void... values) {
      mCancelable = true;
      activity.maybeCancelAnalysis();
    }

    @Override protected void onPostExecute(ReplayActivity activity, Insights insights) {
      activity.setInsights(insights);
    }
  }

  private static class Minimize extends WorkerFragment.ActivityTask<ReplayActivity, InsightMin, Void, Void> {
    private final Grid mGrid;

    Minimize(ReplayActivity activity, Grid grid) {
      super(activity);
      this.mGrid = grid;
    }

    @Override protected Void doInBackground(InsightMin... params) {
      for (InsightMin min : params)
        if (!min.minimize(mGrid))
          break;
      return null;
    }
  }
}
