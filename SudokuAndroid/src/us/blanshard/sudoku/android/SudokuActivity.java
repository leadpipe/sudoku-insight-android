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

import roboguice.inject.InjectFragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.Menu;
import android.view.MenuItem;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class SudokuActivity extends ActionBarActivity {
  private static final boolean STRICT = true;

  @InjectFragment(R.id.board_fragment) SudokuFragment mBoardFragment;
  @InjectFragment(R.id.list_fragment) @Nullable PuzzleListFragment mListFragment;
  @Inject Database mDb;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);  // Can't use @ContentView, it does things too early.

    if (STRICT) {
      StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .penaltyDialog()
          .build());
      StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
          .detectAll()
          .penaltyLog()
          .penaltyDeath()
          .build());
    }
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.main, menu);
    if (mListFragment != null) {
      menu.findItem(R.id.menu_list_puzzles).setVisible(false);
    }
    return true;
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_list_puzzles: {
        Intent intent = new Intent(this, PuzzleListActivity.class);
        intent.putExtra("gameId", mBoardFragment.getGameId());
        startActivity(intent);
        return true;
      }
      case R.id.menu_capture_puzzle: {
        Intent intent = new Intent(this, CapturePuzzleActivity.class);
        startActivity(intent);
        return true;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override public void onDestroy() {
    super.onDestroy();
    mDb.close();
  }
}
