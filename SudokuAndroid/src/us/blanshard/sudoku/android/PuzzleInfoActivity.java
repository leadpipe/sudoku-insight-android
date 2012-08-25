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

import us.blanshard.sudoku.android.actionbarcompat.ActionBarActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * @author Luke Blanshard
 */
public class PuzzleInfoActivity extends ActionBarActivity {

  private Database mDb;
  private PuzzleInfoFragment mFragment;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mDb = new Database(this);
    setContentView(R.layout.info_activity);
    mFragment = (PuzzleInfoFragment) (getSupportFragmentManager().findFragmentById(R.id.info_fragment));
    getActionBarHelper().setDisplayHomeAsUpEnabled(true);
    mFragment.initFragment(mDb, getActionBarHelper());
  }

  @Override protected void onDestroy() {
    mDb.close();
    super.onDestroy();
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.common, menu);
    return true;
  }

  @Override protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    mFragment.setPuzzleId(getIntent().getExtras().getLong(Extras.PUZZLE_ID));
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        Intent upIntent = new Intent(this, PuzzleListActivity.class);
        upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        upIntent.putExtra(Extras.PUZZLE_ID, getIntent().getExtras().getLong(Extras.PUZZLE_ID));
        startActivity(upIntent);
        finish();
        return true;
      case R.id.menu_capture_puzzle: {
        Intent intent = new Intent(this, CapturePuzzleActivity.class);
        startActivity(intent);
        return true;
      }
      case R.id.menu_prefs: {
        Intent intent = new Intent(this, PrefsActivity.class);
        startActivity(intent);
        return true;
      }
    }
    return super.onOptionsItemSelected(item);
  }
}
