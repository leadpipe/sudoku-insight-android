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

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import java.util.List;

import javax.inject.Inject;

/**
 * Captures a puzzle from Goggles.
 *
 * @author Luke Blanshard
 */
public class CapturePuzzleActivity extends ActionBarActivity {
  @InjectView(R.id.capture_source) AutoCompleteTextView mCaptureSource;
  @Inject Database mDb;
  private Grid mGrid;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.capture);

    try {
      mGrid = Grid.fromString(getIntent().getData().getQueryParameter("original"));
      new FetchAutocompletes().execute();
    } catch (Exception e) {
      Log.e("SudokuInsight", "Bad goggles uri", e);
      finish();
    }
  }

  public void play(View playButton) {
    new Save(true).execute();
  }

  public void save(View saveButton) {
    new Save(false).execute();
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
      long puzzleId = mDb.addCapturedPuzzle(mGrid, mSource);
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
