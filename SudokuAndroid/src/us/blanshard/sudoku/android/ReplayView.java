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

import us.blanshard.sudoku.core.Location;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.Set;

/**
 * @author Luke Blanshard
 */
public class ReplayView extends SudokuView {

  private OnSelectListener mOnSelectListener;
  private Set<Location> mSelectable;
  private Location mSelected;

  public ReplayView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ReplayView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public interface OnSelectListener {
    /** Called when the user has touched the given location. */
    void onSelect(Location loc);
  }

  public void setOnSelectListener(OnSelectListener listener) {
    mOnSelectListener = listener;
  }

  public void setSelectable(Set<Location> selectable) {
    mSelectable = selectable;
    mSelected = null;
    invalidate();
  }

  public void setSelected(Location loc) {
    if (mSelectable != null && mSelectable.contains(loc)) {
      mSelected = loc;
      if (mOnSelectListener != null) mOnSelectListener.onSelect(loc);
      invalidateLocation(loc);
    }
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (mSelectable == null || mSelectable.isEmpty()) return;
    for (Location loc : mSelectable) {
      mPaint.setColor(loc == mSelected ? Color.BLUE : Color.YELLOW);
      float half = mSquareSize * 0.5f;
      float cx = mOffsetsX[loc.column.index] + half;
      float cy = mOffsetsY[loc.row.index] + half;
      float radius = half - 1;
      canvas.drawCircle(cx, cy, radius, mPaint);
    }
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        int index = event.getActionIndex();
        float x = event.getX(index), y = event.getY(index);
        setSelected(getLocation(x, y));
    }
    return true;
  }
}
