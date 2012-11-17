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
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.core.Unit;
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

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import org.json.JSONException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
  private ViewGroup mControls;
  private ViewGroup mPauseControls;
  private ViewGroup mExploreControls;
  private SeekBar mReplayLocation;
  private ViewGroup mReplayInfo;
  private TextView mMoveNumber;
  private TextView mTimer;
  private Sudoku mGame;
  private final Sudoku.Registry mRegistry = Sudoku.newRegistry();
  private List<Move> mHistory;
  private int mHistoryPosition;
  private UndoStack mUndoStack = new UndoStack();
  private boolean mRunning;
  private boolean mForward;
  private boolean mExploring;
  private Analyze mAnalyze;
  private boolean mAnalysisRanLong;
  private Insights mInsights;
  private final Set<InsightMin> mToBeDisplayed = Sets.newHashSet();
  private Minimize mMinimize;
  private Disprove mDisprove;
  private GridMarks mSolution;
  private DisprovedAssignment mPendingDisproof;

  private static final Integer sSelectedColor = Color.BLUE;
  private static final Integer[] sMinAssignmentColors, sUnminAssignmentColors;
  private static final Integer[] sMinDisproofColors;
  static {
    sMinAssignmentColors = new Integer[10];
    sUnminAssignmentColors = new Integer[10];
    sMinDisproofColors = new Integer[10];
    for (int i = 0; i < 10; ++i) {
      float f = 1f / (1 << i);
      float h = 1 - f;
      float s = f * 0.5f + 0.5f;
      float v = h * 0.4f + 0.6f;
      sMinAssignmentColors[i] = Color.HSVToColor(new float[] {90f - 20 * h, s, 0.9f * v});
      sUnminAssignmentColors[i] = Color.HSVToColor(new float[] {60f, s, 0.95f});
      sMinDisproofColors[i] = Color.HSVToColor(new float[] {20 + 10 * h, s, v});
    }
  }

  private final Runnable replayCycler = new Runnable() {
    @Override public void run() {
      if (mGame != null) stepReplay(false);
    }
  };

  private final Function<Location, Integer> selectableColors = new Function<Location, Integer>() {
    @Override public Integer apply(Location loc) {
      if (loc == mReplayView.getSelected()) return sSelectedColor;
      if (mInsights == null) return null;
      Integer[] colors;
      int index;
      InsightMin insightMin = mInsights.assignments.get(loc);
      if (insightMin == null) {
        insightMin = mInsights.disproofs.get(loc);
        if (insightMin == null) return null;
        colors = sMinDisproofColors;
        index = insightMin.insight.getCount() - 1;
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

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getActionBar().setDisplayHomeAsUpEnabled(true);

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
    mPauseControls = (ViewGroup) findViewById(R.id.pause_controls);
    mExploreControls = (ViewGroup) findViewById(R.id.explore_controls);
    mReplayLocation = (SeekBar) findViewById(R.id.replay_location);
    mReplayInfo = (ViewGroup) findViewById(R.id.replay_info);
    mMoveNumber = (TextView) findViewById(R.id.move_number);
    mTimer = (TextView) findViewById(R.id.timer);

    mReplayView.setOnSelectListener(this);
    mReplayView.setSelectableColorsFunction(selectableColors);
    mReplayLocation.setOnSeekBarChangeListener(this);

    setUpButton(R.id.play);
    setUpButton(R.id.pause);
    setUpButton(R.id.back);
    setUpButton(R.id.next);
    setUpButton(R.id.previous);
    setUpButton(R.id.undo);
    setUpButton(R.id.clear);
    setUpButton(R.id.apply);
    setUpButton(R.id.redo);

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

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.replay, menu);
    return true;
  }

  @Override public boolean onPrepareOptionsMenu(Menu menu) {
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      switch (item.getItemId()) {
        case R.id.menu_explore:
          item.setEnabled(!mRunning && !mExploring);
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

      case R.id.menu_explore:
        mExploring = true;
        mControls.setVisibility(View.GONE);
        mPauseControls.setVisibility(View.GONE);
        mReplayInfo.setVisibility(View.GONE);
        mReplayLocation.setVisibility(View.GONE);
        mExploreControls.setVisibility(View.VISIBLE);
        setUndoEnablement();
        invalidateOptionsMenu();
        return true;

      case R.id.menu_resume_replay:
        mExploring = false;
        try {
          while (mUndoStack.getPosition() > mHistoryPosition)
            mUndoStack.undo();
        } catch (CommandException e) {
          Log.e(TAG, "Can't resume replay", e);
        }
        mReplayInfo.setVisibility(View.VISIBLE);
        mReplayLocation.setVisibility(View.VISIBLE);
        mExploreControls.setVisibility(View.GONE);
        pause();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    if (mHistory == null || mExploring || mRunning || !fromUser)
      return;
    while (progress != mHistoryPosition)
      if (!move(progress > mHistoryPosition))
        break;
    reflectCurrentMove();
    setControlsEnablement();
  }

  @Override public void onStartTrackingTouch(SeekBar seekBar) {}
  @Override public void onStopTrackingTouch(SeekBar seekBar) {}

  void setGame(Database.Game dbGame) {
    mGame = new Sudoku(dbGame.puzzle, mRegistry).resume();
    setTitle(getString(R.string.text_replay_title, dbGame.puzzleId));
    mReplayView.setGame(mGame);
    mReplayView.setEditable(false);
    try {
      mHistory = GameJson.toHistory(dbGame.history);
    } catch (JSONException e) {
      Log.e(TAG, "Unable to restore history for game #" + dbGame._id, e);
      mHistory = Lists.newArrayList();
    }
    mHistoryPosition = 0;
    mReplayLocation.setMax(mHistory.size());
    setControlsEnablement();
    findViewById(R.id.play).performClick();
  }

  void setInsights(Insights insights) {
    mProgress.setVisibility(View.GONE);
    mInsights = insights;
    mAnalyze = null;
    minimizeEverything();
    if (mAnalysisRanLong) {
      mAnalysisRanLong = false;
      if (mExploring)
        startAnalysis();
      else
        stepReplay(true);
    }
    if (!mRunning)
      mReplayView.selectableColorsUpdated();
  }

  @SuppressWarnings("unchecked")  // the varargs of Iterable<...>
  private void minimizeEverything() {
    if (mAnalyze == null) {
      mMinimize = new Minimize(this, true);
      mMinimize.execute(mInsights.errors, mInsights.assignments.values(), mInsights.disproofs.values());
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

  void disproofComplete(Disprove instance) {
    if (instance == mDisprove) {
      mDisprove = null;
      mInsights.disproofsSetSize = 0;
    }
  }

  void addDisproof(DisprovedAssignment disproof, boolean minimized) {
    Location loc = disproof.getDisprovedAssignment().location;
    mInsights.disproofs.put(loc, minimized ? new InsightMin(disproof, true) : new InsightMin(disproof));
    if (!mRunning)
      mReplayView.invalidateLocation(loc);
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
      if (instance.mEverything) {
        mDisprove = new Disprove(this);
        mDisprove.execute();
      } else minimizeEverything();
    }
  }

  @Override public void onClick(View v) {
    switch (v.getId()) {
      case R.id.play:
      case R.id.back:
        mControls.setVisibility(View.GONE);
        mPauseControls.setVisibility(View.VISIBLE);
        mForward = (v.getId() == R.id.play);
        mRunning = true;
        mReplayView.postDelayed(replayCycler, SET_CYCLE_MILLIS);
        startAnalysis();
        invalidateOptionsMenu();
        mReplayLocation.setEnabled(false);
        displayInsightAndError(null);
        break;

      case R.id.pause:
        mControls.setVisibility(View.VISIBLE);
        mPauseControls.setVisibility(View.GONE);
        mRunning = false;
        startAnalysis();
        invalidateOptionsMenu();
        mReplayLocation.setEnabled(true);
        displayInsightAndError(null);
        break;

      case R.id.next:
      case R.id.previous:
        mRunning = false;
        mForward = (v.getId() == R.id.next);
        stepReplay(true);
        break;

      case R.id.undo:
        if (mUndoStack.getPosition() > mHistoryPosition)
          try {
            mUndoStack.undo();
          } catch (CommandException e) {
            Log.e(TAG, "Undo failed", e);
            break;
          }
        mPendingDisproof = null;
        startAnalysis();
        setUndoEnablement();
        mReplayView.setSelected(null);
        displayInsightAndError(null);
        break;

      case R.id.clear:
        mPendingDisproof = null;
        setUndoEnablement();
        displayInsightAndError(null);
        break;

      case R.id.apply:
        doCommand(new ElimCommand(mPendingDisproof.getDisprovedAssignment()));
        mPendingDisproof = null;
        startAnalysis();
        setUndoEnablement();
        displayInsightAndError(null);
        break;

      case R.id.redo:
        try {
          mUndoStack.redo();
        } catch (CommandException e) {
          Log.e(TAG, "Redo failed", e);
          break;
        }
        mPendingDisproof = null;
        startAnalysis();
        setUndoEnablement();
        mReplayView.setSelected(null);
        displayInsightAndError(null);
        break;
    }
  }

  private void setControlsEnablement() {
    findViewById(R.id.play).setEnabled(mHistoryPosition < mHistory.size());
    findViewById(R.id.next).setEnabled(mHistoryPosition < mHistory.size());
    findViewById(R.id.back).setEnabled(mHistoryPosition > 0);
    findViewById(R.id.previous).setEnabled(mHistoryPosition > 0);
  }

  private void setUndoEnablement() {
    boolean undoEnabled = mUndoStack.getPosition() > mHistoryPosition;
    findViewById(R.id.undo).setEnabled(undoEnabled);
    findViewById(R.id.clear).setEnabled(mPendingDisproof != null);
    findViewById(R.id.apply).setEnabled(mPendingDisproof != null);
    findViewById(R.id.redo).setEnabled(mUndoStack.canRedo());
  }

  @Override public void onSelect(Location loc) {
    mPendingDisproof = null;
    if (mInsights != null) {
      InsightMin insightMin = mInsights.assignments.get(loc);
      if (insightMin == null) {
        insightMin = mInsights.disproofs.get(loc);
        if (insightMin != null && mExploring) {
          mPendingDisproof = (DisprovedAssignment) insightMin.insight;
          setUndoEnablement();
        }
      } else if (mExploring) {
        doCommand(makeMoveCommand(insightMin.insight.getImpliedAssignment()));
        startAnalysis();
        setUndoEnablement();
      }
      displayInsightAndError(insightMin);
    }
  }

  private void displayInsightAndError(InsightMin insightMin) {
    mReplayView.clearInsights();
    mToBeDisplayed.clear();
    if (insightMin != null) displayInsight(insightMin);
    InsightMin error = null;
    if (mInsights != null && !mInsights.errors.isEmpty()) {
      error = mInsights.errors.get(0);
      displayInsight(error);
    }
    minimizeInsights(insightMin, error);
  }

  private void displayInsight(InsightMin insightMin) {
    mReplayView.addInsight(insightMin.getMinimizedInsight());
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

  private void reflectCurrentMove() {
    long timestamp = 0;
    Location loc = null;
    if (mHistoryPosition > 0) {
      Move move = mHistory.get(mHistoryPosition - 1);
      timestamp = move.timestamp;
      updateTrail(move.trailId);
      loc = move.getLocation();
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

  @Nullable Assignment nextAssignment() {
    if (mHistoryPosition < mHistory.size())
      return mHistory.get(mHistoryPosition).getAssignment();
    return null;
  }

  private void startAnalysis() {
    if (mAnalyze == null) {
      if (mMinimize != null) mMinimize.cancel();
      if (mDisprove != null) mDisprove.cancel();
      mAnalyze = new Analyze(this);
      mAnalyze.execute(mReplayView.getGridMarks());
      if (!mRunning)
        mProgress.setVisibility(View.VISIBLE);
    } else {
      mAnalysisRanLong = true;
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
    volatile boolean minimized;

    InsightMin(Insight insight) {
      this(insight, insight.getDepth() == 0);
    }

    InsightMin(DisprovedAssignment insight) {
      this(insight, insight.getDepth() == 1);
    }

    InsightMin(Insight insight, boolean minimized) {
      this.insight = insight;
      this.minimized = minimized;
    }

    boolean minimize(GridMarks gridMarks) {
      if (!minimized) {
        insight = Analyzer.minimize(gridMarks, insight);
        minimized = !Thread.currentThread().isInterrupted();
      }
      return minimized;
    }

    Insight getMinimizedInsight() {
      return minimized ? insight : insight.getNub();
    }

    @Override public String toString() {
      if (minimized) return insight.toString();
      return insight.toShortString();
    }
  }

  private static class Insights {
    final GridMarks gridMarks;
    final Map<Location, InsightMin> assignments = Maps.newLinkedHashMap();
    final Map<Location, InsightMin> disproofs = Maps.newHashMap();
    final List<InsightMin> errors = Lists.newArrayList();
    int disproofsSetSize;

    Insights(GridMarks gridMarks) {
      this.gridMarks = gridMarks;
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
      throw new UnsupportedOperationException();
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

  private static class Analyze extends WorkerFragment.ActivityTask<ReplayActivity, GridMarks, Void, Insights> {

    private final Assignment mTarget;
    boolean mCancelable;

    Analyze(ReplayActivity activity) {
      super(activity);
      mTarget = activity.mRunning && activity.mForward ? activity.nextAssignment() : null;
    }

    @Override protected Insights doInBackground(final GridMarks... params) {
      if (mTarget == null) publishProgress();  // allow cancellation right away
      final Insights answer = new Insights(params[0]);
      Analyzer.analyze(answer.gridMarks, new Analyzer.Callback() {
        private boolean mAiming = mTarget != null;
        @Override public void take(Insight insight) {
          if (insight.isError()) {
            InsightMin insightMin = new InsightMin(insight);
            answer.errors.add(insightMin);
            hit(insightMin);
          } else if (insight.isAssignment()) {
            Assignment assignment = insight.getImpliedAssignment();
            if (!answer.assignments.containsKey(assignment.location)
                && (mTarget == null || assignment.equals(mTarget))) {
              InsightMin insightMin = new InsightMin(insight);
              answer.assignments.put(assignment.location, insightMin);
              hit(insightMin);
            }
          }
        }
        private void hit(InsightMin insightMin) {
          if (mAiming) {
            mAiming = false;
            insightMin.minimize(answer.gridMarks);
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

  private static class Minimize extends WorkerFragment.ActivityTask<ReplayActivity, Iterable<InsightMin>, InsightMin, Void> {
    private final GridMarks mGridMarks;
    final boolean mEverything;

    Minimize(ReplayActivity activity, boolean everything) {
      super(activity);
      mGridMarks = activity.mInsights.gridMarks;
      mEverything = everything;
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
      extends WorkerFragment.ActivityTask<ReplayActivity, Void, DisprovedAssignment, Void> {
    private final Grid mPuzzle;
    private final Insights mInsights;
    private GridMarks mSolution;
    private int mSetSize;

    Disprove(ReplayActivity activity) {
      super(activity);
      mPuzzle = activity.mGame.getPuzzle();
      mInsights = activity.mInsights;
      mSolution = activity.mSolution;
      mSetSize = mInsights.disproofsSetSize;
    }

    @Override protected Void doInBackground(Void... params) {
      GridMarks solution = getSolution();
      GridMarks current = mInsights.gridMarks;
      LocSet available = LocSet.all()
          .minus(current.grid.keySet())
          .minus(mInsights.assignments.keySet())
          .minus(mInsights.disproofs.keySet());
      List<PossibleAssignment> possibles = findPossibles(solution, current, available);

      // First pass: look for assignments that cause an error on recursive assignment.
      for (PossibleAssignment p : possibles) {
        if (available.isEmpty() || wasCanceled()) break;
        checkForDisproof(current, available, p, true);
      }

      // Second pass: do the rest.
      for (PossibleAssignment p : possibles) {
        if (available.isEmpty() || wasCanceled()) break;
        checkForDisproof(current, available, p, false);
      }

      return null;
    }

    @Override protected void onProgressUpdate(ReplayActivity activity, DisprovedAssignment... disproofs) {
      mInsights.disproofsSetSize = mSetSize;
      activity.addDisproof(disproofs[0], true);
    }

    @Override protected void onPostExecute(ReplayActivity activity, Void result) {
      if (mSolution != null) activity.mSolution = mSolution;
      activity.disproofComplete(this);
    }

    private GridMarks getSolution() {
      if (mSolution == null) {
        Solver.Result result = Solver.solve(mPuzzle);
        mSolution = new GridMarks(result.solution);
      }
      return mSolution;
    }

    private List<PossibleAssignment> findPossibles(GridMarks solution, GridMarks current,
        LocSet available) {
      List<PossibleAssignment> possibles = Lists.newArrayList();
      for (Unit unit : Unit.allUnits())
        for (Numeral num : Numeral.ALL) {
          UnitSubset set = current.marks.get(unit, num);
          UnitSubset solSet = solution.marks.get(unit, num);
          if (set.size() > 1 && solSet.size() == 1)
            for (Location loc : available.and(set.minus(solSet))) {
              possibles.add(new PossibleAssignment(loc, num, set.size()));
            }
        }
      for (Location loc : available) {
        NumSet set = current.marks.get(loc);
        NumSet solSet = solution.marks.get(loc);
        if (set.size() > 1 && solSet.size() == 1)
          for (Numeral num : set.minus(solSet))
            possibles.add(new PossibleAssignment(loc, num, set.size()));
      }
      Collections.sort(possibles);
      return possibles;
    }

    private void checkForDisproof(
        GridMarks current, LocSet available, PossibleAssignment p, boolean fastPath) {
      if (!available.contains(p.loc)) return;
      if (mSetSize > 0 && p.setSize > mSetSize) return;
      if (fastPath && current.marks.toBuilder().assignRecursively(p.loc, p.num)) return;

      ErrorGrabber grabber = new ErrorGrabber();
      Analyzer.analyze(current.toBuilder().assign(p.loc, p.num).build(), grabber);

      if (grabber.error != null) {
        mSetSize = p.setSize;
        DisprovedAssignment disproof = new DisprovedAssignment(p.toAssignment(), grabber.error);
        disproof = (DisprovedAssignment) Analyzer.minimize(current, disproof);
        publishProgress(disproof);
        available.remove(p.loc);
      }
    }
  }
}
