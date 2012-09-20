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
import android.graphics.Paint.Style;
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
    Location old = mSelected;
    mSelected = loc;
    if (mOnSelectListener != null) mOnSelectListener.onSelect(loc);
    if (old != null) invalidateLocation(old);
    if (loc != null) invalidateLocation(loc);
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (mSelectable == null || mSelectable.isEmpty()) return;
    mPaint.setStyle(Style.STROKE);
    mPaint.setStrokeWidth(0);
    for (Location loc : Location.ALL) {
      if (loc != mSelected && !mSelectable.contains(loc)) continue;
      mPaint.setColor(loc == mSelected ? Color.BLUE : Color.YELLOW);
      float s = mSquareSize;
      float x = mOffsetsX[loc.column.index];
      float y = mOffsetsY[loc.row.index];
      canvas.drawRect(x + 1, y + 1, x + s - 1, y + s - 1, mPaint);
    }
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        int index = event.getActionIndex();
        float x = event.getX(index), y = event.getY(index);
        Location loc = getLocation(x, y);
        if (mSelectable != null && mSelectable.contains(loc))
          setSelected(loc);
    }
    return true;
  }
}
