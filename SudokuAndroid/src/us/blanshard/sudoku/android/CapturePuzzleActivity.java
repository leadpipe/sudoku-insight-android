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

import us.blanshard.sudoku.android.SudokuView.OnMoveListener;
import us.blanshard.sudoku.android.WorkerFragment.Independence;
import us.blanshard.sudoku.android.WorkerFragment.Priority;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.game.Sudoku.State;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

/**
 * Captures a puzzle from Goggles.
 *
 * @author Luke Blanshard
 */
public class CapturePuzzleActivity extends ActivityBase implements OnMoveListener, View.OnClickListener {
  private SudokuView mSudokuView;
  private AutoCompleteTextView mCaptureSource;
  private Button mPlay;
  private Button mSave;
  private TextView mNotice;
  private boolean mIsPuzzle;
  private Long mPuzzleId;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.capture);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    mSudokuView = (SudokuView) findViewById(R.id.sudoku_view);
    mCaptureSource = (AutoCompleteTextView) findViewById(R.id.capture_source);
    mPlay = (Button) findViewById(R.id.capture_play);
    mSave = (Button) findViewById(R.id.capture_save);
    mNotice = (TextView) findViewById(R.id.already_have_notice);

    mSudokuView.setOnMoveListener(this);
    mPlay.setOnClickListener(this);
    mSave.setOnClickListener(this);
    Grid grid = Grid.BLANK;
    boolean editable = true;

    if (getIntent().getData() != null) {
      try {
        grid = Grid.fromString(getIntent().getData().getQueryParameter("original"));
        editable = false;
      } catch (Exception e) {
        Log.e("SudokuInsight", "Bad goggles uri", e);
        finish();
        return;
      }
    }
    mSudokuView.setPuzzleEditor(grid);
    mSudokuView.setDefaultChoice(Numeral.of(1));
    mSudokuView.setEditable(editable);
    updateState();
    new FetchAutocompletes(this).execute();
  }

  @Override protected String getHelpPage() {
    return "capture";
  }

  @Override public void onClick(View v) {
    if (mIsPuzzle && (v == mPlay || v == mSave)) {
      new Save(this, v == mPlay).execute();
    }
  }

  @Override public void onMove(State state, Location loc, Numeral num) {
    state.set(loc, num);
    mSudokuView.invalidateLocation(loc);
    updateState();
  }

  @Override public boolean onPrepareOptionsMenu(Menu menu) {
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      switch (item.getItemId()) {
        case R.id.menu_capture_puzzle:
          item.setVisible(false);
          break;
      }
    }
    return super.onPrepareOptionsMenu(menu);
  }

  private Grid getPuzzle() {
    return mSudokuView.getGame().getState().getGrid();
  }

  private void updateState() {
    Solver.Result result = Solver.solve(getPuzzle());
    mIsPuzzle = result.solution != null;
    mPlay.setEnabled(mIsPuzzle);
    mSave.setEnabled(mIsPuzzle);
    if (mIsPuzzle)
      new CheckExisting(this).execute();
  }

  private static class FetchAutocompletes extends WorkerFragment.ActivityTask<CapturePuzzleActivity, Void, Void, List<String>> {
    private final Database mDb;

    FetchAutocompletes(CapturePuzzleActivity activity) {
      super(activity);
      mDb = activity.mDb;
    }

    @Override protected List<String> doInBackground(Void... params) {
      return mDb.getPuzzleSources();
    }

    @Override protected void onPostExecute(CapturePuzzleActivity activity, List<String> sources) {
      if (!sources.isEmpty()) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
            android.R.layout.simple_dropdown_item_1line, sources);
        activity.mCaptureSource.setAdapter(adapter);
        if (activity.mPuzzleId == null) {
          activity.mCaptureSource.showDropDown();
        }
      }
    }
  }

  private static class CheckExisting extends WorkerFragment.ActivityTask<CapturePuzzleActivity, Void, Void, Long> {
    private final Database mDb;
    private final Grid mPuzzle;

    CheckExisting(CapturePuzzleActivity activity) {
      super(activity);
      mDb = activity.mDb;
      mPuzzle = activity.getPuzzle();
    }

    @Override protected Long doInBackground(Void... params) {
      return mDb.lookUpPuzzleId(mPuzzle);
    }

    @Override protected void onPostExecute(CapturePuzzleActivity activity, Long puzzleId) {
      activity.mPuzzleId = puzzleId;
      activity.mNotice.setVisibility(puzzleId == null ? View.GONE : View.VISIBLE);
      activity.mCaptureSource.setVisibility(puzzleId == null ? View.VISIBLE : View.GONE);
      if (puzzleId != null) {
        activity.mCaptureSource.dismissDropDown();
      }
    }
  }

  private static class Save extends WorkerFragment.ActivityTask<CapturePuzzleActivity, Void, Void, Long> {
    private final Database mDb;
    private final Long mPuzzleId;
    private final Grid mPuzzle;
    private final String mSource;
    private final boolean mPlay;

    Save(CapturePuzzleActivity activity, boolean play) {
      super(activity, Priority.FOREGROUND, Independence.DEPENDENT);
      this.mDb = activity.mDb;
      this.mPuzzleId = activity.mPuzzleId;
      this.mPuzzle = activity.getPuzzle();
      String source = activity.mCaptureSource.getText().toString();
      this.mSource = source.isEmpty() ? null : source;
      this.mPlay = play;
    }

    @Override protected Long doInBackground(Void... params) {
      if (mPuzzleId == null) {
        long puzzleId = mDb.addCapturedPuzzle(mPuzzle, mSource);
        return mDb.getCurrentAttemptForPuzzle(puzzleId)._id;
      } else {
        return mDb.getOpenAttemptForPuzzle(mPuzzleId)._id;
      }
    }

    @Override protected void onPostExecute(CapturePuzzleActivity activity, Long attemptId) {
      activity.finish();
      if (mPlay) {
        Intent intent = new Intent(activity, SudokuActivity.class);
        intent.putExtra(Extras.ATTEMPT_ID, attemptId);
        activity.startActivity(intent);
      }
    }
  }
}
