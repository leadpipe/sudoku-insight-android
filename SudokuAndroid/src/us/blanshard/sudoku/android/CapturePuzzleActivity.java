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

import us.blanshard.sudoku.android.SudokuView.OnMoveListener;
import us.blanshard.sudoku.android.WorkerFragment.Independence;
import us.blanshard.sudoku.android.WorkerFragment.Priority;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.game.Sudoku.State;
import us.blanshard.sudoku.gen.Generator;

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

import com.google.gson.JsonObject;

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
  private JsonObject mPuzzleProperties;
  private Long mPuzzleId;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.capture);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    mSudokuView = (SudokuView) findViewById(R.id.sudoku_view);
    mCaptureSource = (AutoCompleteTextView) findViewById(R.id.capture_source);
    mPlay = (Button) findViewById(R.id.capture_play);
    mSave = (Button) findViewById(R.id.capture_save);
    mNotice = (TextView) findViewById(R.id.notice);

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
    if (mPuzzleProperties != null && (v == mPlay || v == mSave)) {
      final Save save = new Save(this, v == mPlay);
      if (ImproperDialog.isNeeded(mPrefs, mPuzzleProperties)) {
        new ImproperDialog() {
          @Override protected void okayed() {
            save.execute();
          }
        }.show(getFragmentManager());
      } else {
        save.execute();
      }
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

  @Override protected void onResume() {
    super.onResume();
    updateState();
  }

  private Grid getPuzzle() {
    return mSudokuView.getGame().getState().getGrid();
  }

  private void updateState() {
    Solver.Result result = Solver.solve(getPuzzle(), Prefs.MAX_SOLUTIONS);
    boolean isPuzzle = result.intersection != null;
    mPlay.setEnabled(isPuzzle);
    mSave.setEnabled(isPuzzle);
    if (isPuzzle) {
      mPuzzleProperties = Generator.makePuzzleProperties(result);
      new CheckExisting(this).execute();
    } else {
      mPuzzleProperties = null;
    }
  }

  private void showNotice(int stringId) {
    mNotice.setText(stringId);
    mNotice.setVisibility(View.VISIBLE);
    mCaptureSource.dismissDropDown();
    mCaptureSource.setVisibility(View.GONE);
  }

  private void hideNotice() {
    mNotice.setVisibility(View.GONE);
    mCaptureSource.setVisibility(View.VISIBLE);
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
      if (puzzleId == null) activity.hideNotice();
      else activity.showNotice(R.string.text_already_have_puzzle);
    }
  }

  private static class Save extends WorkerFragment.ActivityTask<CapturePuzzleActivity, Void, Void, Long> {
    private final Database mDb;
    private final Long mPuzzleId;
    private final JsonObject mPuzzleProperties;
    private final String mSource;
    private final boolean mPlay;

    Save(CapturePuzzleActivity activity, boolean play) {
      super(activity, Priority.FOREGROUND, Independence.DEPENDENT);
      this.mDb = activity.mDb;
      this.mPuzzleId = activity.mPuzzleId;
      this.mPuzzleProperties = activity.mPuzzleProperties;
      String source = activity.mCaptureSource.getText().toString();
      this.mSource = source.trim().isEmpty() ? null : source;
      this.mPlay = play;
    }

    @Override protected Long doInBackground(Void... params) {
      if (mPuzzleId == null) {
        long puzzleId = mDb.addCapturedPuzzle(mPuzzleProperties, mSource);
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
