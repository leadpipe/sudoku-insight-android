package us.blanshard.sudoku.android;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

public class TrailAdapter extends ArrayAdapter<TrailItem> implements OnCheckedChangeListener {

  private final SudokuActivity mActivity;
  private boolean mBuildingView;

  public TrailAdapter(SudokuActivity activity) {
    super(activity, R.layout.trail_item);
    mActivity = activity;
  }

  private class ViewHolder {
    View rowView;
    TextView label;
    CheckBox checkbox;

    public ViewHolder() {
      rowView = mActivity.getLayoutInflater().inflate(R.layout.trail_item, null);
      label = (TextView) rowView.findViewById(R.id.trail_item_label);
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
      holder.label.setTag(item);
      holder.label.setTextColor(item.color);
      holder.label.setText(item.toString());
      return holder.rowView;
    } finally {
      mBuildingView = false;
    }
  }

  @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    if (!mBuildingView)
      mActivity.trailCheckChanged((TrailItem) buttonView.getTag(), isChecked);
  }
}
