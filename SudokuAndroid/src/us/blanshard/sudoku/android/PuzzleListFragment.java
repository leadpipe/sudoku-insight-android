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
import roboguice.inject.ContextSingleton;
import roboguice.inject.InjectView;

import us.blanshard.sudoku.android.Database.Game;
import us.blanshard.sudoku.android.actionbarcompat.ActionBarHelper;

import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Longs;

import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

/**
 * Shows a list of puzzles from the database.
 *
 * @author Luke Blanshard
 */
@ContextSingleton
public class PuzzleListFragment extends RoboFragment {
  //private static final String TAG = "PuzzleListFragment";
  @InjectView(R.id.puzzles) ListView mList;
  @Inject Database mDb;
  @Inject PuzzleListActivity mActivity;
  @Inject ActionBarHelper mActionBarHelper;
  @Inject Prefs mPrefs;
  private PuzzleAdapter mPuzzleAdapter;
  private List<Database.Puzzle> mPuzzles;
  private long mCollectionId = Database.ALL_PSEUDO_COLLECTION_ID;
  private long mPuzzleId = 0;
  private Sort mSort = Sort.NUMBER;

  enum Sort {
    NUMBER(R.id.menu_sort_by_number, new Comparator<Database.Puzzle>() {
      @Override public int compare(Database.Puzzle lhs, Database.Puzzle rhs) {
        return Longs.compare(lhs._id, rhs._id);
      }
    }),
    ELAPSED(R.id.menu_sort_by_elapsed, new Comparator<Database.Puzzle>() {
      @Override public int compare(Database.Puzzle lhs, Database.Puzzle rhs) {
        return ComparisonChain.start()
            .compare(elapsed(lhs), elapsed(rhs))
            .compare(lhs._id, rhs._id)
            .result();
      }
    }),
    TIME(R.id.menu_sort_by_time, new Comparator<Database.Puzzle>() {
      @Override public int compare(Database.Puzzle lhs, Database.Puzzle rhs) {
        return ComparisonChain.start()
            .compare(time(lhs), time(rhs))
            .compare(lhs._id, rhs._id)
            .result();
      }
    });

    private static final Sort[] values = values();
    Sort(int itemId, Comparator<Database.Puzzle> comparator) {
      this.itemId = itemId;
      this.comparator = comparator;
    }
    static long elapsed(Database.Puzzle puzzle) {
      if (puzzle.games.isEmpty()) return 0;
      return puzzle.games.get(puzzle.games.size() - 1).elapsedMillis;
    }
    static long time(Database.Puzzle puzzle) {
      if (puzzle.games.isEmpty()) return 0;
      return puzzle.games.get(puzzle.games.size() - 1).lastTime;
    }

    public final int itemId;
    public final Comparator<Database.Puzzle> comparator;

    public static Sort fromOrdinal(int ordinal, Sort def) {
      for (Sort sort : values)
        if (sort.ordinal() == ordinal)
          return sort;
      return def;
    }

    public static Sort fromMenuItem(MenuItem item) {
      for (Sort sort : values)
        if (sort.itemId == item.getItemId())
          return sort;
      return null;
    }
  }

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    setHasOptionsMenu(true);
    return inflater.inflate(R.layout.list, container, true);
  }

  @Override public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
    menuInflater.inflate(R.menu.list, menu);
    super.onCreateOptionsMenu(menu, menuInflater);
  }

  @Override public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mPuzzleAdapter = new PuzzleAdapter();
    mList.setAdapter(mPuzzleAdapter);
    mList.setEnabled(true);
  }

  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    new FetchPuzzles(this).execute();
    mSort = Sort.fromOrdinal(mPrefs.getSort(), Sort.NUMBER);
    mActionBarHelper.invalidateOptionsMenu();
  }

  @Override public void onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    MenuItem item = menu.findItem(mSort.itemId);
    if (item != null) item.setChecked(true);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    Sort sort = Sort.fromMenuItem(item);
    if (sort == null) return false;
    if (sort != mSort) {
      mSort = sort;
      mPrefs.setSortAsync(sort.ordinal());
      updateSort();
      mActionBarHelper.invalidateOptionsMenu();
    }
    return true;
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
    if (mSort != Sort.NUMBER)
      updateSort();
    else
      updateSelectedPuzzle();
  }

  private void updateSort() {
    mPuzzleAdapter.sort(mSort.comparator);
    updateSelectedPuzzle();
  }

  private void updateSelectedPuzzle() {
    int pos = mList.getCheckedItemPosition();
    if (pos != ListView.INVALID_POSITION) mList.setItemChecked(pos, false);
    for (int i = 0, count = mPuzzleAdapter.getCount(); i < count; ++i) {
      if (mPuzzleAdapter.getItemId(i) == mPuzzleId) {
        mList.setItemChecked(i, true);
        // Too slow:
        //mList.smoothScrollToPosition(i);
        mList.setSelection(0);
        mList.setSelection(i);
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

  private class PuzzleAdapter extends ArrayAdapter<Database.Puzzle> {

    public PuzzleAdapter() {
      super(getActivity(), R.layout.list_item);
    }

    private class ViewHolder {
      final View rowView;
      final SudokuView grid;
      final TextView label;

      public ViewHolder() {
        rowView = getActivity().getLayoutInflater().inflate(R.layout.list_item, null);
        grid = (SudokuView) rowView.findViewById(R.id.list_item_grid);
        label = (TextView) rowView.findViewById(R.id.list_item_label);
      }
    }

    @Override public long getItemId(int position) {
      return getItem(position)._id;
    }

    @Override public boolean hasStableIds() {
      return true;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
      ViewHolder holder;
      if (convertView == null) {
        holder = new ViewHolder();
        holder.rowView.setTag(holder);
      } else {
        holder = (ViewHolder) convertView.getTag();
      }
      Database.Puzzle puzzle = getItem(position);
      holder.grid.setPuzzle(puzzle.puzzle);
      holder.label.setText(Html.fromHtml(puzzleDescriptionHtml(puzzle)));
      return holder.rowView;
    }

    private String puzzleDescriptionHtml(Database.Puzzle puzzle) {
      StringBuilder sb = new StringBuilder();
      sb.append(getContext().getString(R.string.text_puzzle_number_start, puzzle._id));
      if (!puzzle.games.isEmpty()) {
        appendGameSummary(sb, puzzle.games.get(puzzle.games.size() - 1));
        if (!puzzle.elements.isEmpty()) sb.append("<br>");
      }
      if (!puzzle.elements.isEmpty()) {
        sb.append(getContext().getString(R.string.text_collection_in_start));
        Joiner.on(getContext().getString(R.string.text_collection_separator))
            .appendTo(sb, Iterables.transform(puzzle.elements, new Function<Database.Element, String>() {
                @Override public String apply(Database.Element element) {
                  return ToText.collectionNameHtml(getContext(), element, false);
                }
              }));
        sb.append(getContext().getString(R.string.text_sentence_end));
      }
      return sb.toString();
    }

    private void appendGameSummary(StringBuilder sb, Game game) {
      sb.append(ToText.gameSummaryHtml(getContext(), game));
      sb.append(getContext().getString(R.string.text_sentence_end));
    }
  }
}
