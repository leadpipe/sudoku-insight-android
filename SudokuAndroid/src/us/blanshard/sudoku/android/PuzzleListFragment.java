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
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import java.util.List;

import javax.inject.Inject;

/**
 * Shows a list of puzzles from the database.
 *
 * @author Luke Blanshard
 */
public class PuzzleListFragment extends RoboFragment implements OnItemClickListener {
  @InjectView(R.id.puzzles) ListView mList;
  @Inject Database mDb;
  private PuzzleAdapter mPuzzleAdapter;

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
    mList.setOnItemClickListener(this);
  }

  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    new FetchPuzzles().execute();
  }

  @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    Database.Puzzle puzzle = mPuzzleAdapter.getItem(position);
    Toast.makeText(getActivity(), puzzle.puzzle.toString(), Toast.LENGTH_LONG).show();
  }

  private class FetchPuzzles extends AsyncTask<Void, Void, List<Database.Puzzle>> {
    @Override protected List<Database.Puzzle> doInBackground(Void... params) {
      return mDb.getAllPuzzles();
    }

    @Override protected void onPostExecute(List<Database.Puzzle> puzzles) {
      setPuzzles(puzzles);
    }
  }

  private void setPuzzles(List<Database.Puzzle> puzzles) {
    for (Database.Puzzle puzzle : puzzles)
      mPuzzleAdapter.add(puzzle);
  }
}
