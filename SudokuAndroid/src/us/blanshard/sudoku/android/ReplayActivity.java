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
package us.blanshard.sudoku.android;

import static java.util.Collections.singleton;
import static us.blanshard.sudoku.android.Json.GSON;
import static us.blanshard.sudoku.core.Numeral.numeral;
import static us.blanshard.sudoku.game.GameJson.JOINER;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.game.Command;
import us.blanshard.sudoku.game.CommandException;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.MoveCommand;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.UndoStack;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Analyzer.StopException;
import us.blanshard.sudoku.insight.DisprovedAssignment;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.UnfoundedAssignment;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * @author Luke Blanshard
 */
public class ReplayActivity extends ActivityBase
    implements View.OnClickListener, ReplayView.OnSelectListener, OnSeekBarChangeListener {
  private static final String TAG = "ReplayActivity";
  private static final long SET_CYCLE_MILLIS = 500;
  private static final long CLEAR_CYCLE_MILLIS = 200;
  private ReplayView mReplayView;
  private ProgressBar mProgress;
  private ProgressBar mProgress2;
  private SeekBar mReplayLocation;
  private TextView mMoveNumber;
  private TextView mTimer;
  private Long mPuzzleId;
  private Sudoku mGame;
  private final Sudoku.Registry mRegistry = Sudoku.newRegistry();
  private List<Move> mHistory;
  private int mHistoryPosition = 0;
  private UndoStack mUndoStack = new UndoStack();
  private String mRestoredUndoJson;
  private boolean mResumed;
  private boolean mRunning = true;
  private boolean mForward = true;
  private boolean mExploring;
  private Analyze mAnalyze;
  private boolean mAnalysisRanLong;
  private Insights mInsights;
  private final Set<InsightMin> mToBeDisplayed = Sets.newHashSet();
  private Minimize mMinimize;
  private Disprove mDisprove;
  private GridMarks mSolution;
  private InsightMin mPendingInsight;
  private Command mPendingCommand;

  private static final Integer[] sMinAssignmentColors, sUnminAssignmentColors;
  private static final Integer[] sUnminDisproofColors;
  private static final Integer[][] sMinDisproofColors;
  static {
    sMinAssignmentColors = new Integer[10];
    sUnminAssignmentColors = new Integer[10];
    for (int i = 0; i < 10; ++i) {
      float f = 1f / (1 << i);
      float h = 1 - f;
      float s = f * 0.5f + 0.5f;
      float v = h * 0.4f + 0.6f;
      sMinAssignmentColors[i] = Color.HSVToColor(new float[] {90f - 20 * h, s, 0.9f * v});
      sUnminAssignmentColors[i] = Color.HSVToColor(new float[] {60f, s, 0.95f});
    }
    sUnminDisproofColors = new Integer[11];
    sMinDisproofColors = new Integer[11][11];
    for (int i = 0; i < 11; ++i) {
      float h = 240 + 60 * (1 - ((float) i / 11));
      sUnminDisproofColors[i] = Color.HSVToColor(new float[] {h, 0.3f, 1});
      for (int j = 0; j < 11; ++j) {
        float f = 1f / (1 << j);
        float s = f * 0.6f + 0.4f;
        float v = 0.7f + 0.3f * i / 11;
        sMinDisproofColors[j][i] = Color.HSVToColor(new float[] {h, s, v});
      }
    }
  }

  private final Runnable replayCycler = new Runnable() {
    @Override public void run() {
      if (mGame != null && mResumed) stepReplay(false);
    }
  };

  private final Function<Location, Integer> selectableColors = new Function<Location, Integer>() {
    @Override public Integer apply(Location loc) {
      if (mInsights == null) return null;
      Integer[] colors;
      int index;
      InsightMin insightMin = mInsights.assignments.get(loc);
      if (insightMin == null) {
        insightMin = mInsights.disproofs.get(loc);
        if (insightMin == null) return null;
        if (insightMin.minimized) {
          index = Math.min(10, insightMin.insight.getCount() - 1);
          colors = sMinDisproofColors[index];
        } else {
          colors = sUnminDisproofColors;
        }
        index = Math.round(insightMin.fractionCovered * 10);
      } else if (insightMin.minimized) {
        colors = sMinAssignmentColors;
        index = insightMin.insight.getCount() - 1;
      } else {
        colors = sUnminAssignmentColors;
        index = insightMin.insight.getDepth();
      }
      index = Math.max(0, Math.min(colors.length - 1, index));
      return colors[index];
    }
  };

  private final Function<Location, Integer> insightSize = new Function<Location, Integer>() {
    @Override public Integer apply(Location loc) {
      if (mInsights == null) return null;
      InsightMin insightMin = mInsights.assignments.get(loc);
      if (insightMin == null)
        insightMin = mInsights.disproofs.get(loc);
      if (insightMin == null || !insightMin.minimized)
        return null;
      return insightMin.insight.getCount();
    }
  };

  private final Function<Location, Integer> percentages = new Function<Location, Integer>() {
    @Override public Integer apply(Location loc) {
      if (mInsights == null) return null;
      InsightMin insightMin = mInsights.disproofs.get(loc);
      if (insightMin == null)
        return null;
      return (int) (insightMin.fractionCovered * 100f + 0.5f);
    }
  };

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    if (!getIntent().hasExtra(Extras.ATTEMPT_ID)) {
      Log.e(TAG, "No attempt ID");
      finish();
      return;
    }

    new FetchAttempt(this).execute(getIntent().getLongExtra(Extras.ATTEMPT_ID, 0));

    setContentView(R.layout.replay);

    mReplayView = (ReplayView) findViewById(R.id.replay_view);
    mProgress = (ProgressBar) findViewById(R.id.progress);
    mProgress2 = (ProgressBar) findViewById(R.id.progress2);
    mReplayLocation = (SeekBar) findViewById(R.id.replay_location);
    mMoveNumber = (TextView) findViewById(R.id.move_number);
    mTimer = (TextView) findViewById(R.id.timer);

    mReplayView.setOnSelectListener(this);
    mReplayView.setSelectableColorsFunction(selectableColors);
    mReplayView.setInsightSizeFunction(insightSize);
    mReplayView.setPercentagesFunction(percentages);
    mReplayLocation.setOnSeekBarChangeListener(this);

    findViewById(R.id.play).setOnClickListener(this);
    findViewById(R.id.pause).setOnClickListener(this);
    findViewById(R.id.back).setOnClickListener(this);
    findViewById(R.id.undo).setOnClickListener(this);
    findViewById(R.id.redo).setOnClickListener(this);
    findViewById(R.id.jump_back).setOnClickListener(this);
    findViewById(R.id.jump_forward).setOnClickListener(this);

    mRegistry.addListener(new Sudoku.Adapter() {
      @Override public void moveMade(Sudoku game, Move move) {
        if (game != mGame) return;
        mReplayView.invalidateLocation(move.getLocation());
      }
    });
  }

  @Override protected void onPause() {
    super.onPause();
    mResumed = false;
    mReplayView.removeCallbacks(replayCycler);
  }

  @Override protected void onResume() {
    super.onResume();
    mResumed = true;
    if (mRunning) {
      mReplayView.postDelayed(replayCycler, 0);
    }
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.replay, menu);
    return true;
  }

  @Override protected String getHelpPage() {
    return "replay";
  }

  @Override public boolean onPrepareOptionsMenu(Menu menu) {
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      switch (item.getItemId()) {
        case R.id.menu_clear:
          item.setEnabled(mPendingInsight != null);
          break;

        case R.id.menu_apply:
          item.setEnabled(mPendingCommand != null);
          break;

        case R.id.menu_resume_replay:
          item.setEnabled(mExploring);
          break;
      }
    }
    return super.onPrepareOptionsMenu(menu);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;

      case R.id.menu_clear:
        clearPending();
        displayInsightAndError(null);
        return true;

      case R.id.menu_apply:
        applyPendingCommand(true);
        return true;

      case R.id.menu_resume_replay:
        mExploring = false;
        try {
          while (mUndoStack.getPosition() > mHistoryPosition)
            mUndoStack.undo();
        } catch (CommandException e) {
          Log.e(TAG, "Can't resume replay", e);
        }
        clearPending();
        setControlsEnablement();
        play();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override protected Long getCurrentPuzzleId() {
    return mPuzzleId;
  }

  private void clearPending() {
    mPendingInsight = null;
    mPendingCommand = null;
    invalidateOptionsMenu();
  }

  private void applyPendingCommand(boolean clear) {
    doCommand(mPendingCommand);
    mPendingCommand = null;
    mExploring = true;
    startAnalysis();
    invalidateOptionsMenu();
    setControlsEnablement();
    if (clear) {
      mPendingInsight = null;
      displayInsightAndError(null);
      mReplayView.setSelected(null);
    }
  }

  private void undoOrRedo(boolean redo) {
    try {
      if (redo) mUndoStack.redo();
      else mUndoStack.undo();
    } catch (CommandException e) {
      Log.e(TAG, "Undo/redo failed", e);
      return;
    }

    startAnalysis();
    clearPending();
    if (mUndoStack.getPosition() <= mHistoryPosition) {
      mExploring = false;
      setControlsEnablement();
    }

    Command command = mUndoStack.getLastCommand(redo);
    Location loc = null;
    if (command instanceof MoveCommand)
      loc = ((MoveCommand) command).getLocation();
    else if (command instanceof ElimCommand)
      loc = ((ElimCommand) command).elimination.location;
    mReplayView.setSelected(loc);
  }

  @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    if (mHistory == null || mExploring || mRunning || !fromUser)
      return;
    mForward = progress > mHistoryPosition;
    while (progress != mHistoryPosition)
      if (!move(mForward))
        break;
    clearPending();
    mInsights = null;
    reflectCurrentMove();
    setControlsEnablement();
  }

  @Override public void onStartTrackingTouch(SeekBar seekBar) {}
  @Override public void onStopTrackingTouch(SeekBar seekBar) {}

  @Override protected void onSaveInstanceState(Bundle outState) {
    outState.putString("undo", GSON.toJson(mUndoStack));
    outState.putInt("historyPosition", mHistoryPosition);
    outState.putBoolean("running", mRunning);
    outState.putBoolean("forward", mForward);
    outState.putBoolean("exploring", mExploring);
  }

  @Override protected void onRestoreInstanceState(Bundle inState) {
    mRestoredUndoJson = inState.getString("undo");
    mHistoryPosition = inState.getInt("historyPosition");
    mRunning = inState.getBoolean("running");
    mForward = inState.getBoolean("forward");
    mExploring = inState.getBoolean("exploring");
  }

  void setAttempt(Database.Attempt attempt) {
    mPuzzleId = attempt.puzzleId;
    mGame = new Sudoku(attempt.clues, mRegistry).resume();
    setTitle(getString(R.string.text_replay_title, attempt.puzzleId));
    mReplayView.setGame(mGame);
    mReplayView.setEditable(false);
    try {
      mHistory = GameJson.toHistory(GSON, attempt.history);
      if (mRestoredUndoJson != null) {
        GameJson.CommandFactory factory = new GameJson.CommandFactory(mGame) {
          @Override public Command toCommand(String type, Iterator<String> values) {
            if (type.equals("elim")) {
              Location loc = Location.of(Integer.parseInt(values.next()));
              Numeral num = numeral(Integer.parseInt(values.next()));
              return new ElimCommand(Assignment.of(loc, num));
            }
            return super.toCommand(type, values);
          }
        };
        GameJson.setFactory(factory);
        mUndoStack = GSON.fromJson(mRestoredUndoJson, UndoStack.class);
        GameJson.clearFactory();
        List<Command> commands = mUndoStack.getCommands();
        for (int i = 0; i < mUndoStack.getPosition(); ++i)
          commands.get(i).redo();
      }
    } catch (CommandException e) {
      Log.e(TAG, "Unable to restore undo state", e);
    }
    mRestoredUndoJson = null;
    mReplayLocation.setMax(mHistory.size());
    if (mRunning)
      findViewById(mForward ? R.id.play : R.id.back).performClick();
    else
      reflectCurrentMove();
    setControlsEnablement();
    invalidateOptionsMenu();
  }

  void setInsights(Insights insights) {
    mProgress.setVisibility(View.GONE);
    mInsights = insights;
    mAnalyze = null;
    if (mAnalysisRanLong) {
      mAnalysisRanLong = false;
      mProgress.setVisibility(View.GONE);
      if (mExploring)
        startAnalysis();
      else
        stepReplay(true);
    }
    if (!mRunning) {
      displayInsightAndError(null);
      minimizeEverything();
      mReplayView.selectableColorsUpdated();
      onSelect(mReplayView.getSelected(), false);
    }
  }

  @SuppressWarnings("unchecked")  // the varargs of Iterable<...>
  private void minimizeEverything() {
    if (mAnalyze == null) {
      if (mDisprove != null) mDisprove.cancel();
      if (mMinimize != null) mMinimize.cancel();
      mMinimize = new Minimize(this, true);
      mMinimize.execute(singleton(mInsights.error), mInsights.assignments.values(), mInsights.disproofsInOrder());
    }
  }

  @SuppressWarnings("unchecked")  // the varargs of Iterable<...>
  private void minimizeInsights(InsightMin... insightMins) {
    if (mAnalyze == null) {
      if (mDisprove != null) mDisprove.cancel();
      if (mMinimize != null) mMinimize.cancel();
      mMinimize = new Minimize(this, false);
      mMinimize.execute(Arrays.asList(insightMins));
    }
  }

  void disproofComplete(Disprove instance, boolean changed) {
    if (instance == mDisprove) {
      mDisprove = null;
      if (changed)
        minimizeEverything();
      else
        mProgress2.setVisibility(View.INVISIBLE);
    }
  }

  boolean addDisproof(InsightMin insightMin) {
    DisprovedAssignment disproof = (DisprovedAssignment) insightMin.insight;
    Location loc = disproof.getDisprovedAssignment().location;
    InsightMin existing = mInsights.disproofs.get(loc);
    if (existing == null
        || insightMin.fractionCovered > existing.fractionCovered
        || (insightMin.fractionCovered == existing.fractionCovered
            && insightMin.insight.getDepth() < existing.insight.getDepth())) {
      mInsights.disproofs.put(loc, insightMin);
      if (!mRunning)
        mReplayView.invalidateLocation(loc);
      return true;
    }
    return false;
  }

  void minimized(InsightMin insightMin) {
    if (!mRunning && !insightMin.insight.isError()) {
      Location loc;
      if (insightMin.insight.isAssignment()) loc = insightMin.insight.getImpliedAssignment().location;
      else loc = ((DisprovedAssignment) insightMin.insight).getDisprovedAssignment().location;
      mReplayView.invalidateLocation(loc);
    }
    if (mToBeDisplayed.contains(insightMin)) {
      mToBeDisplayed.remove(insightMin);
      mReplayView.addInsight(insightMin.insight);
      mReplayView.invalidate();
    }
  }

  void minimizationComplete(Minimize instance) {
    if (instance == mMinimize) {
      mMinimize = null;
      if (!instance.mEverything) {
        minimizeEverything();
      } else if (mInsights != null && mInsights.error == null) {
        mDisprove = new Disprove(this);
        mDisprove.execute();
      } else {
        mProgress2.setVisibility(View.INVISIBLE);
      }
    }
  }

  @Override public void onClick(View v) {
    if (mHistory == null) return;
    switch (v.getId()) {
      case R.id.play:
      case R.id.back:
        mForward = (v.getId() == R.id.play);
        mRunning = true;
        mReplayView.removeCallbacks(replayCycler);
        mReplayView.postDelayed(replayCycler, SET_CYCLE_MILLIS);
        startAnalysis();
        invalidateOptionsMenu();
        setControlsEnablement();
        if (!mForward) clearPending();
        displayInsightAndError(null);
        break;

      case R.id.pause:
        mRunning = false;
        mReplayView.removeCallbacks(replayCycler);
        startAnalysis();
        invalidateOptionsMenu();
        setControlsEnablement();
        displayInsightAndError(null);
        break;

      case R.id.undo:
      case R.id.redo: {
        mRunning = false;
        mReplayView.removeCallbacks(replayCycler);
        boolean forward = v.getId() == R.id.redo;
        clearPending();
        if (mExploring) {
          undoOrRedo(forward);
        } else {
          mForward = forward;
          stepReplay(true);
        }
        break;
      }
      case R.id.jump_back:
      case R.id.jump_forward: {
        mRunning = false;
        mReplayView.removeCallbacks(replayCycler);
        mForward = v.getId() == R.id.jump_forward;
        clearPending();
        jump();
        break;
      }
    }
  }

  private void setControlsEnablement() {
    if (mExploring) {
      findViewById(R.id.play).setEnabled(false);
      findViewById(R.id.back).setEnabled(false);
      findViewById(R.id.pause).setEnabled(false);
      findViewById(R.id.jump_back).setEnabled(false);
      findViewById(R.id.jump_forward).setEnabled(false);
      findViewById(R.id.undo).setEnabled(mUndoStack.canUndo());
      findViewById(R.id.redo).setEnabled(mUndoStack.canRedo());
      mReplayLocation.setEnabled(false);
      mTimer.setTextColor(Color.LTGRAY);
      mMoveNumber.setTextColor(Color.LTGRAY);
    } else {
      boolean notAtBeginning = mHistoryPosition > 0;
      boolean notAtEnd = mHistory != null && mHistoryPosition < mHistory.size();
      findViewById(R.id.play).setEnabled((!mRunning || !mForward) && notAtEnd);
      findViewById(R.id.back).setEnabled((!mRunning || mForward) && notAtBeginning);
      findViewById(R.id.pause).setEnabled(mRunning);
      findViewById(R.id.undo).setEnabled(notAtBeginning);
      findViewById(R.id.redo).setEnabled(notAtEnd);
      findViewById(R.id.jump_back).setEnabled(notAtBeginning);
      findViewById(R.id.jump_forward).setEnabled(notAtEnd);
      mReplayLocation.setEnabled(!mRunning);
      mTimer.setTextColor(Color.BLACK);
      mMoveNumber.setTextColor(Color.BLACK);
    }
  }

  @Override public void onSelect(Location loc, boolean byUser) {
    if (byUser)
      clearPending();
    if (mInsights != null) {
      InsightMin insightMin = mInsights.assignments.get(loc);
      if (insightMin == null) {
        insightMin = mInsights.disproofs.get(loc);
        if (insightMin != null) {
          DisprovedAssignment disproof = (DisprovedAssignment) insightMin.insight;
          mPendingInsight = insightMin;
          mPendingCommand = new ElimCommand(disproof.getDisprovedAssignment());
          invalidateOptionsMenu();
        }
      } else {
        mPendingInsight = insightMin;
        mPendingCommand = makeMoveCommand(insightMin.insight.getImpliedAssignment());
        invalidateOptionsMenu();
        if (mExploring && byUser) applyPendingCommand(false);
      }
      if (byUser || mPendingInsight != null)
        displayInsightAndError(mPendingInsight);
    }
  }

  private void displayInsightAndError(InsightMin insightMin) {
    mReplayView.clearInsights();
    mToBeDisplayed.clear();
    if (insightMin != null) displayInsight(insightMin);
    InsightMin error = null;
    if (mInsights != null && mInsights.error != null) {
      if (insightMin == null) {
        mReplayView.addInsight(mInsights.error.insight.getNub());
      } else {
        error = mInsights.error;
        displayInsight(error);
      }
    }
    if (insightMin != null || error != null)
      minimizeInsights(insightMin, error);
  }

  private void displayInsight(InsightMin insightMin) {
    mReplayView.addInsight(insightMin.getMinimizedInsightOrNub());
    if (!insightMin.minimized) mToBeDisplayed.add(insightMin);
  }

  private MoveCommand makeMoveCommand(Assignment assignment) {
    return new MoveCommand(
        mReplayView.getInputState(), assignment.location, assignment.numeral);
  }

  private void doCommand(Command command) {
    try {
      mUndoStack.doCommand(command);
    } catch (CommandException e) {
      Log.e(TAG, e.getMessage());
    }
  }

  void stepReplay(boolean evenIfNotRunning) {
    if (mRunning || evenIfNotRunning) {
      if (mAnalyze != null) {
        mAnalysisRanLong = true;
        mProgress.setVisibility(View.VISIBLE);
        maybeCancelAnalysis();
        return;
      }

      boolean worked = move(mForward);

      if (worked) {
        reflectCurrentMove();
      }

      if (mRunning && !worked) {
        pause();
        return;
      }
    }
    if (mRunning) {
      long cycleMillis = nextAssignment() == null ? CLEAR_CYCLE_MILLIS : SET_CYCLE_MILLIS;
      mReplayView.removeCallbacks(replayCycler);
      mReplayView.postDelayed(replayCycler, cycleMillis);
    }
    setControlsEnablement();
  }

  private boolean move(boolean forward) {
    if (forward) {
      if (mHistoryPosition < mHistory.size()) {
        Move move = mHistory.get(mHistoryPosition);
        try {
          mUndoStack.doCommand(move.toCommand(mGame));
          ++mHistoryPosition;
          return true;
        } catch (CommandException e) {
          Log.e(TAG, "Unable to apply move " + move, e);
        }
      }
    } else {
      if (mUndoStack.canUndo()) {
        try {
          mUndoStack.undo();
          --mHistoryPosition;
          return true;
        } catch (CommandException e) {
          Log.e(TAG, "Can't undo", e);
        }
      }
    }
    return false;
  }

  private void jump() {
    int trailId = nextTrailId();
    while (move(mForward) && trailId == nextTrailId()) {
      // Just move
    }
    mInsights = null;
    reflectCurrentMove();
    setControlsEnablement();
  }

  private int nextTrailId() {
    if (mForward) {
      if (mHistory != null && mHistoryPosition < mHistory.size())
        return mHistory.get(mHistoryPosition).trailId;
    } else if (mHistoryPosition > 0)
      return mHistory.get(mHistoryPosition - 1).trailId;
    return -1;
  }

  private void reflectCurrentMove() {
    long timestamp = 0;
    Location loc = null;
    if (mHistoryPosition > 0) {
      Move move = mHistory.get(mHistoryPosition - 1);
      timestamp = move.timestamp;
      updateTrail(move.trailId);
      loc = move.getLocation();
      if (mForward && move.getAssignment() != null && mInsights != null
          && !mInsights.assignments.containsKey(loc)) {
        mInsights.assignments.put(
            loc, new InsightMin(new UnfoundedAssignment(move.getAssignment())));
      }
    } else updateTrail(-1);
    startAnalysis();
    mMoveNumber.setText(getString(R.string.text_move_number, mHistoryPosition, mHistory.size()));
    mReplayLocation.setProgress(mHistoryPosition);
    mTimer.setText(ToText.elapsedTime(timestamp));
    mReplayView.setSelected(loc);
  }

  private void pause() {
    findViewById(R.id.pause).performClick();
  }

  private void play() {
    findViewById(R.id.play).performClick();
  }

  @Nullable Assignment nextAssignment() {
    if (mHistoryPosition < mHistory.size())
      return mHistory.get(mHistoryPosition).getAssignment();
    return null;
  }

  private void startAnalysis() {
    if (mAnalyze == null) {
      if (mMinimize != null) mMinimize.cancel();
      if (mDisprove != null) mDisprove.cancel();
      if (!mRunning || mForward) {
        mAnalyze = new Analyze(this);
        mProgress2.setVisibility(View.INVISIBLE);
        Location toClear = null;
        if (!mExploring) {
          Assignment asmt = nextAssignment();
          if (asmt != null && nextTrailId() == mReplayView.getInputState().getId())
            toClear = asmt.location;
        }
        mAnalyze.execute(mReplayView.getGridMarks(toClear));
        if (!mRunning)
          mProgress.setVisibility(View.VISIBLE);
      }
    } else {
      mAnalysisRanLong = true;
      mProgress.setVisibility(View.VISIBLE);
      maybeCancelAnalysis();
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
        trails = ImmutableList.of(new TrailItem(mGame.getTrail(stateId), Color.DKGRAY, Color.LTGRAY, true, false));
      mReplayView.setTrails(trails);
      mReplayView.setTrailActive(isTrail);
    }
  }

  private static class FetchAttempt extends WorkerFragment.ActivityTask<
      ReplayActivity, Long, Void, Database.Attempt> {
    private final Database mDb;
    private GridMarks mSolution;

    FetchAttempt(ReplayActivity activity) {
      super(activity);
      mDb = activity.mDb;
    }

    @Override protected Database.Attempt doInBackground(Long... params) {
      Database.Attempt answer = mDb.getAttempt(params[0]);
      Solver.Result result = Solver.solve(answer.clues, Prefs.MAX_SOLUTIONS);
      mSolution = new GridMarks(result.intersection);
      mDb.noteReplay(answer._id);
      return answer;
    }

    @Override protected void onPostExecute(ReplayActivity activity, Database.Attempt attempt) {
      activity.mSolution = mSolution;
      activity.setAttempt(attempt);
    }
  }

  private static class InsightMin implements Comparable<InsightMin> {
    volatile Insight insight;
    volatile boolean minimized;
    final float fractionCovered;

    InsightMin(Insight insight) {
      this(insight, insight.getDepth() == 0, 0);
    }

    InsightMin(DisprovedAssignment insight, float fractionCovered) {
      this(insight, insight.getDepth() == 1, fractionCovered);
    }

    InsightMin(Insight insight, boolean minimized, float fractionCovered) {
      this.insight = insight;
      this.minimized = minimized;
      this.fractionCovered = fractionCovered;
    }

    boolean minimize(GridMarks gridMarks) {
      if (!minimized) {
        insight = Analyzer.minimize(gridMarks, insight);
        minimized = !Thread.currentThread().isInterrupted();
      }
      return minimized;
    }

    Insight getMinimizedInsightOrNub() {
      return minimized ? insight : insight.getNub();
    }

    @Override public String toString() {
      if (minimized) return insight.toString();
      return insight.toShortString();
    }

    @Override public int compareTo(InsightMin that) {
      // Compare by larger fraction covered.
      return Floats.compare(that.fractionCovered, this.fractionCovered);
    }
  }

  private static class Insights {
    final GridMarks gridMarks;
    final Map<Location, InsightMin> assignments = Maps.newLinkedHashMap();
    final Map<Location, InsightMin> disproofs = Maps.newHashMap();
    @Nullable InsightMin error;
    @Nullable LocSet available;
    @Nullable Queue<PossibleAssignment> possibles;
    int disproofsSetSize;

    Insights(GridMarks gridMarks) {
      this.gridMarks = gridMarks;
    }

    Collection<InsightMin> disproofsInOrder() {
      List<InsightMin> list = Lists.newArrayList();
      for (InsightMin insightMin : disproofs.values())
        if (!insightMin.minimized)
          list.add(insightMin);
      Collections.sort(list);
      return list;
    }
  }

  private static class PossibleAssignment implements Comparable<PossibleAssignment> {
    final Location loc;
    final Numeral num;
    final int setSize;

    PossibleAssignment(Location loc, Numeral num, int setSize) {
      this.loc = loc;
      this.num = num;
      this.setSize = setSize;
    }

    Assignment toAssignment() {
      return Assignment.of(loc, num);
    }

    @Override public int compareTo(PossibleAssignment that) {
      return Ints.compare(this.setSize, that.setSize);
    }
  }

  private class ElimCommand implements Command {
    private final Assignment elimination;

    ElimCommand(Assignment elimination) {
      this.elimination = elimination;
    }

    @Override public void redo() throws CommandException {
      mReplayView.addElimination(elimination);
    }

    @Override public void undo() throws CommandException {
      mReplayView.removeElimination(elimination);
    }

    @Override public String toJsonValue() {
      return JOINER.join("elim", elimination.location.index, elimination.numeral.number);
    }
  }

  /** Analyzer callback that grabs the first error it sees and stops the process. */
  private static class ErrorGrabber implements Analyzer.Callback {
    @Nullable Insight error;

    @Override public void take(Insight insight) throws StopException {
      if (insight.isError()) {
        error = insight;
        throw new StopException();
      }
    }
  }

  /**
   * Analyzer callback that keeps track of all assignment locations found within
   * a set and stops early if the entire set is covered.
   */
  private static class CoverageMeasurer implements Analyzer.Callback {
    private final LocSet available;
    LocSet found = new LocSet();

    public CoverageMeasurer(LocSet available) {
      this.available = available;
    }

    public float getFractionCovered() {
      return (float) found.size() / available.size();
    }

    @Override public void take(Insight insight) throws StopException {
      if (insight.isAssignment()) {
        Location loc = insight.getImpliedAssignment().location;
        if (available.contains(loc)) {
          if (found.add(loc) && available.minus(found).isEmpty())
            throw new StopException();
        }
      }
    }
  }

  private static class Analyze extends WorkerFragment.ActivityTask<ReplayActivity, GridMarks, Void, Insights> {

    private final Assignment mTarget;
    private final Grid mSolution;
    boolean mCancelable;

    Analyze(ReplayActivity activity) {
      super(activity);
      mTarget = activity.mRunning && activity.mForward ? activity.nextAssignment() : null;
      mSolution = activity.mSolution.grid;
    }

    @Override protected Insights doInBackground(final GridMarks... params) {
      final Insights answer = new Insights(params[0]);
      if (mTarget == null) {
        publishProgress();  // allow cancellation right away
        doFullAnalysis(answer);
      } else {
        doTargetedAnalysis(answer);
      }
      return answer;
    }

    private void doFullAnalysis(final Insights answer) {
      Analyzer.analyze(answer.gridMarks, new Analyzer.Callback() {
        @Override public void take(Insight insight) {
          if (insight.isError()) {
            answer.error = new InsightMin(insight);
          } else if (insight.isAssignment()) {
            Assignment assignment = insight.getImpliedAssignment();
            if (!answer.assignments.containsKey(assignment.location)) {
              answer.assignments.put(assignment.location, new InsightMin(insight));
            }
          }
        }
      });
    }

    private void doTargetedAnalysis(final Insights answer) {
      Analyzer.analyze(answer.gridMarks, new Analyzer.Callback() {
        @Override public void take(Insight insight) {
          if (insight.isAssignment() && mTarget.equals(insight.getImpliedAssignment())) {
            InsightMin insightMin = new InsightMin(insight);
            insightMin.minimize(answer.gridMarks);
            answer.assignments.put(mTarget.location, insightMin);
            throw new StopException();
          }
        }
      });
      if (mSolution.get(mTarget.location) != mTarget.numeral) {
        ErrorGrabber grabber = new ErrorGrabber();
        GridMarks postTarget = answer.gridMarks.toBuilder().assign(mTarget).build();
        Analyzer.analyze(postTarget, grabber);
        if (grabber.error != null) {
          answer.error = new InsightMin(grabber.error);
          //answer.error.minimize(postTarget);
        }
      }
    }

    @Override protected void onProgressUpdate(ReplayActivity activity, Void... values) {
      mCancelable = true;
      activity.maybeCancelAnalysis();
    }

    @Override protected void onPostExecute(ReplayActivity activity, Insights insights) {
      activity.setInsights(insights);
    }
  }

  private static class Minimize extends WorkerFragment.ActivityTask<ReplayActivity, Iterable<InsightMin>, InsightMin, Void> {
    private final GridMarks mGridMarks;
    final boolean mEverything;

    Minimize(ReplayActivity activity, boolean everything) {
      super(activity);
      mGridMarks = activity.mInsights.gridMarks;
      mEverything = everything;
      activity.mProgress2.setVisibility(View.VISIBLE);
    }

    @Override protected Void doInBackground(Iterable<InsightMin>... params) {
      for (Iterable<InsightMin> iterable : params)
        for (InsightMin min : iterable) {
          if (min == null) continue;
          if (min.minimize(mGridMarks))
            publishProgress(min);
          else
            break;
        }
      return null;
    }

    @Override protected void onProgressUpdate(ReplayActivity activity, InsightMin... mins) {
      activity.minimized(mins[0]);
    }

    @Override protected void onPostExecute(ReplayActivity activity, Void ignored) {
      activity.minimizationComplete(this);
    }
  }

  private static class Disprove
      extends WorkerFragment.ActivityTask<ReplayActivity, Void, InsightMin, Void> {
    private final Insights mInsights;
    private final GridMarks mSolution;
    private int mSetSize;
    private volatile boolean mChanged;

    Disprove(ReplayActivity activity) {
      super(activity);
      mInsights = activity.mInsights;
      mSolution = activity.mSolution;
      mSetSize = mInsights.disproofsSetSize;
    }

    @Override protected Void doInBackground(Void... params) {
      GridMarks current = mInsights.gridMarks;
      if (mInsights.possibles == null) {
        mInsights.available = LocSet.all()
            .minus(current.grid.keySet())
            .minus(mInsights.assignments.keySet());
        mInsights.possibles = findPossibles(mSolution, current, mInsights.available);
      }

      for (PossibleAssignment p; (p = mInsights.possibles.poll()) != null; ) {
        if (mSetSize > 0 && p.setSize > mSetSize) {
          mInsights.possibles.clear();
          break;
        }
        if (wasCanceled()) {
          mChanged = true;
          break;
        }
        InsightMin insightMin = mInsights.disproofs.get(p.loc);
        if (insightMin == null || insightMin.fractionCovered < 1f)
          checkForDisproof(current, p);
      }

      return null;
    }

    @Override protected void onProgressUpdate(ReplayActivity activity, InsightMin... insightMins) {
      mInsights.disproofsSetSize = mSetSize;
      if (mInsights == activity.mInsights
          && activity.addDisproof(insightMins[0]))
        mChanged = true;
    }

    @Override protected void onPostExecute(ReplayActivity activity, Void result) {
      if (mSolution != null) activity.mSolution = mSolution;
      activity.disproofComplete(this, mChanged);
    }

    private Queue<PossibleAssignment> findPossibles(GridMarks solution, GridMarks current,
        LocSet available) {
      Queue<PossibleAssignment> possibles = Queues.newPriorityQueue();
      for (UnitNumeral unitNum : UnitNumeral.all()) {
        UnitSubset set = current.marks.get(unitNum);
        UnitSubset solSet = solution.marks.get(unitNum);
        if (set.size() > 1 && solSet.size() == 1)
          for (Location loc : available.and(set.minus(solSet))) {
            possibles.add(new PossibleAssignment(loc, unitNum.numeral, set.size()));
          }
      }
      for (Location loc : available) {
        NumSet set = current.marks.get(loc);
        NumSet solSet = solution.marks.get(loc);
        if (set.size() > 1 && solSet.size() == 1)
          for (Numeral num : set.minus(solSet))
            possibles.add(new PossibleAssignment(loc, num, set.size()));
      }
      return possibles;
    }

    private void checkForDisproof(GridMarks current, PossibleAssignment p) {
      ErrorGrabber grabber = new ErrorGrabber();
      Analyzer.analyze(current.toBuilder().assign(p.loc, p.num).build(), grabber);

      if (grabber.error != null) {
        mSetSize = p.setSize;
        DisprovedAssignment disproof = new DisprovedAssignment(p.toAssignment(), grabber.error);
        CoverageMeasurer cm = new CoverageMeasurer(mInsights.available);
        Analyzer.analyze(current.toBuilder().eliminate(p.loc, p.num).build(), cm);
        publishProgress(new InsightMin(disproof, cm.getFractionCovered()));
      }
    }
  }
}
