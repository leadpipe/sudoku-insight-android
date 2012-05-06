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

import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * @author Luke Blanshard
 */
public class PuzzleAdapter extends ArrayAdapter<Database.Puzzle> {
  private final PuzzleListFragment mFragment;

  public PuzzleAdapter(PuzzleListFragment fragment) {
    super(fragment.getActivity(), R.layout.puzzle_item);
    this.mFragment = fragment;
  }

  private class ViewHolder {
    View rowView;
    SudokuView grid;
    TextView label;

    public ViewHolder() {
      rowView = mFragment.getActivity().getLayoutInflater().inflate(R.layout.puzzle_item, null);
      grid = (SudokuView) rowView.findViewById(R.id.puzzle_item_grid);
      label = (TextView) rowView.findViewById(R.id.puzzle_item_label);
    }
  }

  @Override public long getItemId(int position) {
    return getItem(position)._id;
  }

  @Override public View getView(int position, View convertView, ViewGroup parent) {
    ViewHolder holder;
    if (convertView == null) {
      holder = new ViewHolder();
      holder.rowView.setTag(holder);
    } else {
      holder = (ViewHolder) convertView.getTag();
    }
    Database.Puzzle item = getItem(position);
    holder.grid.setTag(item);
    holder.grid.setPuzzle(item.puzzle);
    holder.label.setTag(item);
    holder.label.setText(item.toString());
    return holder.rowView;
  }
}
