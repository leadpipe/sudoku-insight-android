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

import android.os.Bundle;
import android.os.StrictMode;
import android.view.MenuItem;
import android.view.WindowManager;

public class SudokuActivity extends ActivityBase {
  private SudokuFragment mBoardFragment;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getActionBar().setDisplayHomeAsUpEnabled(false);
    setContentView(R.layout.main);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setTitle(R.string.app_short_name);
    mBoardFragment = (SudokuFragment) getFragmentManager().findFragmentById(R.id.board_fragment);

    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .penaltyDialog()
          .build());
      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
          .detectLeakedClosableObjects()
          .detectLeakedSqlLiteObjects()
          .penaltyLog()
          .penaltyDropBox()
          .build());
    }
  }

  @Override protected String getHelpPage() {
    return "sudoku";
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        return false;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override protected Long getCurrentPuzzleId() {
    return mBoardFragment.getPuzzleId();
  }

  @Override public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    mBoardFragment.gameShowing(hasFocus);
  }
}
