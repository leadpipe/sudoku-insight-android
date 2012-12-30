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

import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

public class SudokuActivity extends ActivityBase {
  private static final boolean STRICT = true;

  private SudokuFragment mBoardFragment;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setTitle(R.string.app_short_name);
    mBoardFragment = (SudokuFragment) getFragmentManager().findFragmentById(R.id.board_fragment);

    if (STRICT) {
      StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .penaltyDialog()
          .build());
      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
          .detectAll()
          .penaltyLog()
          //.penaltyDeath()
          .build());
    }
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.list_item, menu);
    return true;
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_list_puzzles: {
        Intent intent = new Intent(this, PuzzleListActivity.class);
        intent.putExtra(Extras.PUZZLE_ID, mBoardFragment.getPuzzleId());
        startActivity(intent);
        return true;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    mBoardFragment.gameShowing(hasFocus);
  }
}
