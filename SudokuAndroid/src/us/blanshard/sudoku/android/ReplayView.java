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

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * @author Luke Blanshard
 */
public class ReplayView extends SudokuView {

  private OnSelectListener mOnSelectListener;
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private Function<Location, Integer> mSelectableColors = (Function) Functions.constant(null);
  private Location mSelected;
  private Map<Location, NumSet> mEliminations;

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

  public void setSelectableColorsFunction(Function<Location, Integer> selectableColors) {
    mSelectableColors = selectableColors;
  }

  public void selectableColorsUpdated() {
    invalidate();
  }

  public Location getSelected() {
    return mSelected;
  }

  public void setSelected(Location loc) {
    Location old = mSelected;
    mSelected = loc;
    if (mOnSelectListener != null) mOnSelectListener.onSelect(loc);
    if (old != null) invalidateLocation(old);
    if (loc != null) invalidateLocation(loc);
  }

  public void addElimination(Assignment elimination) {
    if (mEliminations == null) mEliminations = Maps.newHashMap();
    NumSet set = mEliminations.get(elimination.location);
    mEliminations.put(elimination.location,
        set == null ? NumSet.of(elimination.numeral) : set.with(elimination.numeral));
  }

  public void removeElimination(Assignment elimination) {
    if (mEliminations == null) return;
    NumSet set = mEliminations.get(elimination.location);
    if (set != null && set.contains(elimination.numeral))
      mEliminations.put(elimination.location, set.without(elimination.numeral));
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    mPaint.setStyle(Style.STROKE);
    mPaint.setStrokeWidth(0);
    for (Location loc : Location.ALL) {
      drawEliminations(canvas, loc);
      drawSelectable(canvas, loc);
    }
  }

  private void drawEliminations(Canvas canvas, Location loc) {
    if (mGame.getState().get(loc) != null) return;
    NumSet set = mEliminations == null ? null : mEliminations.get(loc);
    if (set == null || set.isEmpty()) return;
    StringBuilder sb = new StringBuilder(set.get(0).number);
    for (int i = 1; i < set.size(); ++i) sb.append(',').append(set.get(i).number);
    String text = sb.toString();
    float s = mSquareSize;
    float x = mOffsetsX[loc.column.index];
    float y = mOffsetsY[loc.row.index];
    float w = mPaint.measureText(text);
    float textSize = mPaint.getTextSize();
    if (w >= s - 2) mPaint.setTextSize(textSize * (s - 2) / w);
    mPaint.setColor(Color.DKGRAY);
    canvas.drawText(text, x + s/2, y + s + mPaint.ascent(), mPaint);
    mPaint.setTextSize(textSize);
  }

  private void drawSelectable(Canvas canvas, Location loc) {
    Integer color = mSelectableColors.apply(loc);
    if (color == null) return;
    mPaint.setColor(color);
    float s = mSquareSize;
    float x = mOffsetsX[loc.column.index];
    float y = mOffsetsY[loc.row.index];
    canvas.drawRect(x + 1, y + 1, x + s - 1, y + s - 1, mPaint);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        int index = event.getActionIndex();
        float x = event.getX(index), y = event.getY(index);
        Location loc = getLocation(x, y);
        if (mSelectableColors.apply(loc) != null)
          setSelected(loc);
    }
    return true;
  }
}
