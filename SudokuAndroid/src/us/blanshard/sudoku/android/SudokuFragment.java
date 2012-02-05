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

import android.graphics.Color;
import android.os.Bundle;
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
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import javax.inject.Inject;

/**
 * The fragment for playing a sudoku.
 *
 * @author Luke Blanshard
 */
@ContextSingleton
public class SudokuFragment extends RoboFragment implements SudokuView.OnMoveListener,
    OnCheckedChangeListener, OnItemClickListener, OnItemLongClickListener {
  private static final int MAX_VISIBLE_TRAILS = 4;

  private UndoStack mUndoStack = new UndoStack();
  private Sudoku mGame;
  @Inject Sudoku.Registry mRegistry;
  @Inject ActionBarHelper mActionBarHelper;
  private TrailAdapter mTrailAdapter;
  @InjectView(R.id.sudoku_view) SudokuView mSudokuView;
  @InjectView(R.id.edit_trail_toggle) ToggleButton mEditTrailToggle;
  @InjectView(R.id.trails) ListView mTrailsList;

  // Public methods

  public void doCommand(Command command) {
    try {
      mUndoStack.doCommand(command);
      invalidateOptionsMenu();
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

  public void setGame(Sudoku game) {
    mGame = game;
    mUndoStack = new UndoStack();
    mSudokuView.setGame(game);
    updateTrails(Lists.<TrailItem>newArrayList(), Lists.<TrailItem>newArrayList());
    mSudokuView.setDefaultChoice(Numeral.of(1));
    invalidateOptionsMenu();
  }

  public void showError(String s) {
    Toast.makeText(getActivity(), s, Toast.LENGTH_LONG).show();
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
    try {
      if (savedInstanceState != null && savedInstanceState.containsKey("puzzle")) {
        Grid puzzle = Grid.fromString(savedInstanceState.getString("puzzle"));
        List<Move> history = GameJson.toHistory(new JSONArray(savedInstanceState.getString("history")));
        long elapsedMillis = savedInstanceState.getLong("elapsedMillis");
        setGame(new Sudoku(puzzle, mRegistry, history, elapsedMillis));
        if (savedInstanceState.containsKey("uiState")) {
          JSONObject uiState = new JSONObject(savedInstanceState.getString("uiState"));
          mUndoStack = GameJson.toUndoStack(uiState.getJSONObject("undo"), mGame);
          mSudokuView.setDefaultChoice(numeral(uiState.getInt("defaultChoice")));
          restoreTrails(uiState.getJSONArray("trailOrder"), uiState.getInt("numVisibleTrails"));
          mEditTrailToggle.setChecked(uiState.getBoolean("trailActive"));
        }
      }
    } catch (JSONException e) {
      Log.e("SudokuFragment", "Unable to restore state from " + savedInstanceState, e);
    }
  }

  @Override public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (mGame != null) {
      outState.putString("puzzle", mGame.getPuzzle().toFlatString());
      outState.putString("history", GameJson.fromHistory(mGame.getHistory()).toString());
      outState.putLong("elapsedMillis", mGame.elapsedMillis());
      try {
        outState.putString("uiState", makeUiState().toString());
      } catch (JSONException e) {
        Log.e("SudokuFragment", "Unable to save UI state", e);
      }
    }
  }

  @Override public void onPrepareOptionsMenu(Menu menu) {
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      switch (item.getItemId()) {
        case R.id.menu_undo:
          item.setEnabled(mUndoStack.canUndo());
          break;

        case R.id.menu_redo:
          item.setEnabled(mUndoStack.canRedo());
          break;

        case R.id.menu_new_trail:
          item.setEnabled(mGame != null);
          break;
      }
    }
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_undo:
        try {
          mUndoStack.undo();
          invalidateOptionsMenu();
        } catch (CommandException e) {
          showError(e.getMessage());
        }
        return true;

      case R.id.menu_redo:
        try {
          mUndoStack.redo();
          invalidateOptionsMenu();
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
          makeActiveTrailItem(makeTrailItem(trailId));
        }
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

    mSudokuView.setOnMoveListener(this);
    mRegistry.addListener(new Sudoku.Adapter() {
      @Override public void moveMade(Sudoku game, Move move) {
        mSudokuView.invalidateLocation(move.getLocation());
        if (move.id >= 0) {
          makeActiveTrail(game.getTrail(move.id));
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

  private TrailItem makeTrailItem(int trailId) {
    double hue = (trailId + 1) * 5.0 / 17;
    hue = hue - Math.floor(hue);
    int color = Color.HSVToColor(new float[] { (float) hue * 360, 1f, 0.5f });
    TrailItem trailItem = new TrailItem(mGame.getTrail(trailId), color, true);
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
  }

  private void invalidateOptionsMenu() {
    mActionBarHelper.invalidateOptionsMenu();
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
      TrailItem item = makeTrailItem(trailOrder.getInt(i));
      if (i < numVisibleTrails) {
        vis.add(item);
      } else {
        invis.add(item);
        item.shown = false;
      }
    }
    updateTrails(vis, invis);
  }
}
