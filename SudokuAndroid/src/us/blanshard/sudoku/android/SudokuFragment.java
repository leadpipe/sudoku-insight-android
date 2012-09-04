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

import static us.blanshard.sudoku.android.SudokuView.MAX_VISIBLE_TRAILS;
import static us.blanshard.sudoku.core.Numeral.number;
import static us.blanshard.sudoku.core.Numeral.numeral;

import us.blanshard.sudoku.android.Database.GameState;
import us.blanshard.sudoku.android.SudokuView.OnMoveListener;
import us.blanshard.sudoku.android.WorkerFragment.Independence;
import us.blanshard.sudoku.android.WorkerFragment.Priority;
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
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

  private static int sUpdateGameCount;

  private UndoStack mUndoStack = new UndoStack();
  private Database.Game mDbGame;
  private Sudoku mGame;
  private boolean mResumed;
  private Grid.State mState;
  private final Sudoku.Registry mRegistry = Sudoku.newRegistry();
  private TrailAdapter mTrailAdapter;
  private SudokuView mSudokuView;
  private ProgressBar mProgress;
  private ToggleButton mEditTrailToggle;
  private ListView mTrailsList;
  private TextView mTimer;
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

  private final Runnable gameSaver = new Runnable() {
    @Override public void run() {
      if (updateDbGame(false) && mGame.isRunning()) {
        new SaveGame(SudokuFragment.this).execute(mDbGame.clone());
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
    if (mDbGame == null) return 0;
    return mDbGame.puzzleId;
  }

  private void setDbGame(Database.Game dbGame) {
    mDbGame = dbGame;
    if (dbGame.gameState == GameState.UNSTARTED) {
      Database.startUnstartedGame(dbGame);
    }
    new CheckNextGame(this).execute(dbGame._id);
    try {
      Sudoku game = new Sudoku(
          dbGame.puzzle, mRegistry, GameJson.toHistory(dbGame.history), dbGame.elapsedMillis);
      getActivity().setTitle(getString(R.string.text_puzzle_number, dbGame.puzzleId));
      setGame(game);
      if (dbGame.uiState != null) {
        JSONObject uiState = new JSONObject(dbGame.uiState);
        mUndoStack = GameJson.toUndoStack(uiState.getJSONObject("undo"), mGame);
        mSudokuView.setDefaultChoice(numeral(uiState.getInt("defaultChoice")));
        restoreTrails(uiState.getJSONArray("trailOrder"), uiState.getInt("numVisibleTrails"));
        mEditTrailToggle.setChecked(uiState.getBoolean("trailActive"));
      }
      if (dbGame.gameState.isInPlay()) {
        mPrefs.setCurrentGameIdAsync(dbGame._id);
      }
    } catch (JSONException e) {
      Log.e("SudokuFragment", "Unable to restore state from puzzle #" + dbGame.puzzleId, e);
      setGame(null);
    }
    if (dbGame.elements != null && !dbGame.elements.isEmpty()) {
      String colls = Joiner.on(getString(R.string.text_collection_separator)).join(
          Iterables.transform(dbGame.elements, new Function<Database.Element, String>() {
              @Override public String apply(Database.Element element) {
                return ToText.collectionNameAndTimeText(getActivity(), element);
              }
          }));
      showStatus(colls);
    }
  }

  private void setGame(Sudoku game) {
    mGame = game;
    mState = Grid.State.INCOMPLETE;
    mUndoStack = new UndoStack();
    mSudokuView.setGame(game);
    List<TrailItem> vis = Lists.newArrayList(), invis = Lists.newArrayList();
    if (game != null)
      for (int i = game.getNumTrails() - 1; i >= 0; --i)
        invis.add(makeTrailItem(i, false));
    updateTrails(vis, invis);
    mSudokuView.setDefaultChoice(Numeral.of(1));
    if (game != null) {
      updateState();
      if (mState != Grid.State.SOLVED && mResumed)
        game.resume();
    }
    stateChanged();
    mProgress.setVisibility(View.GONE);
    mSudokuView.postDelayed(gameSaver, DB_UPDATE_MILLIS);
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

  public void giveUp() {
    DialogFragment dialog = new DialogFragment() {
      @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
            .setMessage(R.string.dialog_give_up_message)
            .setPositiveButton(R.string.button_give_up, new OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                  dialog.dismiss();
                  mDbGame.gameState = GameState.GAVE_UP;
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
      item.uninteresting = false;
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
    item.uninteresting = true;
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
    long gameId = 0;
    if (getActivity().getIntent().hasExtra(Extras.GAME_ID)) {
      gameId = getActivity().getIntent().getExtras().getLong(Extras.GAME_ID);
    } else if (savedInstanceState != null && savedInstanceState.containsKey(Extras.GAME_ID)) {
      gameId = savedInstanceState.getLong(Extras.GAME_ID);
    } else if (mPrefs.hasCurrentGameId()) {
      gameId = mPrefs.getCurrentGameId();
    }
    new FetchFindOrMakePuzzle(this).execute(gameId);
    mProgress.setVisibility(View.VISIBLE);
  }

  @Override public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    saveGameFromUiThread();
    if (mDbGame != null) outState.putLong(Extras.GAME_ID, mDbGame._id);
  }

  private boolean updateDbGame(boolean suspend) {
    ++sUpdateGameCount;
    if (mGame == null) return false;
    if (suspend) mGame.suspend();
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
    if (updateDbGame(true)) {
      ThreadPolicy policy = StrictMode.allowThreadDiskWrites();
      try {
        saveGame(mDb, mDbGame, sUpdateGameCount);
      } finally {
        StrictMode.setThreadPolicy(policy);
      }
    }
  }

  private static synchronized void saveGame(Database db, Database.Game game, int count) {
    if (count == sUpdateGameCount)
      db.updateGame(game);
  }

  private static Database.Game generateAndStorePuzzle(Database db, Prefs prefs) {
    Database.Game answer;
    Random random = new Random();
    Generator gen = prefs.getGenerator();
    Symmetry sym = prefs.chooseSymmetry(random);
    long seed = random.nextLong();
    random = new Random(seed);
    Grid puzzle = gen.generate(random, sym);
    String genParams = String.format("%s:%s:%s", gen, sym, seed);
    long id = db.addGeneratedPuzzle(puzzle, genParams);
    answer = db.getCurrentGameForPuzzle(id);
    return answer;
  }

  private static class FetchFindOrMakePuzzle extends WorkerFragment.Task<SudokuFragment, Long, Void, Database.Game> {
    private final Database mDb;
    private final Prefs mPrefs;

    FetchFindOrMakePuzzle(SudokuFragment fragment) {
      super(fragment, Priority.FOREGROUND, Independence.DEPENDENT);
      mDb = fragment.mDb;
      mPrefs = fragment.mPrefs;
    }

    @Override protected Database.Game doInBackground(Long... params) {
      Database.Game answer = mDb.getGame(params[0]);
      if (answer == null || !answer.gameState.isInPlay())
        answer = mDb.getFirstOpenGame();
      if (answer == null)
        answer = generateAndStorePuzzle(mDb, mPrefs);
      return answer;
    }

    @Override protected void onPostExecute(SudokuFragment fragment, Database.Game dbGame) {
      fragment.setDbGame(dbGame);
    }
  }

  private static class CheckNextGame extends WorkerFragment.Task<SudokuFragment, Long, Void, Void> {
    private final Database mDb;
    private final Prefs mPrefs;

    CheckNextGame(SudokuFragment fragment) {
      super(fragment);
      mDb = fragment.mDb;
      mPrefs = fragment.mPrefs;
    }

    @Override protected Void doInBackground(Long... params) {
      int numOpenGames = mDb.getNumOpenGames();
      boolean hasNext = numOpenGames > 1
          || numOpenGames == 1 && (params[0] == null || mDb.getFirstOpenGame()._id != params[0]);
      if (!hasNext)
        generateAndStorePuzzle(mDb, mPrefs);
      return null;
    }
  }

  private static class SaveGame extends WorkerFragment.Task<SudokuFragment, Database.Game, Void, Void> {
    private final Database mDb;
    private final int mUpdateGameCount;

    SaveGame(SudokuFragment fragment) {
      super(fragment, Priority.BACKGROUND, Independence.FREE);
      mDb = fragment.mDb;
      mUpdateGameCount = sUpdateGameCount;
    }

    @Override protected Void doInBackground(Database.Game... params) {
      saveGame(mDb, params[0], mUpdateGameCount);
      return null;
    }
  }

  @Override public void onPause() {
    super.onPause();
    mResumed = false;
    saveGameFromUiThread();
    cancelStatus();
  }

  private void cancelStatus() {
    if (mToast != null) {
      mToast.cancel();
      mToast = null;
    }
  }

  @Override public void onResume() {
    super.onResume();
    mResumed = true;
    if (mGame != null && mState != Grid.State.SOLVED) mGame.resume();
    new CheckNextGame(this).execute(mDbGame == null ? null : mDbGame._id);
  }

  @Override public void onPrepareOptionsMenu(Menu menu) {
    boolean going = mState != Grid.State.SOLVED;
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
          item.setEnabled(going && mGame != null);
          break;
      }
    }
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_give_up:
        giveUp();
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

    mSudokuView = (SudokuView) view.findViewById(R.id.sudoku_view);
    mProgress = (ProgressBar) view.findViewById(R.id.progress);
    mEditTrailToggle = (ToggleButton) view.findViewById(R.id.edit_trail_toggle);
    mTrailsList = (ListView) view.findViewById(R.id.trails);
    mTimer = (TextView) view.findViewById(R.id.timer);

    mEditTrailToggle.setOnCheckedChangeListener(this);
    mEditTrailToggle.setEnabled(false);

    mTrailAdapter = new TrailAdapter(this);
    mTrailsList.setAdapter(mTrailAdapter);
    mTrailsList.setEnabled(true);
    mTrailsList.setOnItemClickListener(this);
    mTrailsList.setOnItemLongClickListener(this);

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
    mPrefs.removeCurrentGameIdAsync();
    saveGameFromUiThread();
    Intent intent = new Intent(getActivity(), PuzzleInfoActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NO_HISTORY);
    intent.putExtra(Extras.PUZZLE_ID, mDbGame.puzzleId);
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

  private TrailItem makeTrailItem(int trailId, boolean shown) {
    // This is very scientific:
    double hueBase = 0.6 + trailId * 0.1573;
    double valueBase = (0.57 + hueBase) * Math.PI * 256;
    hueBase = hueBase - Math.floor(hueBase);
    valueBase = valueBase - Math.floor(valueBase);
    float hue = (float) (hueBase * 360);
    float value = (float) (valueBase * 0.5 + 0.4);
    int color = Color.HSVToColor(new float[] { hue, 0.9f, value });
    int dimColor = Color.HSVToColor(new float[] { hue, 0.4f, value });
    TrailItem trailItem = new TrailItem(mGame.getTrail(trailId), color, dimColor, shown);
    return trailItem;
  }

  private void makeActiveTrailItem(TrailItem item) {
    mTrailAdapter.remove(item);
    item.shown = true;
    item.uninteresting = false;
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
      (item.shown ? vis : item.uninteresting ? off : invis).add(item);
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

  private JSONObject makeUiState() throws JSONException {
    JSONObject object = new JSONObject();
    object.put("undo", GameJson.fromUndoStack(mUndoStack));
    object.put("defaultChoice", number(mSudokuView.getDefaultChoice()));
    object.put("trailOrder", makeTrailOrder());
    object.put("numVisibleTrails", countVisibleTrails());
    object.put("trailActive", mEditTrailToggle.isChecked());
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
        mDbGame.gameState = GameState.FINISHED;
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
