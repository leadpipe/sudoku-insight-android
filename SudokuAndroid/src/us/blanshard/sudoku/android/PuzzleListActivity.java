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
import us.blanshard.sudoku.android.actionbarcompat.ActionBarHelper.OnNavigationListener;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import javax.annotation.Nullable;

/**
 * @author Luke Blanshard
 */
public class PuzzleListActivity extends ActionBarActivity
    implements OnNavigationListener, AdapterView.OnItemClickListener {
  //private static final String TAG = "PuzzleListActivity";
  private Database mDb;
  private Prefs mPrefs;
  private PuzzleListFragment mListFragment;
  private @Nullable PuzzleInfoFragment mInfoFragment;
  private CollectionAdapter mCollectionAdapter;
  private long mCollectionId = Database.ALL_PSEUDO_COLLECTION_ID;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mDb = new Database(this);
    mPrefs = new Prefs(this);
    new FetchCollections(this).execute();
    setContentView(R.layout.list_activity);
    mListFragment = (PuzzleListFragment) getSupportFragmentManager().findFragmentById(R.id.list_fragment);
    mListFragment.initFragment(mDb, getActionBarHelper(), mPrefs);
    mInfoFragment = (PuzzleInfoFragment) getSupportFragmentManager().findFragmentById(R.id.info_fragment);
    if (mInfoFragment != null) mInfoFragment.initFragment(mDb, getActionBarHelper());
    mCollectionAdapter = new CollectionAdapter();
    getActionBarHelper().setDisplayHomeAsUpEnabled(true);
    getActionBarHelper().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
    getActionBarHelper().setListNavigationCallbacks(mCollectionAdapter, this);
    mListFragment.setOnItemClickListener(this);
    if (getIntent().hasExtra(Extras.PUZZLE_ID)) {
      long puzzleId = getIntent().getExtras().getLong(Extras.PUZZLE_ID);
      mListFragment.setPuzzleId(puzzleId);
      if (mInfoFragment != null) mInfoFragment.setPuzzleId(puzzleId);
    }
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

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        Intent upIntent = new Intent(this, SudokuActivity.class);
        upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
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

  @Override public boolean onNavigationItemSelected(int itemPosition, long itemId) {
    if (mListFragment == null)
      return false;
    mCollectionId = itemId;
    mListFragment.setCollectionId(itemId);
    return true;
  }

  @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    if (mInfoFragment == null) {
      Intent intent = new Intent(this, PuzzleInfoActivity.class);
      intent.putExtra(Extras.PUZZLE_ID, id);
      startActivity(intent);
    } else {
      mInfoFragment.setPuzzleId(id);
    }
  }

  public void setCollectionId(long id) {
    int position = getActionBarHelper().getSelectedNavigationIndex();
    for (int i = 0, count = mCollectionAdapter.getCount(); i < count; ++i)
      if (mCollectionAdapter.getItem(i)._id == id) {
        position = i;
        break;
      }
    getActionBarHelper().setSelectedNavigationItem(position);
  }

  private void setCollections(List<Database.CollectionInfo> collections) {
    Database.CollectionInfo all = new Database.CollectionInfo();
    int position = 0, foundPosition = 0;
    all._id = Database.ALL_PSEUDO_COLLECTION_ID;
    all.name = getString(R.string.text_all_puzzles);
    mCollectionAdapter.clear();
    mCollectionAdapter.add(all);
    for (Database.CollectionInfo coll : collections) {
      ++position;
      if (coll._id == mCollectionId) foundPosition = position;
      mCollectionAdapter.add(coll);
    }
    getActionBarHelper().setSelectedNavigationItem(foundPosition);
  }

  private static class FetchCollections
      extends WorkerFragment.ActivityTask<PuzzleListActivity, Void, Void, List<Database.CollectionInfo>> {
    private final Database mDb;

    FetchCollections(PuzzleListActivity activity) {
      super(activity);
      this.mDb = activity.mDb;
    }

    @Override protected List<Database.CollectionInfo> doInBackground(Void... params) {
      return mDb.getAllCollections();
    }

    @Override protected void onPostExecute(
        PuzzleListActivity activity, List<Database.CollectionInfo> collections) {
      activity.setCollections(collections);
    }
  }

  private class CollectionAdapter extends ArrayAdapter<Database.CollectionInfo> {

    public CollectionAdapter() {
      super(PuzzleListActivity.this, 0);
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null)
        view = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
      TextView text = (TextView) view.findViewById(android.R.id.text1);
      text.setText(getItem(position).name);
      return view;
    }

    @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
      return getView(position, convertView, parent);
    }

    @Override public long getItemId(int position) {
      return getItem(position)._id;
    }

    @Override public boolean hasStableIds() {
      return true;
    }
  }
}
