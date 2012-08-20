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

import us.blanshard.sudoku.android.Database.Game;

import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

/**
 * @author Luke Blanshard
 */
public class PuzzleAdapter extends ArrayAdapter<Database.Puzzle> {
  private final PuzzleListFragment mFragment;

  public PuzzleAdapter(PuzzleListFragment fragment) {
    super(fragment.getActivity(), R.layout.list_item);
    this.mFragment = fragment;
  }

  private class ViewHolder {
    final View rowView;
    final SudokuView grid;
    final TextView label;

    public ViewHolder() {
      rowView = mFragment.getActivity().getLayoutInflater().inflate(R.layout.list_item, null);
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
                return ToText.collectionNameHtml(getContext(), element);
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
