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
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
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
    OnCheckedChangeListener, OnClickListener, OnItemClickListener, OnItemLongClickListener {
  private static final int MAX_VISIBLE_TRAILS = 4;

  private UndoStack mUndoStack = new UndoStack();
  private Sudoku mGame;
  @Inject Sudoku.Registry mRegistry;
  private TrailAdapter mTrailAdapter;
  @InjectView(R.id.sudokuView) SudokuView mSudokuView;
  @InjectView(R.id.undo) Button mUndoButton;
  @InjectView(R.id.redo) Button mRedoButton;
  @InjectView(R.id.newTrail) Button mNewTrailButton;
  @InjectView(R.id.solutionTrailToggle) ToggleButton mSolutionTrailToggle;
  @InjectView(R.id.trails) ListView mTrailsList;

  // Public methods

  public void doCommand(Command command) {
    try {
      mUndoStack.doCommand(command);
      updateUndoButtonStates();
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
    mNewTrailButton.setEnabled(game != null);
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
    if (buttonView == mSolutionTrailToggle) {
      mSudokuView.setTrailActive(isChecked);
    }
  }

  @Override public void onClick(View v) {
    switch (v.getId()) {
      case R.id.undo:
        try {
          mUndoStack.undo();
          updateUndoButtonStates();
        } catch (CommandException e) {
          showError(e.getMessage());
        }
        break;
      case R.id.redo:
        try {
          mUndoStack.redo();
          updateUndoButtonStates();
        } catch (CommandException e) {
          showError(e.getMessage());
        }
        break;
      case R.id.newTrail: {
        boolean recycled = false;
        int trailId = mTrailAdapter.getCount();
        for (int i = 0; i < trailId; ++i) {
          TrailItem item = mTrailAdapter.getItem(i);
          if (item.trail.getTrailhead() == null) {
            recycled = true;
            makeActiveTrailItem(item);
            break;
          }
        }
        if (!recycled) {
          double hue = (trailId + 1) * 5.0 / 17;
          hue = hue - Math.floor(hue);
          int color = Color.HSVToColor(new float[] { (float) hue * 360, 1f, 0.5f });
          TrailItem item = new TrailItem(mGame.getTrail(trailId), color, true);
          makeActiveTrailItem(item);
        }
        break;
      }
      default:
        showError("Unrecognized click view " + v);
        break;
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
    return inflater.inflate(R.layout.board, container, true);
  }

  @Override public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mUndoButton.setOnClickListener(this);
    mRedoButton.setOnClickListener(this);
    updateUndoButtonStates();

    mNewTrailButton.setOnClickListener(this);
    mNewTrailButton.setEnabled(false);
    mSolutionTrailToggle.setOnCheckedChangeListener(this);
    mSolutionTrailToggle.setEnabled(false);

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

  private void updateUndoButtonStates() {
    mUndoButton.setEnabled(mUndoStack.canUndo());
    mRedoButton.setEnabled(mUndoStack.canRedo());
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

  private void makeActiveTrailItem(TrailItem item) {
    mTrailAdapter.remove(item);
    item.shown = true;
    mTrailAdapter.insert(item, 0);
    mTrailsList.smoothScrollToPosition(0);
    fixVisibleItems();
    mSolutionTrailToggle.setChecked(true);
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
    if (vis.isEmpty() && mSolutionTrailToggle.isChecked())
      mSolutionTrailToggle.setChecked(false);
    mSolutionTrailToggle.setEnabled(!vis.isEmpty());
  }
}
