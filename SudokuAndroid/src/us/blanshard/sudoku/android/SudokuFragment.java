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

import roboguice.fragment.RoboFragment;
import roboguice.inject.ContextSingleton;
import roboguice.inject.InjectView;

import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.game.Command;
import us.blanshard.sudoku.game.CommandException;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.MoveCommand;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.Sudoku.State;
import us.blanshard.sudoku.game.UndoStack;

import android.graphics.Color;
import android.os.Bundle;
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
    mSudokuView.setGame(game);
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
          double hue = (trailId + 1) * 5.0 / 17;
          hue = hue - Math.floor(hue);
          int color = Color.HSVToColor(new float[] { (float) hue * 360, 1f, 0.5f });
          TrailItem trailItem = new TrailItem(mGame.getTrail(trailId), color, true);
          makeActiveTrailItem(trailItem);
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
        if (move.getState(game) instanceof Sudoku.Trail) {
          makeActiveTrail((Sudoku.Trail) move.getState(game));
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
}
