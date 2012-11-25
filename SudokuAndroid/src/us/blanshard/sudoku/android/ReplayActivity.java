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

import static java.util.Collections.singleton;

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
import us.blanshard.sudoku.insight.UnfoundedAssignment;

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
  private SeekBar mReplayLocation;
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
  private Insight mPendingInsight;
  private Command mPendingCommand;

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
    mReplayLocation = (SeekBar) findViewById(R.id.replay_location);
    mMoveNumber = (TextView) findViewById(R.id.move_number);
    mTimer = (TextView) findViewById(R.id.timer);

    mReplayView.setOnSelectListener(this);
    mReplayView.setSelectableColorsFunction(selectableColors);
    mReplayLocation.setOnSeekBarChangeListener(this);

    setUpButton(R.id.play);
    setUpButton(R.id.pause);
    setUpButton(R.id.back);

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
        case R.id.menu_undo: {
          boolean enabled = (mExploring && mUndoStack.canUndo())
              || (!mExploring && mHistoryPosition > 0);
          item.setEnabled(enabled);
          break;
        }
        case R.id.menu_redo: {
          boolean enabled = (mExploring && mUndoStack.canRedo())
              || (!mExploring && mHistory != null && mHistoryPosition < mHistory.size());
          item.setEnabled(enabled);
          break;
        }
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

      case R.id.menu_undo:
      case R.id.menu_redo:
        boolean forward = item.getItemId() == R.id.menu_redo;
        if (mExploring) {
          undoOrRedo(forward);
        } else {
          mForward = forward;
          stepReplay(true);
        }
        return true;

      case R.id.menu_clear:
        mPendingInsight = null;
        mPendingCommand = null;
        invalidateOptionsMenu();
        displayInsightAndError(null);
        return true;

      case R.id.menu_apply:
        doCommand(mPendingCommand);
        mPendingInsight = null;
        mPendingCommand = null;
        mExploring = true;
        startAnalysis();
        invalidateOptionsMenu();
        setControlsEnablement();
        displayInsightAndError(null);
        return true;

      case R.id.menu_resume_replay:
        mExploring = false;
        try {
          while (mUndoStack.getPosition() > mHistoryPosition)
            mUndoStack.undo();
        } catch (CommandException e) {
          Log.e(TAG, "Can't resume replay", e);
        }
        invalidateOptionsMenu();
        setControlsEnablement();
        pause();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void undoOrRedo(boolean redo) {
    try {
      if (redo) mUndoStack.redo();
      else mUndoStack.undo();
    } catch (CommandException e) {
      Log.e(TAG, "Undo/redo failed", e);
      return;
    }

    mPendingInsight = null;
    mPendingCommand = null;
    startAnalysis();
    if (mUndoStack.getPosition() <= mHistoryPosition) {
      mExploring = false;
      setControlsEnablement();
    }
    invalidateOptionsMenu();
    mReplayView.setSelected(null);
    displayInsightAndError(null);
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
    if (!mRunning) {
      mReplayView.selectableColorsUpdated();
      onSelect(mReplayView.getSelected());
    }
  }

  @SuppressWarnings("unchecked")  // the varargs of Iterable<...>
  private void minimizeEverything() {
    if (mAnalyze == null) {
      if (mDisprove != null) mDisprove.cancel();
      if (mMinimize != null) mMinimize.cancel();
      mMinimize = new Minimize(this, true);
      mMinimize.execute(singleton(mInsights.error), mInsights.assignments.values(), mInsights.disproofs.values());
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
    if (instance == mDisprove)
      mDisprove = null;
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
        mForward = (v.getId() == R.id.play);
        mRunning = true;
        mReplayView.postDelayed(replayCycler, SET_CYCLE_MILLIS);
        startAnalysis();
        invalidateOptionsMenu();
        setControlsEnablement();
        mReplayLocation.setEnabled(false);
        displayInsightAndError(null);
        break;

      case R.id.pause:
        mRunning = false;
        startAnalysis();
        invalidateOptionsMenu();
        setControlsEnablement();
        mReplayLocation.setEnabled(true);
        displayInsightAndError(null);
        break;
    }
  }

  private void setControlsEnablement() {
    if (mExploring) {
      mControls.setVisibility(View.INVISIBLE);
    } else {
      mControls.setVisibility(View.VISIBLE);
      findViewById(R.id.play).setEnabled(!mRunning && mHistoryPosition < mHistory.size());
      findViewById(R.id.back).setEnabled(!mRunning && mHistoryPosition > 0);
      findViewById(R.id.pause).setEnabled(mRunning);
    }
  }

  @Override public void onSelect(Location loc) {
    mPendingInsight = null;
    mPendingCommand = null;
    if (mInsights != null) {
      InsightMin insightMin = mInsights.assignments.get(loc);
      if (insightMin == null) {
        insightMin = mInsights.disproofs.get(loc);
        if (insightMin != null) {
          DisprovedAssignment disproof = (DisprovedAssignment) insightMin.insight;
          mPendingInsight = disproof;
          mPendingCommand = new ElimCommand(disproof.getDisprovedAssignment());
          invalidateOptionsMenu();
        }
      } else {
        mPendingInsight = insightMin.insight;
        mPendingCommand = makeMoveCommand(insightMin.insight.getImpliedAssignment());
        invalidateOptionsMenu();
      }
      displayInsightAndError(insightMin);
    }
  }

  private void displayInsightAndError(InsightMin insightMin) {
    mReplayView.clearInsights();
    mToBeDisplayed.clear();
    if (insightMin != null) displayInsight(insightMin);
    InsightMin error = null;
    if (mInsights != null && mInsights.error != null) {
      displayInsight(mInsights.error);
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
        Location toClear = null;
        if (!mExploring) {
          Assignment asmt = nextAssignment();
          if (asmt != null) toClear = asmt.location;
        }
        mAnalyze.execute(mReplayView.getGridMarks(toClear));
        if (!mRunning)
          mProgress.setVisibility(View.VISIBLE);
      }
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
        trails = ImmutableList.of(new TrailItem(mGame.getTrail(stateId), Color.DKGRAY, Color.LTGRAY, true, false));
      mReplayView.setTrails(trails);
      mReplayView.setTrailActive(isTrail);
    }
  }

  private static class FetchGame extends WorkerFragment.ActivityTask<
      ReplayActivity, Long, Void, Database.Game> {
    private final Database mDb;
    private GridMarks mSolution;

    FetchGame(ReplayActivity activity) {
      super(activity);
      mDb = activity.mDb;
    }

    @Override protected Database.Game doInBackground(Long... params) {
      Database.Game answer = mDb.getGame(params[0]);
      Solver.Result result = Solver.solve(answer.puzzle);
      mSolution = new GridMarks(result.solution);
      mDb.noteReplay(answer._id);
      return answer;
    }

    @Override protected void onPostExecute(ReplayActivity activity, Database.Game game) {
      activity.mSolution = mSolution;
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
    @Nullable InsightMin error;
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
          answer.error.minimize(postTarget);
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
    private final Insights mInsights;
    private final GridMarks mSolution;
    private int mSetSize;

    Disprove(ReplayActivity activity) {
      super(activity);
      mInsights = activity.mInsights;
      mSolution = activity.mSolution;
      mSetSize = mInsights.disproofsSetSize;
    }

    @Override protected Void doInBackground(Void... params) {
      GridMarks current = mInsights.gridMarks;
      LocSet available = LocSet.all()
          .minus(current.grid.keySet())
          .minus(mInsights.assignments.keySet())
          .minus(mInsights.disproofs.keySet());
      List<PossibleAssignment> possibles = findPossibles(mSolution, current, available);

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
