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

import roboguice.inject.InjectView;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.game.Sudoku.State;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import java.util.List;

import javax.inject.Inject;

/**
 * Captures a puzzle from Goggles.
 *
 * @author Luke Blanshard
 */
public class CapturePuzzleActivity extends ActionBarActivity implements SudokuView.OnMoveListener, View.OnClickListener {
  @InjectView(R.id.sudoku_view) SudokuView mSudokuView;
  @InjectView(R.id.capture_source) AutoCompleteTextView mCaptureSource;
  @InjectView(R.id.capture_play) Button mPlay;
  @InjectView(R.id.capture_save) Button mSave;
  @Inject Database mDb;
  private boolean mIsPuzzle;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.capture);
    mSudokuView.setOnMoveListener(this);
    Grid grid;

    if (getIntent().getData() == null) {
      grid = Grid.BLANK;
    } else {
      try {
        grid = Grid.fromString(getIntent().getData().getQueryParameter("original"));
      } catch (Exception e) {
        Log.e("SudokuInsight", "Bad goggles uri", e);
        finish();
        return;
      }
    }
    mSudokuView.setPuzzleEditor(grid);
    mSudokuView.setDefaultChoice(Numeral.of(1));
    updateState();
    new FetchAutocompletes().execute();
  }

  @Override public void onDestroy() {
    super.onDestroy();
    mDb.close();
  }

  @Override public void onClick(View v) {
    if (mIsPuzzle && (v == mPlay || v == mSave)) {
      new Save(v == mPlay).execute();
    }
  }

  @Override public void onMove(State state, Location loc, Numeral num) {
    state.set(loc, num);
    mSudokuView.invalidateLocation(loc);
    updateState();
  }

  private Grid getPuzzle() {
    return mSudokuView.getGame().getState().getGrid();
  }

  private void updateState() {
    Solver.Result result = Solver.solve(getPuzzle());
    mIsPuzzle = result.solution != null;
    mPlay.setEnabled(mIsPuzzle);
    mSave.setEnabled(mIsPuzzle);
  }

  private class FetchAutocompletes extends AsyncTask<Void, Void, List<String>> {
    @Override protected List<String> doInBackground(Void... params) {
      return mDb.getElementSources();
    }

    @Override protected void onPostExecute(List<String> sources) {
      if (!sources.isEmpty()) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(CapturePuzzleActivity.this,
            android.R.layout.simple_dropdown_item_1line, sources);
        mCaptureSource.setAdapter(adapter);
        // TODO(leadpipe): make the dropdown text not white on white in gingerbread
        mCaptureSource.showDropDown();
      }
    }
  }

  private class Save extends AsyncTask<Void, Void, Long> {
    private final String mSource;
    private final boolean mPlay;

    Save(boolean play) {
      String source = mCaptureSource.getText().toString();
      this.mSource = source.isEmpty() ? null : source;
      this.mPlay = play;
    }

    @Override protected Long doInBackground(Void... params) {
      long puzzleId = mDb.addCapturedPuzzle(getPuzzle(), mSource);
      return mDb.getCurrentGame(puzzleId)._id;
    }

    @Override protected void onPostExecute(Long gameId) {
      finish();
      if (mPlay) {
        Intent intent = new Intent(CapturePuzzleActivity.this, SudokuActivity.class);
        intent.putExtra("gameId", gameId);
        startActivity(intent);
      }
    }
  }
}
