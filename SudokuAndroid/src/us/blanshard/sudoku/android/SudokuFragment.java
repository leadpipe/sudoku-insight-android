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

import static us.blanshard.sudoku.android.Json.GSON;
import static us.blanshard.sudoku.android.SudokuView.MAX_VISIBLE_TRAILS;
import static us.blanshard.sudoku.core.Numeral.number;
import static us.blanshard.sudoku.core.Numeral.numeral;

import us.blanshard.sudoku.android.Database.Attempt;
import us.blanshard.sudoku.android.Database.AttemptState;
import us.blanshard.sudoku.android.SudokuView.OnMoveListener;
import us.blanshard.sudoku.android.WorkerFragment.Independence;
import us.blanshard.sudoku.android.WorkerFragment.Priority;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.game.Command;
import us.blanshard.sudoku.game.CommandException;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.MoveCommand;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.Sudoku.State;
import us.blanshard.sudoku.game.UndoStack;
import us.blanshard.sudoku.gen.Generator;
import us.blanshard.sudoku.insight.Evaluator;
import us.blanshard.sudoku.insight.Rating;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The fragment for playing a sudoku.
 *
 * @author Luke Blanshard
 */
public class SudokuFragment
    extends FragmentBase
    implements OnMoveListener, OnCheckedChangeListener, OnItemClickListener,
               OnItemLongClickListener {

  private static final long DB_UPDATE_MILLIS = TimeUnit.SECONDS.toMillis(10);

  private static int sUpdateAttemptCount;

  private UndoStack mUndoStack = new UndoStack();
  private Database.Attempt mAttempt;
  private Sudoku mGame;
  private boolean mResumed;
  private Grid.State mState;
  private final Sudoku.Registry mRegistry = Sudoku.newRegistry();
  private TrailAdapter mTrailAdapter;
  private SudokuView mSudokuView;
  private View mRatingFrame;
  private ProgressBar mProgress;
  private ToggleButton mEditTrailToggle;
  private ListView mTrailsList;
  private TextView mTimer;
  private TextView mRating;
  private Toast mToast;

  private final Runnable timerUpdater = new Runnable() {
    @Override public void run() {
      String time = "";
      if (mGame != null) {
        long millis = mGame.elapsedMillis();
        time = ToText.elapsedTime(millis);
        if (mGame.isRunning()) {
          mTimer.postDelayed(timerUpdater, (millis / 1000 + 1) * 1000 - millis);
        }
      }
      if (mTimer != null) {
        mTimer.setText(time);
      }
    }
  };

  private final Runnable attemptSaver = new Runnable() {
    @Override public void run() {
      if (updateAttempt(false) && mGame.isRunning()) {
        new SaveAttempt(SudokuFragment.this).execute(mAttempt.clone());
        mSudokuView.postDelayed(this, DB_UPDATE_MILLIS);
      }
    }
  };

  // Public methods

  public void doCommand(Command command) {
    try {
      mUndoStack.doCommand(command);
      stateChanged();
    } catch (CommandException e) {
      showError(e.getMessage());
    }
  }

  public Sudoku.Registry getRegistry() {
    return mRegistry;
  }

  public Sudoku getGame() {
    return mGame;
  }

  public long getPuzzleId() {
    if (mAttempt == null) return 0;
    return mAttempt.puzzleId;
  }

  private void setAttempt(Database.Attempt attempt, List<Move> history,
      String title, String status) {
    mAttempt = attempt;
    setRatingText();
    if (attempt == null) {
      setGame(null);
      getActivity().setTitle("");
    } else {
      if (attempt.attemptState == AttemptState.UNSTARTED)
        showRatingFrame();
      else
        new CheckNextAttempt(this).execute(mAttempt._id);
      finishSettingAttempt(history, title, status);
    }
  }

  private void showRatingFrame() {
    final JsonObject props = new JsonParser().parse(mAttempt.properties).getAsJsonObject();

    String ratingTitle = props.has(Generator.NAME_KEY)
        ? getString(R.string.text_puzzle_name, props.get(Generator.NAME_KEY).getAsString(), mAttempt.puzzleId)
        : getString(R.string.text_puzzle_number, mAttempt.puzzleId);
    TextView titleView = (TextView) mRatingFrame.findViewById(R.id.rating_title);
    titleView.setText(ratingTitle);

    Rating rating = mAttempt.rating;
    boolean mustRate = rating == null || !rating.estimateComplete;
    Spanned message = mustRate
        ? Html.fromHtml(ToText.ratingProgressHtml(getActivity(), 0))
        : Html.fromHtml(ToText.ratingHtml(getActivity(), rating));
    TextView textView = (TextView) mRatingFrame.findViewById(R.id.rating_text);
    textView.setText(message);

    mRatingFrame.findViewById(R.id.rating_play).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        Database.startUnstartedAttempt(mAttempt);
        new CheckNextAttempt(SudokuFragment.this).execute(mAttempt._id);
        mRatingFrame.setVisibility(View.GONE);
        resumeGameIfStarted();
      }
    });

    mRatingFrame.findViewById(R.id.rating_skip).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        mRatingFrame.setVisibility(View.GONE);
        mProgress.setVisibility(View.VISIBLE);
        Database.startUnstartedAttempt(mAttempt);
        mAttempt.attemptState = AttemptState.SKIPPED;
        ThreadPolicy policy = StrictMode.allowThreadDiskWrites();
        try {
          mDb.updateAttempt(mAttempt);
        } finally {
          StrictMode.setThreadPolicy(policy);
        }
        long attemptId = mAttempt._id;
        setAttempt(null, null, null, null);
        WorkerFragment.cancelAll(SudokuFragment.this);
        new FetchFindOrMakePuzzle(SudokuFragment.this).execute(attemptId);
      }
    });

    if (mustRate)
      new RatePuzzle(SudokuFragment.this, textView).execute();
    else
      new CheckNextAttempt(this).execute(mAttempt._id);

    if (mSudokuView.hasGridDimensions()) {
      updateRatingFrame();
    } else {
      mSudokuView.getViewTreeObserver().addOnGlobalLayoutListener(
          new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override public void onGlobalLayout() {
          if (mSudokuView.hasGridDimensions()) {
            mSudokuView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            updateRatingFrame();
          }
        }
      });
    }
    mRatingFrame.setVisibility(View.VISIBLE);
  }

  private void updateRatingFrame() {
    LayoutParams params = mRatingFrame.getLayoutParams();
    params.height = mSudokuView.getGridHeight();
    params.width = mSudokuView.getGridWidth();
    mRatingFrame.setLayoutParams(params);
  }

  private void finishSettingAttempt(List<Move> history, String title, String status) {
    Sudoku game = new Sudoku(mAttempt.clues, mRegistry, history, mAttempt.elapsedMillis);
    getActivity().setTitle(title);
    setGame(game);
    resumeGameIfStarted();
    mProgress.setVisibility(View.GONE);
    if (mAttempt.uiState != null) {
      GameJson.setFactory(game);
      UiState uiState = GSON.fromJson(mAttempt.uiState, UiState.class);
      GameJson.clearFactory();
      mUndoStack = uiState.undo;
      mSudokuView.setDefaultChoice(numeral(uiState.defaultChoice));
      restoreTrails(uiState.trailOrder, uiState.numVisibleTrails, uiState.numOffTrails);
      mEditTrailToggle.setChecked(uiState.trailActive);
    }
    if (mAttempt.attemptState.isInPlay()) {
      mPrefs.setCurrentAttemptIdAsync(mAttempt._id);
    }
    if (status != null)
      showStatus(status);
  }

  private void resumeGameIfStarted() {
    if (mAttempt.attemptState == AttemptState.STARTED) {
      updateState();
      if (mState != Grid.State.SOLVED && mResumed)
        mGame.resume();
      mSudokuView.postDelayed(attemptSaver, DB_UPDATE_MILLIS);
      stateChanged();
    }
  }

  void setRatingText() {
    if (mAttempt == null || mAttempt.rating == null) {
      mRating.setText("");
    } else {
      mRating.setText(Html.fromHtml(ToText.ratingSummaryHtml(getActivity(), mAttempt.rating)));
    }
  }

  private void setGame(Sudoku game) {
    mGame = game;
    mState = Grid.State.INCOMPLETE;
    mUndoStack = new UndoStack();
    mSudokuView.setGame(game);
    mSudokuView.setDefaultChoice(Numeral.of(1));
    List<TrailItem> vis = Lists.newArrayList(), invis = Lists.newArrayList();
    if (game != null)
      for (int i = game.getNumTrails() - 1; i >= 0; --i)
        invis.add(makeTrailItem(i, false, false));
    updateTrails(vis, invis);
    stateChanged();
  }

  public void showError(String s) {
    showStatus(s);
  }

  public void showStatus(String s) {
    cancelStatus();
    mToast = makeToast(s);
    mToast.show();
  }

  private Toast makeToast(String s) {
    return Toast.makeText(getActivity().getApplicationContext(), s, Toast.LENGTH_LONG);
  }

  public void showSolution() {
    DialogFragment dialog = new DialogFragment() {
      @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
            .setMessage(R.string.dialog_show_solution_message)
            .setPositiveButton(R.string.button_show_solution, new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                  dialog.dismiss();
                  mAttempt.attemptState = AttemptState.GAVE_UP;
                  transitionToInfoPage();
                }
            })
            .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                  dialog.dismiss();
                }
            })
            .create();
      }
    };
    dialog.show(getFragmentManager(), "cancelPuzzle");
  }

  public void trailCheckChanged(TrailItem item, boolean isChecked) {
    if (isChecked) {
      item.off = false;
      int count = 0;
      for (int i = 0; i < mTrailAdapter.getCount(); ++i) {
        TrailItem item2 = mTrailAdapter.getItem(i);
        if (item2.shown) {
          if (++count >= MAX_VISIBLE_TRAILS)
            item2.shown = false;
        }
      }
    }
    item.shown = isChecked;
    fixVisibleItems();
    if (isChecked)
      mTrailsList.smoothScrollToPosition(mTrailAdapter.getPosition(item));
  }

  public boolean isTrailActive() {
    return mSudokuView.isTrailActive();
  }

  // Listener implementations

  @Override public void onMove(State state, Location loc, Numeral num) {
    doCommand(new MoveCommand(state, loc, num));
  }

  @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    if (buttonView == mEditTrailToggle) {
      mSudokuView.setTrailActive(isChecked);
      fixVisibleItems();
    }
  }

  @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    makeActiveTrailItem(mTrailAdapter.getItem(position));
  }

  @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
    TrailItem item = mTrailAdapter.getItem(position);
    item.off = true;
    item.shown = false;
    fixVisibleItems();
    return true;
  }

  // Fragment overrides

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    setHasOptionsMenu(true);
    return inflater.inflate(R.layout.board, container, true);
  }

  @Override public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
    menuInflater.inflate(R.menu.board, menu);
    super.onCreateOptionsMenu(menu, menuInflater);
  }

  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    long attemptId = 0;
    if (getActivity().getIntent().hasExtra(Extras.ATTEMPT_ID)) {
      attemptId = getActivity().getIntent().getExtras().getLong(Extras.ATTEMPT_ID);
    } else if (savedInstanceState != null && savedInstanceState.containsKey(Extras.ATTEMPT_ID)) {
      attemptId = savedInstanceState.getLong(Extras.ATTEMPT_ID);
    } else if (mPrefs.hasCurrentAttemptId()) {
      attemptId = mPrefs.getCurrentAttemptId();
    }
    WorkerFragment.cancelAll(this);
    new FetchFindOrMakePuzzle(this).execute(attemptId);
    mProgress.setVisibility(View.VISIBLE);
  }

  private void showFirstTimeDialog() {
    if (mPrefs.hasUserSeenNoticeHere())
      return;
    final int messageId = mPrefs.hasUserEverSeenNotice()
        ? R.string.dialog_first_time_here_message
        : R.string.dialog_first_time_message;
    DialogFragment dialog = new DialogFragment() {
      @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
            .setMessage(messageId)
            .setNeutralButton(android.R.string.ok, new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                  dialog.dismiss();
                }
            })
            .create();
      }
      @Override public void onDismiss(DialogInterface dialog) {
        mPrefs.setUserHasSeenNoticeAsync();
      }
    };
    dialog.show(getFragmentManager(), "firstTime");
    gameShowing(false);
  }

  @Override public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    saveAttemptFromUiThread();
    if (mAttempt != null) outState.putLong(Extras.ATTEMPT_ID, mAttempt._id);
  }

  private boolean updateAttempt(boolean suspend) {
    ++sUpdateAttemptCount;
    if (mGame == null) return false;
    if (suspend) mGame.suspend();
    mAttempt.history = GSON.toJson(mGame.getHistory());
    mAttempt.elapsedMillis = mGame.elapsedMillis();
    mAttempt.numMoves = mGame.getHistory().size();
    mAttempt.numTrails = mGame.getNumTrails();
    if (mAttempt.attemptState.isInPlay()) {
      mAttempt.uiState = GSON.toJson(makeUiState());
    } else {
      mAttempt.uiState = null;
    }
    return true;
  }

  private void saveAttemptFromUiThread() {
    if (updateAttempt(true)) {
      ThreadPolicy policy = StrictMode.allowThreadDiskWrites();
      try {
        saveAttempt(mDb, mAttempt, sUpdateAttemptCount);
      } finally {
        StrictMode.setThreadPolicy(policy);
      }
    }
  }

  private static synchronized void saveAttempt(Database db, Database.Attempt attempt, int count) {
    if (count == sUpdateAttemptCount)
      db.updateAttempt(attempt);
  }

  private static Database.Attempt generateAndStorePuzzle(Database db, Prefs prefs) {
    Calendar cal = Calendar.getInstance();
    int counter = prefs.getNextCounter(cal);

    while (true) {
      JsonObject props = Generator.generateBasicPuzzle(
          prefs.getStream(), cal.get(Calendar.YEAR),
          1 + cal.get(Calendar.MONTH), counter);
      long id = db.addGeneratedPuzzle(props);
      Database.Attempt attempt = db.getCurrentAttemptForPuzzle(id);
      if (attempt == null || !attempt.attemptState.isInPlay()) {
        // Whoops, we've already seen this puzzle: our counter got out of sync.
        // Loop around and generate the next one.
      } else if (prefs.getProperOnly() && props.get(Generator.NUM_SOLUTIONS_KEY).getAsInt() > 1) {
        // Skip improper puzzles if the settings say so.
        Database.startUnstartedAttempt(attempt);
        attempt.attemptState = AttemptState.SKIPPED;
        db.updateAttempt(attempt);
      } else {
        prefs.setNextCounterSync(cal, counter);
        return attempt;
      }
      ++counter;
    }
  }

  private static class FetchFindOrMakePuzzle extends WorkerFragment.Task<SudokuFragment, Long, Void, Database.Attempt> {
    private final Database mDb;
    private final Prefs mPrefs;
    private final Context mAppContext;

    private List<Move> mHistory;
    private String mTitle;
    private String mStatus;

    FetchFindOrMakePuzzle(SudokuFragment fragment) {
      super(fragment, Priority.FOREGROUND, Independence.DEPENDENT);
      mDb = fragment.mDb;
      mPrefs = fragment.mPrefs;
      mAppContext = fragment.getActivity().getApplicationContext();
    }

    @Override protected Database.Attempt doInBackground(Long... params) {
      Database.Attempt answer = mDb.getAttempt(params[0]);
      if (answer == null || !answer.attemptState.isInPlay())
        answer = mDb.getFirstOpenAttempt();
      if (answer == null || !answer.attemptState.isInPlay())
        answer = generateAndStorePuzzle(mDb, mPrefs);
      if (answer != null)
        makeAdditionalArtifacts(answer);
      return answer;
    }

    /**
     * The purpose of this method is to get as many classes loaded by the
     * background thread as possible, to reduce the work done on the UI thread.
     */
    private void makeAdditionalArtifacts(Database.Attempt attempt) {
      mHistory = GameJson.toHistory(GSON, attempt.history);
      mTitle = mAppContext.getString(R.string.text_puzzle_number, attempt.puzzleId);
      if (attempt.elements != null && !attempt.elements.isEmpty()) {
        mStatus = Joiner.on(mAppContext.getString(R.string.text_collection_separator)).join(
            Iterables.transform(attempt.elements, new Function<Database.Element, String>() {
                @Override public String apply(Database.Element element) {
                  return ToText.collectionNameAndTimeText(mAppContext, element);
                }
            }));
      }
    }

    @Override protected void onPostExecute(SudokuFragment fragment, Database.Attempt attempt) {
      fragment.setAttempt(attempt, mHistory, mTitle, mStatus);
    }
  }

  private static class RatePuzzle extends WorkerFragment.Task<SudokuFragment, Void, Double, Rating> {
    private final Database mDb;
    private final Database.Attempt mAttempt;
    private final TextView mTextView;

    /**
     * @param fragment
     */
    public RatePuzzle(SudokuFragment fragment, TextView view) {
      super(fragment, WorkerFragment.Priority.FOREGROUND, WorkerFragment.Independence.FREE);
      mDb = fragment.mDb;
      mAttempt = fragment.mAttempt;
      mTextView = view;
    }

    @Override protected Rating doInBackground(Void... inputs) {
      Rating rating = Evaluator.evaluate(mAttempt.clues, new Evaluator.Callback() {
        @Override public void updateEstimate(double minSeconds) {
          publishProgress(minSeconds);
        }
        @Override public void disproofsRequired() {}
      });
      if (rating.estimateComplete)
        mDb.setPuzzleRating(mAttempt.puzzleId, rating);
      return rating;
    }

    @Override protected void onProgressUpdate(SudokuFragment anchor, Double... values) {
      mTextView.setText(Html.fromHtml(ToText.ratingProgressHtml(anchor.getActivity(), values[0])));
    }

    @Override protected void onPostExecute(SudokuFragment anchor, Rating output) {
      if (output.estimateComplete) {
        mAttempt.rating = output;
        mTextView.setText(Html.fromHtml(ToText.ratingHtml(anchor.getActivity(), output)));
        anchor.setRatingText();
        new CheckNextAttempt(anchor).execute(mAttempt._id);
      }
    }
  }

  private static class CheckNextAttempt extends WorkerFragment.Task<SudokuFragment, Long, Void, Rating> {
    private final Database mDb;
    private final Prefs mPrefs;
    private final Grid mPuzzle;
    private final long mPuzzleId;
    private final Database.Attempt mAttempt;

    CheckNextAttempt(SudokuFragment fragment) {
      super(fragment);
      mDb = fragment.mDb;
      mPrefs = fragment.mPrefs;
      if (fragment.mAttempt == null || fragment.mAttempt.rating != null) {
        mPuzzle = null;
        mPuzzleId = 0;
        mAttempt = null;
      } else {
        mPuzzle = fragment.mAttempt.clues;
        mPuzzleId = fragment.mAttempt.puzzleId;
        mAttempt = fragment.mAttempt;
      }
    }

    @Override protected Rating doInBackground(Long... params) {
      int numOpenAttempts = mDb.getNumOpenAttempts();
      boolean hasNext = numOpenAttempts > 1
          || numOpenAttempts == 1 && (params[0] == null || mDb.getFirstOpenAttempt()._id != params[0]);
      if (!hasNext) {
        Attempt attempt = generateAndStorePuzzle(mDb, mPrefs);
        Rating rating = Evaluator.evaluate(attempt.clues, null);
        if (rating.estimateComplete)
          mDb.setPuzzleRating(attempt.puzzleId, rating);
      }
      if (!wasCanceled() && mPuzzle != null) {
        Rating rating = Evaluator.evaluate(mPuzzle, null);
        if (rating.estimateComplete)
          mDb.setPuzzleRating(mPuzzleId, rating);
        return rating;
      }
      return null;
    }

    @Override protected void onPostExecute(SudokuFragment anchor, Rating output) {
      if (mAttempt != null && mAttempt == anchor.mAttempt) {
        mAttempt.rating = output;
        anchor.setRatingText();
      }
    }
  }

  private static class SaveAttempt extends WorkerFragment.Task<SudokuFragment, Database.Attempt, Void, Void> {
    private final Database mDb;
    private final int mUpdateAttemptCount;

    SaveAttempt(SudokuFragment fragment) {
      super(fragment, Priority.BACKGROUND, Independence.FREE);
      mDb = fragment.mDb;
      mUpdateAttemptCount = sUpdateAttemptCount;
    }

    @Override protected Void doInBackground(Database.Attempt... params) {
      saveAttempt(mDb, params[0], mUpdateAttemptCount);
      return null;
    }
  }

  void gameShowing(boolean showing) {
    boolean ratingShowing = mRatingFrame.getVisibility() == View.VISIBLE;
    if ((mResumed = showing) && !ratingShowing) {
      if (mGame != null && mState != Grid.State.SOLVED) mGame.resume();
      new CheckNextAttempt(this).execute(mAttempt == null ? null : mAttempt._id);
    } else {
      saveAttemptFromUiThread();
      cancelStatus();
    }
  }

  @Override public void onResume() {
    super.onResume();
    gameShowing(true);
    showFirstTimeDialog();
  }

  private void cancelStatus() {
    if (mToast != null) {
      mToast.cancel();
      mToast = null;
    }
  }

  @Override public void onPrepareOptionsMenu(Menu menu) {
    boolean going = mGame != null && mState != Grid.State.SOLVED;
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      switch (item.getItemId()) {
        case R.id.menu_undo:
        case R.id.menu_undo_to_start:
          item.setEnabled(going && mUndoStack.canUndo());
          break;

        case R.id.menu_redo:
        case R.id.menu_redo_to_end:
          item.setEnabled(going && mUndoStack.canRedo());
          break;

        case R.id.menu_new_trail:
        case R.id.menu_show_solution:
          item.setEnabled(going);
          break;
      }
    }
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    gameShowing(true);
    switch (item.getItemId()) {
      case R.id.menu_show_solution:
        showSolution();
        return true;

      case R.id.menu_undo:
        try {
          mUndoStack.undo();
          stateChanged();
        } catch (CommandException e) {
          showError(e.getMessage());
        }
        return true;

      case R.id.menu_undo_to_start:
        try {
          while (mUndoStack.canUndo())
            mUndoStack.undo();
          stateChanged();
        } catch (CommandException e) {
          showError(e.getMessage());
        }
        return true;

      case R.id.menu_redo:
        try {
          mUndoStack.redo();
          stateChanged();
        } catch (CommandException e) {
          showError(e.getMessage());
        }
        return true;

      case R.id.menu_redo_to_end:
        try {
          while (mUndoStack.canRedo())
            mUndoStack.redo();
          stateChanged();
        } catch (CommandException e) {
          showError(e.getMessage());
        }
        return true;

      case R.id.menu_new_trail:
        boolean recycled = false;
        int trailId = mTrailAdapter.getCount();
        for (int i = 0; i < trailId; ++i) {
          TrailItem trailItem = mTrailAdapter.getItem(i);
          if (trailItem.trail.getTrailhead() == null) {
            recycled = true;
            makeActiveTrailItem(trailItem);
            break;
          }
        }
        if (!recycled) {
          makeActiveTrailItem(makeTrailItem(trailId, true, false));
        }
        stateChanged();
        return true;

      default:
        return false;
    }
  }

  @Override public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mSudokuView = (SudokuView) view.findViewById(R.id.sudoku_view);
    mRatingFrame = view.findViewById(R.id.rating_frame);
    mProgress = (ProgressBar) view.findViewById(R.id.progress);
    mEditTrailToggle = (ToggleButton) view.findViewById(R.id.edit_trail_toggle);
    mTrailsList = (ListView) view.findViewById(R.id.trails);
    mTimer = (TextView) view.findViewById(R.id.timer);
    mRating = (TextView) view.findViewById(R.id.rating);

    mEditTrailToggle.setOnCheckedChangeListener(this);
    mEditTrailToggle.setEnabled(false);

    mTrailAdapter = new TrailAdapter(this);
    mTrailsList.setAdapter(mTrailAdapter);
    mTrailsList.setEnabled(true);
    mTrailsList.setOnItemClickListener(this);
    mTrailsList.setOnItemLongClickListener(this);

    mSudokuView.setKeepScreenOn(true);
    mSudokuView.setOnMoveListener(this);
    mRegistry.addListener(new Sudoku.Adapter() {
      @Override public void moveMade(Sudoku game, Move move) {
        if (game == mGame) {
          mSudokuView.invalidateLocation(move.getLocation());
          if (move.trailId >= 0) {
            makeActiveTrail(game.getTrail(move.trailId));
          }
          boolean wasBroken = mState == Grid.State.BROKEN;
          updateState();
          if (mState == Grid.State.SOLVED) {
            makeToast(getString(R.string.text_congrats)).show();
            transitionToInfoPage();
          }
          else if (mState == Grid.State.BROKEN && !wasBroken) {
            showStatus(getString(R.string.text_oops));
          }
        }
      }

      @Override public void gameResumed(Sudoku game) {
        if (game == mGame) {
          mTimer.post(timerUpdater);
        }
      }
    });
  }

  // Private methods

  private void transitionToInfoPage() {
    mPrefs.removeCurrentAttemptIdAsync();
    saveAttemptFromUiThread();
    NetworkService.saveAttempt(getActivity(), mAttempt);
    Intent intent = new Intent(getActivity(), PuzzleListActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    intent.putExtra(Extras.PUZZLE_ID, mAttempt.puzzleId);
    intent.putExtra(Extras.SHOW_INFO, true);
    if (mAttempt.attemptState == AttemptState.GAVE_UP) {
      intent.putExtra(Extras.SHOW_SOLUTION, mAttempt._id);
    }
    startActivity(intent);
    getActivity().finish();
  }

  private void makeActiveTrail(Sudoku.Trail trail) {
    for (int i = 0; i < mTrailAdapter.getCount(); ++i) {
      TrailItem item = mTrailAdapter.getItem(i);
      if (item.trail == trail) {
        makeActiveTrailItem(item);
        return;
      }
    }
  }

  private TrailItem makeTrailItem(int trailId, boolean shown, boolean off) {
    // This is very scientific:
    double hueBase = 0.6 + trailId * 0.1573;
    double valueBase = (0.57 + hueBase) * Math.PI * 256;
    hueBase = hueBase - Math.floor(hueBase);
    valueBase = valueBase - Math.floor(valueBase);
    float hue = (float) (hueBase * 360);
    float value = (float) (valueBase * 0.5 + 0.4);
    int color = Color.HSVToColor(new float[] { hue, 0.9f, value });
    int dimColor = Color.HSVToColor(new float[] { hue, 0.4f, value });
    TrailItem trailItem = new TrailItem(mGame.getTrail(trailId), color, dimColor, shown, off);
    return trailItem;
  }

  private void makeActiveTrailItem(TrailItem item) {
    mTrailAdapter.remove(item);
    item.shown = true;
    item.off = false;
    mTrailAdapter.insert(item, 0);
    mTrailsList.smoothScrollToPosition(0);
    fixVisibleItems();
    mEditTrailToggle.setChecked(true);
  }

  private void fixVisibleItems() {
    List<TrailItem> vis = Lists.newArrayList(), invis = Lists.newArrayList(),
        off = Lists.newArrayList();
    for (int i = 0; i < mTrailAdapter.getCount(); ++i) {
      TrailItem item = mTrailAdapter.getItem(i);
      (item.shown ? vis : item.off ? off : invis).add(item);
    }
    if (vis.size() > MAX_VISIBLE_TRAILS)
      for (TrailItem item : vis.subList(MAX_VISIBLE_TRAILS, vis.size())) {
        item.shown = false;
      }
    invis.addAll(off);
    updateTrails(vis, invis);
  }

  /**
   * @param vis  trails visible in the grid
   * @param invis  trails in the list but not shown
   */
  private void updateTrails(List<TrailItem> vis, List<TrailItem> invis) {
    mTrailAdapter.clear();
    for (TrailItem item : Iterables.concat(vis, invis))
      mTrailAdapter.add(item);
    if (vis.size() > MAX_VISIBLE_TRAILS)
      vis.subList(MAX_VISIBLE_TRAILS, vis.size()).clear();
    mSudokuView.setTrails(vis);
    if (vis.isEmpty() && mEditTrailToggle.isChecked())
      mEditTrailToggle.setChecked(false);
    mEditTrailToggle.setEnabled(!vis.isEmpty());
    stateChanged();
  }

  private void stateChanged() {
    getActivity().invalidateOptionsMenu();
  }

  private static class UiState {
    UndoStack undo;
    int defaultChoice;
    int[] trailOrder;
    int numVisibleTrails;
    int numOffTrails;
    boolean trailActive;
  }

  private UiState makeUiState() {
    UiState object = new UiState();
    object.undo = mUndoStack;
    object.defaultChoice = number(mSudokuView.getDefaultChoice());
    object.trailOrder = makeTrailOrder();
    object.numVisibleTrails = countVisibleTrails();
    object.numOffTrails = countOffTrails();
    object.trailActive = mEditTrailToggle.isChecked();
    return object;
  }

  private int[] makeTrailOrder() {
    int[] array = new int[mTrailAdapter.getCount()];
    for (int i = 0; i < array.length; ++i)
      array[i] = (int) mTrailAdapter.getItemId(i);
    return array;
  }

  private int countVisibleTrails() {
    int count = 0;
    for (int i = 0; i < mTrailAdapter.getCount(); ++i) {
      if (mTrailAdapter.getItem(i).shown) ++count;
    }
    return count;
  }

  private int countOffTrails() {
    int count = 0;
    for (int i = 0; i < mTrailAdapter.getCount(); ++i) {
      if (mTrailAdapter.getItem(i).off) ++count;
    }
    return count;
  }

  private void restoreTrails(int[] trailOrder, int numVisibleTrails, int numOffTrails) {
    List<TrailItem> vis = Lists.newArrayList(), invis = Lists.newArrayList();
    int offIndex = trailOrder.length - numOffTrails;
    for (int i = 0; i < trailOrder.length; ++i) {
      TrailItem item = makeTrailItem(trailOrder[i], i < numVisibleTrails, i >= offIndex);
      (item.shown ? vis : invis).add(item);
    }
    updateTrails(vis, invis);
  }

  private void updateState() {
    if (mGame.isFull()) {
      Collection<Location> broken = mGame.getState().getGrid().getBrokenLocations();
      mSudokuView.setBrokenLocations(broken);
      if (broken.isEmpty()) {
        mState = Grid.State.SOLVED;
        mSudokuView.setEditable(false);
        mGame.suspend();
        mAttempt.attemptState = AttemptState.FINISHED;
        stateChanged();
      } else {
        mState = Grid.State.BROKEN;
      }
    } else if (mState != Grid.State.INCOMPLETE) {
      mState = Grid.State.INCOMPLETE;
      mSudokuView.setBrokenLocations(Collections.<Location>emptySet());
    }
  }
}
