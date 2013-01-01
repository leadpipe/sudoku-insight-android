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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

/**
 * @author Luke Blanshard
 */
public abstract class ActivityBase extends Activity {
  protected Database mDb;
  protected Prefs mPrefs;
  protected String mInstallationId;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    mDb = Database.instance(this);
    mPrefs = Prefs.instance(this);
    mInstallationId = Installation.id(this);
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.common, menu);
    return true;
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home: {
        Intent upIntent = new Intent(this, SudokuActivity.class);
        upIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(upIntent);
        return true;
      }
      case R.id.menu_list_puzzles: {
        Intent intent = new Intent(this, PuzzleListActivity.class);
        Long id = getCurrentPuzzleId();
        if (id != null)
          intent.putExtra(Extras.PUZZLE_ID, id);
        startActivity(intent);
        return true;
      }
      case R.id.menu_capture_puzzle: {
        Intent intent = new Intent(this, CapturePuzzleActivity.class);
        startActivity(intent);
        return true;
      }
      case R.id.menu_settings: {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        return true;
      }
      case R.id.menu_help: {
        Intent intent = new Intent(this, HelpActivity.class);
        intent.putExtra(Extras.HELP_PAGE, getHelpPage());
        startActivity(intent);
        return true;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  protected Long getCurrentPuzzleId() {
    return null;
  }

  abstract protected String getHelpPage();
}
