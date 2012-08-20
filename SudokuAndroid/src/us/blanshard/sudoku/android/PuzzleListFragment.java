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

import roboguice.fragment.RoboFragment;
import roboguice.inject.InjectView;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.util.List;

import javax.inject.Inject;

/**
 * Shows a list of puzzles from the database.
 *
 * @author Luke Blanshard
 */
public class PuzzleListFragment extends RoboFragment {
  private static final String TAG = "PuzzleListFragment";
  @InjectView(R.id.puzzles) ListView mList;
  @Inject Database mDb;
  @Inject PuzzleListActivity mActivity;
  private PuzzleAdapter mPuzzleAdapter;
  private List<Database.Puzzle> mPuzzles;
  private long mCollectionId = Database.ALL_PSEUDO_COLLECTION_ID;
  private long mPuzzleId = 0;

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    setHasOptionsMenu(false);
    return inflater.inflate(R.layout.list, container, true);
  }

  @Override public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mPuzzleAdapter = new PuzzleAdapter(this);
    mList.setAdapter(mPuzzleAdapter);
    mList.setEnabled(true);
    Log.d(TAG, "Choice mode: " + mList.getChoiceMode());
  }

  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    new FetchPuzzles(this).execute();
  }

  public void setCollectionId(long collectionId) {
    mCollectionId = collectionId;
    updateList();
  }

  public void setPuzzleId(long puzzleId) {
    mPuzzleId = puzzleId;
    updateSelectedPuzzle();
  }

  private void setPuzzles(List<Database.Puzzle> puzzles) {
    mPuzzles = puzzles;
    updateList();
  }

  private void updateList() {
    mPuzzleAdapter.clear();
    if (mPuzzles == null) return;
    for (Database.Puzzle puzzle : mPuzzles)
      if (isIn(puzzle, mCollectionId))
        mPuzzleAdapter.add(puzzle);
    updateSelectedPuzzle();
  }

  private void updateSelectedPuzzle() {
    int pos = mList.getCheckedItemPosition();
    if (pos != ListView.INVALID_POSITION) mList.setItemChecked(pos, false);
    for (int i = 0, count = mPuzzleAdapter.getCount(); i < count; ++i) {
      if (mPuzzleAdapter.getItemId(i) == mPuzzleId) {
        // Too slow:
        //mList.smoothScrollToPosition(i);
        mList.setSelection(i);
        mList.setItemChecked(i, true);
        return;
      }
    }
  }

  private static boolean isIn(Database.Puzzle puzzle, long collectionId) {
    if (collectionId == Database.ALL_PSEUDO_COLLECTION_ID) return true;
    for (Database.Element element : puzzle.elements)
      if (collectionId == element.collection._id)
        return true;
    return false;
  }

  private static class FetchPuzzles extends WorkerFragment.Task<PuzzleListFragment, Void, Void, List<Database.Puzzle>> {
    private final Database mDb;

    FetchPuzzles(PuzzleListFragment fragment) {
      super(fragment);
      this.mDb = fragment.mDb;
    }

    @Override protected List<Database.Puzzle> doInBackground(Void... params) {
      return mDb.getAllPuzzles();
    }

    @Override protected void onPostExecute(PuzzleListFragment fragment, List<Database.Puzzle> puzzles) {
      fragment.setPuzzles(puzzles);
    }
  }
}
