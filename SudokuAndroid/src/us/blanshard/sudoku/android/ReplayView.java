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

import static com.google.common.base.Preconditions.checkState;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
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
  private static final int ASGMT_COLOR = Color.argb(128, 96, 96, 128);
  private static final int UNIT_MASK = 7;
  private static final int ERROR_BORDER_MASK = 8;
  private static final int QUESTION_MASK = 16;

  private OnSelectListener mOnSelectListener;
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private Function<Location, Integer> mSelectableColors = (Function) Functions.constant(null);
  private Location mSelected;
  private Map<Location, NumSet> mEliminations;
  private Collection<Insight> mInsights;
  private final Collection<Location> mConflicts = new LocSet();
  private Map<Location, LocDisplay> mLocDisplays;
  private Collection<Unit> mErrorUnits;

  private float mInsightTextSize;
  private float mToBaseline;

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
        set == null ? elimination.numeral.asSet() : set.with(elimination.numeral));
    mLocDisplays = null;
    mErrorUnits = null;
    invalidateLocation(elimination.location);
  }

  public void removeElimination(Assignment elimination) {
    if (mEliminations == null) return;
    NumSet set = mEliminations.get(elimination.location);
    if (set != null && set.contains(elimination.numeral))
      mEliminations.put(elimination.location, set.without(elimination.numeral));
    mLocDisplays = null;
    mErrorUnits = null;
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
    mLocDisplays = null;
    mErrorUnits = null;
    invalidate();
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
    invalidate();
  }

  public void addInsights(Iterable<Insight> insights) {
    for (Insight insight : insights)
      addInsight(insight);
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    mInsightTextSize = mTextSize * 0.75f;
    mPaint.setTextSize(mInsightTextSize);
    mToBaseline = calcToBaseline();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (mInsights != null && mLocDisplays == null) buildLocDisplays();
    mPaint.setStyle(Style.STROKE);
    mPaint.setStrokeWidth(mThickLineWidth);
    mPaint.setTypeface(Typeface.DEFAULT);
    mPaint.setFakeBoldText(false);
    mPaint.setTextSize(mInsightTextSize);
    for (Location loc : Location.ALL) {
      drawEliminations(canvas, loc);
      drawSelectable(canvas, loc);
    }
    if (mInsights != null)
      for (Insight insight : mInsights)
        drawInsight(canvas, insight);
  }

  private void buildLocDisplays() {
    mLocDisplays = Maps.newHashMap();
    LocDisplay locDisplay;
    for (Insight insight : mInsights) {
      switch (insight.type) {
        case BARRED_LOCATION: {
          BarredLoc barredLoc = (BarredLoc) insight;
          locDisplay = getLocDisplay(barredLoc.getLocation());
          locDisplay.crossOut(NumSet.ALL);
          locDisplay.flags |= ERROR_BORDER_MASK;
          break;
        }
        case BARRED_NUMERAL: {
          BarredNum barredNum = (BarredNum) insight;
          if (mErrorUnits == null) mErrorUnits = Sets.newHashSet();
          mErrorUnits.add(barredNum.getUnit());
          for (Location loc : barredNum.getUnit()) {
            locDisplay = getLocDisplay(loc);
            locDisplay.crossOut(barredNum.getNumeral().asSet());
            // TODO: is this visible enough or do we need a different set for errors?
          }
          break;
        }
        case FORCED_LOCATION: {
          ForcedLoc forcedLoc = (ForcedLoc) insight;
          locDisplay = getLocDisplay(forcedLoc.getLocation());
          locDisplay.addUnit(forcedLoc.getUnit());
          locDisplay.updatePossibles(forcedLoc.getNumeral().asSet());
          break;
        }
        case FORCED_NUMERAL: {
          ForcedNum forcedNum = (ForcedNum) insight;
          locDisplay = getLocDisplay(forcedNum.getLocation());
          locDisplay.crossOut(forcedNum.getNumeral().asSet().not());
          locDisplay.updatePossibles(forcedNum.getNumeral().asSet());
          break;
        }
        case LOCKED_SET: {
          LockedSet lockedSet = (LockedSet) insight;
          for (Location loc : lockedSet.getLocations()) {
            locDisplay = getLocDisplay(loc);
            locDisplay.addUnit(lockedSet.getLocations().unit);
            locDisplay.updatePossibles(lockedSet.getNumerals());
            // TODO: consider adding this:
//            for (Assignment assignment : lockedSet.getEliminations()) {
//              getLocDisplay(assignment.location).crossOut(assignment.numeral.asSet());
//            }
          }
          break;
        }
        case OVERLAP: {
          Overlap overlap = (Overlap) insight;
          for (Location loc : overlap.getUnit().intersect(overlap.getOverlappingUnit())) {
            locDisplay = getLocDisplay(loc);
            locDisplay.overlaps = locDisplay.overlaps.or(overlap.getNumeral().asSet());
            locDisplay.addUnit(overlap.getOverlappingUnit());
            // TODO: consider adding eliminations
          }
          break;
        }
      }
    }
  }

  private LocDisplay getLocDisplay(Location loc) {
    LocDisplay answer = mLocDisplays.get(loc);
    if (answer == null)
      mLocDisplays.put(loc, (answer = new LocDisplay()));
    return answer;
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
    if (w >= s - 2) mPaint.setTextSize(mInsightTextSize * (s - 2) / w);
    mPaint.setColor(ELIM_COLOR);
    canvas.drawLine(x, y, x + s, y + s, mPaint);
    canvas.drawText(text, x + s/2, y + mToBaseline, mPaint);
    mPaint.setTextSize(mInsightTextSize);
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
    mPaint.setColor(Color.RED);
    Location loc = barredLoc.getLocation();
    float s = mSquareSize;
    float x = mOffsetsX[loc.column.index];
    float y = mOffsetsY[loc.row.index];
    canvas.drawRect(x, y, x + s, y + s, mPaint);
    // TODO: x out all of 1-9 on the clock face
  }

  private void drawBarredNum(Canvas canvas, BarredNum barredNum) {
    mPaint.setColor(Color.RED);
    Unit unit = barredNum.getUnit();
    float s = mSquareSize;
    float top = mOffsetsX[unit.get(0).column.index];
    float left = mOffsetsY[unit.get(0).row.index];
    float bottom = s + mOffsetsX[unit.get(9 - 1).column.index];
    float right = s + mOffsetsY[unit.get(9 - 1).row.index];
    canvas.drawRect(left, top, right, bottom, mPaint);
    // TODO: for each open location in the unit, x out the numeral
  }

  private void drawForcedLoc(Canvas canvas, ForcedLoc forcedLoc) {
    mPaint.setColor(ASGMT_COLOR);
    Location loc = forcedLoc.getLocation();
    float s = mSquareSize;
    float h = s * 0.5f;
    float x = mOffsetsX[loc.column.index];
    float y = mOffsetsY[loc.row.index];
    if (isOpen(loc)) {
      canvas.drawText(forcedLoc.getNumeral().toString(), x + h, y + mToBaseline, mPaint);
    }
    switch (forcedLoc.getUnit().getType()) {
      case ROW:
        canvas.drawLine(x + h, y, x + h, y + s, mPaint);
        break;
      case COLUMN:
        canvas.drawLine(x, y + h, x + s, y + h, mPaint);
        break;
      case BLOCK:
        float q = h * 0.5f;
        canvas.drawRect(x + q, y + q, x + h + q, y + h + q, mPaint);
        break;
    }
  }

  private void drawForcedNum(Canvas canvas, ForcedNum forcedNum) {
    mPaint.setColor(ASGMT_COLOR);

  }

  private void drawLockedSet(Canvas canvas, LockedSet lockedSet) {

  }

  private void drawOverlap(Canvas canvas, Overlap overlap) {

  }

  private boolean isOpen(Location loc) {
    return getInputState().get(loc) == null;
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

  /**
   * Combines display info for all the insights that affect a given location.
   */
  private static class LocDisplay {
    int flags;
    NumSet crossedOut = NumSet.NONE;
    NumSet overlaps = NumSet.NONE;
    NumSet possibles = NumSet.NONE;

    void addUnit(Unit unit) {
      flags |= unitFlag(unit.getType());
    }

    boolean hasUnit(Unit.Type type) {
      return (flags & unitFlag(type)) != 0;
    }

    void crossOut(NumSet set) {
      crossedOut = crossedOut.or(set);
    }

    void updatePossibles(NumSet set) {
      if (possibles == NumSet.NONE) possibles = set;
      else {
        possibles = possibles.and(set);
        checkState(possibles.isEmpty());
      }
    }

    private int unitFlag(Unit.Type type) {
      return 1 << type.ordinal();
    }
  }
}
