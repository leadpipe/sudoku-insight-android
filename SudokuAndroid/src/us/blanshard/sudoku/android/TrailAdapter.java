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

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

public class TrailAdapter extends ArrayAdapter<TrailItem> implements OnCheckedChangeListener {

  private final SudokuFragment mFragment;
  private boolean mBuildingView;

  public TrailAdapter(SudokuFragment fragment) {
    super(fragment.getActivity(), R.layout.trail_item);
    mFragment = fragment;
  }

  private class ViewHolder {
    final View rowView;
    final TextView label;
    final TextView count;
    final CheckBox checkbox;

    public ViewHolder() {
      rowView = mFragment.getActivity().getLayoutInflater().inflate(R.layout.trail_item, null);
      label = (TextView) rowView.findViewById(R.id.trail_item_label);
      count = (TextView) rowView.findViewById(R.id.trail_item_count);
      checkbox = (CheckBox) rowView.findViewById(R.id.trail_item_checkbox);
    }
  }

  @Override public long getItemId(int position) {
    return getItem(position).trail.getId();
  }

  @Override public View getView(int position, View convertView, ViewGroup parent) {
    ViewHolder holder;
    if (convertView == null) {
      holder = new ViewHolder();
      holder.rowView.setTag(holder);
      holder.checkbox.setOnCheckedChangeListener(this);
    } else {
      holder = (ViewHolder) convertView.getTag();
    }
    mBuildingView = true;
    try {
      TrailItem item = getItem(position);
      holder.checkbox.setTag(item);
      holder.checkbox.setChecked(item.shown);
      int color = item.uninteresting ? Color.LTGRAY
          : position == 0 && mFragment.isTrailActive() ? item.color : item.dimColor;
      holder.label.setTextColor(color);
      holder.label.setText(item.toString());
      holder.count.setText(String.valueOf(item.trail.getSetCount()));
      return holder.rowView;
    } finally {
      mBuildingView = false;
    }
  }

  @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    if (!mBuildingView)
      mFragment.trailCheckChanged((TrailItem) buttonView.getTag(), isChecked);
  }
}
