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

import static us.blanshard.sudoku.core.Numeral.number;
import static us.blanshard.sudoku.core.Numeral.numeral;

import roboguice.fragment.RoboFragment;
import roboguice.inject.ContextSingleton;
import roboguice.inject.InjectView;

import us.blanshard.sudoku.android.Database.GameState;
import us.blanshard.sudoku.core.Generator;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Symmetry;
import us.blanshard.sudoku.game.Command;
import us.blanshard.sudoku.game.CommandException;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.MoveCommand;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.Sudoku.State;
import us.blanshard.sudoku.game.UndoStack;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.InsightSum;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;

/**
 * The fragment for playing a sudoku.
 *
 * @author Luke Blanshard
 */
@ContextSingleton
public class SudokuFragment
    extends RoboFragment
    implements SudokuView.OnMoveListener, OnCheckedChangeListener, OnItemClickListener,
               OnItemLongClickListener, View.OnClickListener {
  private static final int MAX_VISIBLE_TRAILS = 4;

  private UndoStack mUndoStack = new UndoStack();
  private Database.Game mDbGame;
  private Sudoku mGame;
  private boolean mResumed;
  private final InsightWell mInsightWell = new InsightWell(this);
  Analyzer mAnalyzer;
  private InsightSum mInsightSum;
  private boolean mShowInsights = false;
  private boolean mHasNext = false;
  private HintLevel mHintLevel = HintLevel.NONE;
  private Grid.State mState;
  @Inject Sudoku.Registry mRegistry;
  @Inject ActionBarHelper mActionBarHelper;
  private TrailAdapter mTrailAdapter;
  @InjectView(R.id.sudoku_view) SudokuView mSudokuView;
  @InjectView(R.id.progress) ProgressBar mProgress;
  @InjectView(R.id.edit_trail_toggle) ToggleButton mEditTrailToggle;
  @InjectView(R.id.trails) ListView mTrailsList;
  @InjectView(R.id.timer) TextView mTimer;
  @InjectView(R.id.insights) TextView mInsights;
  @Inject Database mDb;
  @Inject SharedPreferences mPrefs;

  enum HintLevel {
    NONE(Database.GameState.FINISHED),
    SUMMARY(Database.GameState.FINISHED_WITH_HINT),
    DETAILS(Database.GameState.FINISHED_WITH_EXPLICIT_HINT);

    final Database.GameState finishedGameState;

    private HintLevel(Database.GameState finishedGameState) {
      this.finishedGameState = finishedGameState;
    }
  }

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

  public long getGameId() {
    if (mDbGame == null) return 0;
    return mDbGame._id;
  }

  private void setDbGame(Database.Game dbGame) {
    mDbGame = dbGame;
    if (dbGame.gameState == GameState.UNSTARTED) {
      Database.startUnstartedGame(dbGame);
    }
    new CheckNextGame().execute();
    mShowInsights = mPrefs.getBoolean("showInsights", true);
    try {
      Sudoku game = new Sudoku(
          dbGame.puzzle, mRegistry, GameJson.toHistory(dbGame.history), dbGame.elapsedMillis);
      setGame(game);
      if (dbGame.uiState != null) {
        JSONObject uiState = new JSONObject(dbGame.uiState);
        mUndoStack = GameJson.toUndoStack(uiState.getJSONObject("undo"), mGame);
        mSudokuView.setDefaultChoice(numeral(uiState.getInt("defaultChoice")));
        restoreTrails(uiState.getJSONArray("trailOrder"), uiState.getInt("numVisibleTrails"));
        mEditTrailToggle.setChecked(uiState.getBoolean("trailActive"));
        mHintLevel = HintLevel.values()[uiState.optInt("hint")];
        if (mShowInsights != (mHintLevel != HintLevel.NONE))
          mShowInsights = !mShowInsights;
      }
      if (dbGame.gameState.isInPlay()) {
        SharedPreferences.Editor prefs = mPrefs.edit();
        prefs.putLong("gameId", dbGame._id);
        prefs.apply();
      }
    } catch (JSONException e) {
      Log.e("SudokuFragment", "Unable to restore state from puzzle #" + dbGame.puzzleId, e);
      setGame(null);
    }
    if (dbGame.elements != null && !dbGame.elements.isEmpty()) {
      showStatus(Joiner.on(getActivity().getString(R.string.text_collection_separator)).join(
          Iterables.transform(dbGame.elements, new Function<Database.Element, String>() {
              @Override public String apply(Database.Element element) {
                String coll = ToText.collectionName(getActivity(), element);
                if (element.createTime == 0) return coll;
                CharSequence date = ToText.relativeDateTime(getActivity(), element.createTime);
                return getActivity().getString(R.string.text_collection_date, coll, date);
              }
          })));
    }
  }

  private void setGame(Sudoku game) {
    mGame = game;
    mAnalyzer = game == null ? null : new Analyzer(game, mInsightWell);
    mHintLevel = HintLevel.NONE;
    mState = Grid.State.INCOMPLETE;
    mUndoStack = new UndoStack();
    mSudokuView.setGame(game);
    List<TrailItem> vis = Lists.newArrayList(), invis = Lists.newArrayList();
    if (game != null)
      for (int i = game.getNumTrails() - 1; i >= 0; --i)
        invis.add(makeTrailItem(i, false));
    updateTrails(vis, invis);
    mSudokuView.setDefaultChoice(Numeral.of(1));
    stateChanged();
    if (game != null) {
      updateState();
      if (mState != Grid.State.SOLVED && mResumed)
        game.resume();
    }
    mProgress.setVisibility(View.GONE);
  }

  public void setInsights(InsightSum insights) {
    mInsightSum = insights;
    mInsights.setText(insights == null ? "" : insights.getSummary());
  }

  public void showError(String s) {
    showStatus(s);
  }

  public void showStatus(String s) {
    Toast.makeText(getActivity(), s, Toast.LENGTH_LONG).show();
  }

  public void generatePuzzle() {
    cancelCurrentPuzzle(new FindOrMakePuzzle(false));
  }

  public void nextPuzzle() {
    cancelCurrentPuzzle(new FindOrMakePuzzle(true));
  }

  private void cancelCurrentPuzzle(final FindOrMakePuzzle replacementAction) {
    if (mGame == null || mState == Grid.State.SOLVED) {
      doReplacePuzzle(replacementAction);
      return;
    }
    DialogFragment dialog = new DialogFragment() {
      @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
            .setMessage(R.string.dialog_cancel_message)
            .setPositiveButton(R.string.button_give_up, new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                  dialog.dismiss();
                  mDbGame.gameState = GameState.GAVE_UP;
                  doReplacePuzzle(replacementAction);
                }
            })
            .setNegativeButton(R.string.button_save, new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                  dialog.dismiss();
                  doReplacePuzzle(replacementAction);
                }
            })
            .create();
      }
    };
    dialog.show(getFragmentManager(), "cancelPuzzle");
  }

  private void doReplacePuzzle(FindOrMakePuzzle replacementAction) {
    saveGameFromUiThread();
    mSudokuView.setGame(null);
    mProgress.setVisibility(View.VISIBLE);
    replacementAction.execute();
  }

  public void trailCheckChanged(TrailItem item, boolean isChecked) {
    if (isChecked) {
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

  // Listener implementations

  @Override public void onMove(State state, Location loc, Numeral num) {
    doCommand(new MoveCommand(state, loc, num));
  }

  @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    if (buttonView == mEditTrailToggle) {
      mSudokuView.setTrailActive(isChecked);
      stateChanged();
    }
  }

  @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    makeActiveTrailItem(mTrailAdapter.getItem(position));
  }

  @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
    // We don't actually do anything on long click, except swallow it so it
    // doesn't get treated as a regular click.
    return true;
  }

  @Override public void onClick(View v) {
    if (v == mInsights && mInsightSum != null) {
      mHintLevel = HintLevel.DETAILS;
      InsightsFragment.newInstance(mInsightSum.toString()).show(getFragmentManager(), "insights");
    }
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
    Long gameId = null;
    if (getActivity().getIntent().hasExtra("gameId")) {
      gameId = getActivity().getIntent().getExtras().getLong("gameId");
    } else if (savedInstanceState != null && savedInstanceState.containsKey("gameId")) {
      gameId = savedInstanceState.getLong("gameId");
    } else if (mPrefs.contains("gameId")) {
      gameId = mPrefs.getLong("gameId", -1);
    }
    if (gameId == null || gameId == -1) new FindOrMakePuzzle().execute();
    else new FetchGame().execute(gameId);
    mProgress.setVisibility(View.VISIBLE);
  }

  @Override public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    saveGameFromUiThread();
    if (mDbGame != null) outState.putLong("gameId", mDbGame._id);
  }

  private boolean updateDbGame() {
    if (mGame == null) return false;
    mGame.suspend();
    mDbGame.history = GameJson.fromHistory(mGame.getHistory()).toString();
    mDbGame.elapsedMillis = mGame.elapsedMillis();
    if (mDbGame.gameState.isInPlay()) {
      try {
        mDbGame.uiState = makeUiState().toString();
      } catch (JSONException e) {
        Log.e("SudokuFragment", "Unable to save UI state", e);
      }
    } else {
      mDbGame.uiState = null;
    }
    return true;
  }

  private void saveGameFromUiThread() {
    if (updateDbGame()) {
      ThreadPolicy policy = StrictMode.allowThreadDiskWrites();
      try {
        mDb.updateGame(mDbGame);
      } finally {
        StrictMode.setThreadPolicy(policy);
      }
    }
  }

  private class FetchGame extends AsyncTask<Long, Void, Database.Game> {
    @Override protected Database.Game doInBackground(Long... params) {
      return mDb.getGame(params[0]);
    }

    @Override protected void onPostExecute(Database.Game dbGame) {
      setDbGame(dbGame);
    }
  }

  private class FindOrMakePuzzle extends AsyncTask<Void, Void, Database.Game> {
    private final boolean mFindBeforeMaking;

    FindOrMakePuzzle() {
      this(true);
    }

    FindOrMakePuzzle(boolean findBeforeMaking) {
      mFindBeforeMaking = findBeforeMaking;
    }

    @Override protected Database.Game doInBackground(Void... params) {
      Database.Game answer = mFindBeforeMaking ? mDb.getFirstOpenGame() : null;
      if (answer == null) {
        Random random = new Random();
        Generator gen = Generator.SUBTRACTIVE_RANDOM;
        Symmetry sym = Symmetry.choosePleasing(random);
        long seed = random.nextLong();
        random = new Random(seed);
        Grid puzzle = gen.generate(random, sym);
        String genParams = String.format("%s:%s:%s", gen, sym, seed);
        long id = mDb.addGeneratedPuzzle(puzzle, genParams);
        answer = mDb.getCurrentGameForPuzzle(id);
      }
      return answer;
    }

    @Override protected void onPostExecute(Database.Game dbGame) {
      setDbGame(dbGame);
    }
  }

  private class CheckNextGame extends AsyncTask<Void, Void, Boolean> {
    @Override protected Boolean doInBackground(Void... params) {
      return mDb.getNumOpenGames() > 1;
    }

    @Override protected void onPostExecute(Boolean hasNext) {
      mHasNext = hasNext;
      mActionBarHelper.invalidateOptionsMenu();
    }
  }

  private class SaveGame extends AsyncTask<Database.Game, Void, Void> {
    @Override protected Void doInBackground(Database.Game... params) {
      mDb.updateGame(params[0]);
      return null;
    }
  }

  @Override public void onPause() {
    super.onPause();
    mResumed = false;
    saveGameFromUiThread();
  }

  @Override public void onResume() {
    super.onResume();
    mResumed = true;
    if (mGame != null && mState != Grid.State.SOLVED) mGame.resume();
    mHasNext = false;
  }

  @Override public void onPrepareOptionsMenu(Menu menu) {
    boolean going = mState != Grid.State.SOLVED;
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      switch (item.getItemId()) {
        case R.id.menu_show_insights:
        case R.id.menu_hide_insights:
          item.setVisible(mShowInsights != (item.getItemId() == R.id.menu_show_insights));
          break;

        case R.id.menu_next_puzzle:
          item.setEnabled(mHasNext);
          break;

        case R.id.menu_undo:
          item.setEnabled(going && mUndoStack.canUndo());
          break;

        case R.id.menu_redo:
          item.setEnabled(going && mUndoStack.canRedo());
          break;

        case R.id.menu_new_trail:
          item.setEnabled(going && mGame != null);
          break;
      }
    }
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_show_insights:
      case R.id.menu_hide_insights: {
        mShowInsights = item.getItemId() == R.id.menu_show_insights;
        if (!mShowInsights && mHintLevel != HintLevel.DETAILS
            && (mGame == null || mGame.getHistory().isEmpty())) {
          mHintLevel = HintLevel.NONE;
        }
        SharedPreferences.Editor prefs = mPrefs.edit();
        prefs.putBoolean("showInsights", mShowInsights);
        prefs.apply();
        stateChanged();
        return true;
      }
      case R.id.menu_next_puzzle:
        nextPuzzle();
        return true;

      case R.id.menu_generate_puzzle:
        generatePuzzle();
        return true;

      case R.id.menu_undo:
        try {
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
          makeActiveTrailItem(makeTrailItem(trailId, true));
        }
        stateChanged();
        return true;

      default:
        return false;
    }
  }

  @Override public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mEditTrailToggle.setOnCheckedChangeListener(this);
    mEditTrailToggle.setEnabled(false);

    mTrailAdapter = new TrailAdapter(this);
    mTrailsList.setAdapter(mTrailAdapter);
    mTrailsList.setEnabled(true);
    mTrailsList.setOnItemClickListener(this);
    mTrailsList.setOnItemLongClickListener(this);

    mInsights.setOnClickListener(this);

    mSudokuView.setOnMoveListener(this);
    mRegistry.addListener(new Sudoku.Adapter() {
      @Override public void moveMade(Sudoku game, Move move) {
        if (game == mGame) {
          mSudokuView.invalidateLocation(move.getLocation());
          if (move.id >= 0) {
            makeActiveTrail(game.getTrail(move.id));
          }
          updateState();
          if (mState == Grid.State.SOLVED) showStatus(getActivity().getString(R.string.text_congrats));
          else if (mState == Grid.State.BROKEN) showStatus(getActivity().getString(R.string.text_oops));
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

  private void makeActiveTrail(Sudoku.Trail trail) {
    for (int i = 0; i < mTrailAdapter.getCount(); ++i) {
      TrailItem item = mTrailAdapter.getItem(i);
      if (item.trail == trail) {
        makeActiveTrailItem(item);
        return;
      }
    }
  }

  private TrailItem makeTrailItem(int trailId, boolean shown) {
    double hue = (trailId + 1) * 5.0 / 17;
    hue = hue - Math.floor(hue);
    int color = Color.HSVToColor(new float[] { (float) hue * 360, 1f, 0.625f });
    TrailItem trailItem = new TrailItem(mGame.getTrail(trailId), color, shown);
    return trailItem;
  }

  private void makeActiveTrailItem(TrailItem item) {
    mTrailAdapter.remove(item);
    item.shown = true;
    mTrailAdapter.insert(item, 0);
    mTrailsList.smoothScrollToPosition(0);
    fixVisibleItems();
    mEditTrailToggle.setChecked(true);
  }

  private void fixVisibleItems() {
    List<TrailItem> vis = Lists.newArrayList(), invis = Lists.newArrayList();
    for (int i = 0; i < mTrailAdapter.getCount(); ++i) {
      TrailItem item = mTrailAdapter.getItem(i);
      (item.shown ? vis : invis).add(item);
    }
    if (vis.size() > MAX_VISIBLE_TRAILS)
      for (TrailItem item : vis.subList(MAX_VISIBLE_TRAILS, vis.size())) {
        item.shown = false;
      }
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
    mActionBarHelper.invalidateOptionsMenu();
    setInsights(null);
    if (mShowInsights && mState != Grid.State.SOLVED) {
      if (mHintLevel == HintLevel.NONE) mHintLevel = HintLevel.SUMMARY;
      mInsightWell.refill();
      mInsights.setTextColor(mSudokuView.getInputColor());
    }
  }

  private JSONObject makeUiState() throws JSONException {
    JSONObject object = new JSONObject();
    object.put("undo", GameJson.fromUndoStack(mUndoStack));
    object.put("defaultChoice", number(mSudokuView.getDefaultChoice()));
    object.put("trailOrder", makeTrailOrder());
    object.put("numVisibleTrails", countVisibleTrails());
    object.put("trailActive", mEditTrailToggle.isChecked());
    object.put("hint", mHintLevel.ordinal());
    return object;
  }

  private JSONArray makeTrailOrder() {
    JSONArray array = new JSONArray();
    for (int i = 0; i < mTrailAdapter.getCount(); ++i) {
      array.put(mTrailAdapter.getItemId(i));
    }
    return array;
  }

  private int countVisibleTrails() {
    int count = 0;
    for (int i = 0; i < mTrailAdapter.getCount(); ++i) {
      if (mTrailAdapter.getItem(i).shown) ++count;
    }
    return count;
  }

  private void restoreTrails(JSONArray trailOrder, int numVisibleTrails) throws JSONException {
    List<TrailItem> vis = Lists.newArrayList(), invis = Lists.newArrayList();
    for (int i = 0; i < trailOrder.length(); ++i) {
      TrailItem item = makeTrailItem(trailOrder.getInt(i), i < numVisibleTrails);
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
        mDbGame.gameState = mHintLevel.finishedGameState;
        updateDbGame();
        new SaveGame().execute(mDbGame);
        SharedPreferences.Editor prefs = mPrefs.edit();
        prefs.remove("gameId");
        prefs.apply();
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
