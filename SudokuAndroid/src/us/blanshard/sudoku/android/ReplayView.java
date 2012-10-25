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
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.insight.BarredLoc;
import us.blanshard.sudoku.insight.BarredNum;
import us.blanshard.sudoku.insight.Conflict;
import us.blanshard.sudoku.insight.ForcedLoc;
import us.blanshard.sudoku.insight.ForcedNum;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Implication;
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.Insight.Type;
import us.blanshard.sudoku.insight.LockedSet;
import us.blanshard.sudoku.insight.Overlap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Map;

/**
 * @author Luke Blanshard
 */
public class ReplayView extends SudokuView {

  private static final int ELIM_COLOR = Color.argb(128, 255, 100, 100);

  private OnSelectListener mOnSelectListener;
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private Function<Location, Integer> mSelectableColors = (Function) Functions.constant(null);
  private Location mSelected;
  private Map<Location, NumSet> mEliminations;
  private Collection<Insight> mInsights;
  private final Collection<Location> mConflicts = new LocSet();

  public ReplayView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setBrokenLocations(mConflicts);
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
    invalidateLocation(elimination.location);
  }

  public void removeElimination(Assignment elimination) {
    if (mEliminations == null) return;
    NumSet set = mEliminations.get(elimination.location);
    if (set != null && set.contains(elimination.numeral))
      mEliminations.put(elimination.location, set.without(elimination.numeral));
    invalidateLocation(elimination.location);
  }

  public GridMarks getGridMarks() {
    GridMarks gm = new GridMarks(getInputState().getGrid());
    if (getInputState().getId() < 0 && mEliminations != null) {
      GridMarks.Builder builder = gm.toBuilder();
      for (Map.Entry<Location, NumSet> entry : mEliminations.entrySet())
        for (Numeral num : entry.getValue())
          builder.eliminate(entry.getKey(), num);
      gm = builder.build();
    }
    return gm;
  }

  public void clearInsights() {
    mInsights = null;
    mConflicts.clear();
  }

  public void addInsight(Insight insight) {
    if (insight.type == Insight.Type.IMPLICATION) {
      Implication implication = (Implication) insight;
      addInsights(implication.getAntecedents());
      addInsight(implication.getConsequent());
    } else if (insight.type == Type.CONFLICT) {
      Conflict conflict = (Conflict) insight;
      mConflicts.addAll(conflict.getLocations());
    } else {
      if (mInsights == null) mInsights = Sets.newLinkedHashSet();
      mInsights.add(insight);
    }
  }

  public void addInsights(Iterable<Insight> insights) {
    for (Insight insight : insights)
      addInsight(insight);
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    mPaint.setStyle(Style.STROKE);
    mPaint.setTypeface(Typeface.DEFAULT);
    mPaint.setFakeBoldText(false);
    for (Location loc : Location.ALL) {
      drawEliminations(canvas, loc);
      drawSelectable(canvas, loc);
    }
    if (mInsights != null)
      for (Insight insight : mInsights)
        drawInsight(canvas, insight);
  }

  private void drawEliminations(Canvas canvas, Location loc) {
    if (mGame == null || mGame.getState().get(loc) != null) return;
    NumSet set = mEliminations == null ? null : mEliminations.get(loc);
    if (set == null || set.isEmpty()) return;
    StringBuilder sb = new StringBuilder().append(set.get(0).number);
    for (int i = 1; i < set.size(); ++i) sb.append(',').append(set.get(i).number);
    String text = sb.toString();
    float s = mSquareSize;
    float x = mOffsetsX[loc.column.index];
    float y = mOffsetsY[loc.row.index];
    float w = mPaint.measureText(text);
    float textSize = mPaint.getTextSize();
    if (w >= s - 2) mPaint.setTextSize(textSize * (s - 2) / w);
    mPaint.setColor(ELIM_COLOR);
    mPaint.setStrokeWidth(s * 0.05f);
    canvas.drawLine(x, y, x + s, y + s, mPaint);
    canvas.drawText(text, x + s/2, y - mPaint.ascent() + (s - mPaint.getTextSize()) / 2 - 1, mPaint);
    mPaint.setTextSize(textSize);
  }

  private void drawSelectable(Canvas canvas, Location loc) {
    Integer color = mSelectableColors.apply(loc);
    if (color == null) return;
    mPaint.setColor(color);
    float s = mSquareSize;
    float x = mOffsetsX[loc.column.index];
    float y = mOffsetsY[loc.row.index];
    mPaint.setStrokeWidth(0);
    canvas.drawRect(x + 1, y + 1, x + s - 1, y + s - 1, mPaint);
  }

  private void drawInsight(Canvas canvas, Insight insight) {
    switch (insight.type) {
      case BARRED_LOCATION:
        drawBarredLoc(canvas, (BarredLoc) insight);
        break;

      case BARRED_NUMERAL:
        drawBarredNum(canvas, (BarredNum) insight);
        break;

      case FORCED_LOCATION:
        drawForcedLoc(canvas, (ForcedLoc) insight);
        break;

      case FORCED_NUMERAL:
        drawForcedNum(canvas, (ForcedNum) insight);
        break;

      case LOCKED_SET:
        drawLockedSet(canvas, (LockedSet) insight);
        break;

      case OVERLAP:
        drawOverlap(canvas, (Overlap) insight);
        break;
    }
  }

  private void drawBarredLoc(Canvas canvas, BarredLoc barredLoc) {

  }

  private void drawBarredNum(Canvas canvas, BarredNum barredNum) {

  }

  private void drawForcedLoc(Canvas canvas, ForcedLoc forcedLoc) {

  }

  private void drawForcedNum(Canvas canvas, ForcedNum forcedNum) {

  }

  private void drawLockedSet(Canvas canvas, LockedSet lockedSet) {

  }

  private void drawOverlap(Canvas canvas, Overlap overlap) {

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
