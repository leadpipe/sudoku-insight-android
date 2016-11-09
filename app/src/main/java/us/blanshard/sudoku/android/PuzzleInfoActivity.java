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

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

/**
 * @author Luke Blanshard
 */
public class PuzzleInfoActivity extends ActivityBase implements PuzzleInfoFragment.ActivityCallback {

  private PuzzleInfoFragment mFragment;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.info_activity);
    mFragment = (PuzzleInfoFragment) getFragmentManager().findFragmentById(R.id.info_fragment);
    getActionBar().setDisplayHomeAsUpEnabled(true);
  }

  @Override protected String getHelpPage() {
    return "info";
  }

  @Override protected void onNewIntent(Intent intent) {
    setIntent(intent);
  }

  @Override protected void onResume() {
    super.onResume();
    long puzzleId = getPuzzleId();
    mFragment.setPuzzleId(puzzleId);
    setTitle(getString(R.string.text_info_title, puzzleId));
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
      case R.id.menu_list_puzzles:
        Intent upIntent = new Intent(this, PuzzleListActivity.class);
        upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        upIntent.putExtra(Extras.PUZZLE_ID, getPuzzleId());
        startActivity(upIntent);
        finish();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override public void showCollection(long collectionId) {
    Intent intent = new Intent(this, PuzzleListActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    intent.putExtra(Extras.COLLECTION_ID, collectionId);
    intent.putExtra(Extras.PUZZLE_ID, getPuzzleId());
    startActivity(intent);
  }

  @Override public void voted() {
    // Nothing required in the standalone info activity.
  }

  private long getPuzzleId() {
    return getIntent().getExtras().getLong(Extras.PUZZLE_ID);
  }
}
