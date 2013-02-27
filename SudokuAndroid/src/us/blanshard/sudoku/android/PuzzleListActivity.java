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

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
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
public class PuzzleListActivity extends ActivityBase
    implements OnNavigationListener, AdapterView.OnItemClickListener, PuzzleInfoFragment.ActivityCallback {
  //private static final String TAG = "PuzzleListActivity";
  private PuzzleListFragment mListFragment;
  private @Nullable PuzzleInfoFragment mInfoFragment;
  private CollectionAdapter mCollectionAdapter;
  private long mCollectionId = Database.ALL_PSEUDO_COLLECTION_ID;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.list_activity);
    mListFragment = (PuzzleListFragment) getFragmentManager().findFragmentById(R.id.list_fragment);
    mInfoFragment = (PuzzleInfoFragment) getFragmentManager().findFragmentById(R.id.info_fragment);

    Bundle extras = getIntent().getExtras();
    if (mInfoFragment == null && extras != null && extras.getBoolean(Extras.SHOW_INFO, false)) {
      Intent infoIntent = new Intent(this, PuzzleInfoActivity.class);
      infoIntent.putExtra(Extras.PUZZLE_ID, extras.getLong(Extras.PUZZLE_ID));
      infoIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(infoIntent);
      finish();
      return;
    }

    new FetchCollections(this).execute();
    mCollectionAdapter = new CollectionAdapter();
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
    getActionBar().setListNavigationCallbacks(mCollectionAdapter, this);
    mListFragment.setOnItemClickListener(this);
  }

  @Override protected String getHelpPage() {
    return mInfoFragment == null ? "list" : "info";
  }

  @Override protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    applyExtras();
  }

  @Override protected void onNewIntent(Intent intent) {
    setIntent(intent);
    applyExtras();
  }

  @Override public boolean onPrepareOptionsMenu(Menu menu) {
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      switch (item.getItemId()) {
        case R.id.menu_list_puzzles:
          item.setVisible(false);
          break;
      }
    }
    return super.onPrepareOptionsMenu(menu);
  }

  private void applyExtras() {
    if (getIntent().hasExtra(Extras.COLLECTION_ID)) {
      long collectionId = getIntent().getExtras().getLong(Extras.COLLECTION_ID);
      setCollectionId(collectionId);
    }
    if (getIntent().hasExtra(Extras.PUZZLE_ID)) {
      long puzzleId = getIntent().getExtras().getLong(Extras.PUZZLE_ID);
      mListFragment.setPuzzleId(puzzleId);
      if (mInfoFragment != null) mInfoFragment.setPuzzleId(puzzleId);
    }
  }

  @Override public boolean onNavigationItemSelected(int itemPosition, long itemId) {
    if (mListFragment == null)
      return false;
    mCollectionId = itemId;
    mListFragment.setCollectionId(itemId);
    return true;
  }

  @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    mListFragment.setPuzzleId(id);
    if (mInfoFragment == null) {
      Intent intent = new Intent(this, PuzzleInfoActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      intent.putExtra(Extras.PUZZLE_ID, id);
      startActivity(intent);
    } else {
      mInfoFragment.setPuzzleId(id);
    }
  }

  @Override public void showCollection(long collectionId) {
    setCollectionId(collectionId);
  }

  @Override public void voted() {
    mListFragment.reloadPuzzles();
  }

  public void setCollectionId(long id) {
    int position = getActionBar().getSelectedNavigationIndex();
    for (int i = 0, count = mCollectionAdapter.getCount(); i < count; ++i)
      if (mCollectionAdapter.getItem(i)._id == id) {
        position = i;
        break;
      }
    getActionBar().setSelectedNavigationItem(position);
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
    getActionBar().setSelectedNavigationItem(foundPosition);
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
