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

import us.blanshard.sudoku.android.SudokuView.OnMoveListener;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.game.CommandException;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.Sudoku.State;
import us.blanshard.sudoku.game.UndoStack;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.json.JSONException;

import java.util.List;


/**
 * @author Luke Blanshard
 */
public class ReplayActivity extends ActivityBase implements OnMoveListener, View.OnClickListener {
  private static final String TAG = "ReplayActivity";
  private static final long CYCLE_MILLIS = 500;
  private ReplayView mReplayView;
  private ViewGroup mControls;
  private ViewGroup mPauseControls;
  private TextView mTimer;
  private Database.Game mDbGame;
  private Sudoku mGame;
  private final Sudoku.Registry mRegistry = Sudoku.newRegistry();
  private List<Move> mHistory;
  private int mHistoryPosition;
  private UndoStack mUndoStack = new UndoStack();
  private boolean mRunning;
  private boolean mForward;

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

    mReplayView.setOnMoveListener(this);
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
    mDbGame = dbGame;
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
        if (mHistoryPosition > 0) {
          Move move = mHistory.get(mHistoryPosition - 1);
          time = ToText.elapsedTime(move.timestamp);
          updateTrail(move.trailId);
        } else updateTrail(-1);
        mTimer.setText(time);
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

  @Override public void onMove(State state, Location loc, Numeral num) {
    state.set(loc, num);
    mReplayView.invalidateLocation(loc);
  }

  private static class FetchGame extends WorkerFragment.ActivityTask<
      ReplayActivity, Long, Void, Database.Game> {
    private final Database mDb;

    FetchGame(ReplayActivity activity) {
      super(activity);
      mDb = activity.mDb;
    }

    @Override protected Database.Game doInBackground(Long... params) {
      return mDb.getGame(params[0]);
    }

    @Override protected void onPostExecute(ReplayActivity activity, Database.Game game) {
      activity.setGame(game);
    }
  }
}
