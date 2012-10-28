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
import android.util.FloatMath;
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

  private static final float ASGMT_SCALE = 0.75f;
  private static final float CLOCK_SCALE = 0.4f;
  private static final int ELIM_COLOR = Color.argb(128, 255, 100, 100);
  private static final int ASGMT_COLOR = Color.argb(128, 96, 96, 128);
  private static final int UNIT_COLOR = Color.argb(128, 96, 96, 128);
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

  private float mToBaseline;
  private float[] mClockX;
  private float[] mClockY;

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
    mPaint.setTextSize(mTextSize * ASGMT_SCALE);
    mToBaseline = calcToBaseline();

    mPaint.setTextSize(mTextSize * CLOCK_SCALE);
    float r = (mSquareSize + mPaint.ascent()) * 0.5f - mThickLineWidth;
    float h = mSquareSize * 0.5f;

    mClockX = new float[10];
    mClockY = new float[10];
    for (Numeral num : Numeral.ALL) {
      float radians = calcRadians(num.number);
      mClockX[num.number] = h + r * FloatMath.cos(radians);
      mClockY[num.number] = h + r * FloatMath.sin(radians);
    }
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (mInsights != null && mLocDisplays == null) buildLocDisplays();
    mPaint.setStyle(Style.STROKE);
    mPaint.setStrokeWidth(mThickLineWidth);
    mPaint.setTypeface(Typeface.DEFAULT);
    mPaint.setFakeBoldText(false);
    mPaint.setTextSize(mTextSize * ASGMT_SCALE);
    for (Location loc : Location.ALL) {
      drawSelectable(canvas, loc);
      if (mLocDisplays != null)
        drawInsights(canvas, loc, mLocDisplays.get(loc));
    }
    if (mErrorUnits != null)
      for (Unit unit : mErrorUnits)
        drawErrorUnit(canvas, unit);
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
    if (mEliminations != null) {
      for (Map.Entry<Location, NumSet> entry : mEliminations.entrySet()) {
        getLocDisplay(entry.getKey()).crossOut(entry.getValue());
      }
    }
  }

  private LocDisplay getLocDisplay(Location loc) {
    LocDisplay answer = mLocDisplays.get(loc);
    if (answer == null)
      mLocDisplays.put(loc, (answer = new LocDisplay()));
    return answer;
  }

  private void drawInsights(Canvas canvas, Location loc, LocDisplay locDisplay) {
    if (locDisplay == null) return;

    float s = mSquareSize;
    float h = s * 0.5f;
    float x = mOffsetsX[loc.column.index];
    float y = mOffsetsY[loc.row.index];

    if ((locDisplay.flags & ERROR_BORDER_MASK) != 0) {
      mPaint.setColor(Color.RED);
      canvas.drawRect(x, y, x + s, y + s, mPaint);
    }

    if ((locDisplay.flags & UNIT_MASK) != 0) {
      mPaint.setColor(UNIT_COLOR);
      if (locDisplay.hasUnit(Unit.Type.ROW))
        canvas.drawLine(x, y + h, x + s, y + h, mPaint);
      if (locDisplay.hasUnit(Unit.Type.COLUMN))
        canvas.drawLine(x + h, y, x + h, y + s, mPaint);
      if (locDisplay.hasUnit(Unit.Type.BLOCK)) {
        float q = h * 0.5f;
        canvas.drawRect(x + q, y + q, x + h + q, y + h + q, mPaint);
      }
    }

    if (!locDisplay.crossedOut.isEmpty()) {
      mPaint.setTextSize(mTextSize * CLOCK_SCALE);
      mPaint.setStyle(Style.FILL);
      mPaint.setColor(ELIM_COLOR);
      for (Numeral num : locDisplay.crossedOut) {
        canvas.drawText("\u00d7", x + mClockX[num.number], y + mClockY[num.number], mPaint);
      }
    }

    boolean open = isOpen(loc);
    if (open && !locDisplay.overlaps.isEmpty()) {
      mPaint.setTextSize(mTextSize * CLOCK_SCALE);
      mPaint.setStyle(Style.FILL);
      mPaint.setColor(ASGMT_COLOR);
      for (Numeral num : locDisplay.overlaps) {
        canvas.drawText(num.toString(), x + mClockX[num.number], y + mClockY[num.number], mPaint);
      }
    }

    if (open && !locDisplay.possibles.isEmpty()) {
      mPaint.setTextSize(mTextSize * ASGMT_SCALE);
      mPaint.setStyle(Style.FILL);
      mPaint.setColor(ASGMT_COLOR);
      String text = locDisplay.possibles.toString();
      text = text.substring(1, text.length() - 1);  // strip brackets
      float w = mPaint.measureText(text);
      if (w >= s - 2) mPaint.setTextSize(mTextSize * ASGMT_SCALE * (s - 2) / w);
      canvas.drawText(text, x + h, y + mToBaseline, mPaint);
    }

    mPaint.setTextSize(mTextSize * ASGMT_SCALE);
    mPaint.setStyle(Style.STROKE);
  }

  private void drawErrorUnit(Canvas canvas, Unit unit) {
    mPaint.setColor(Color.RED);
    float s = mSquareSize;
    float top = mOffsetsX[unit.get(0).column.index];
    float left = mOffsetsY[unit.get(0).row.index];
    float bottom = s + mOffsetsX[unit.get(9 - 1).column.index];
    float right = s + mOffsetsY[unit.get(9 - 1).row.index];
    canvas.drawRect(left, top, right, bottom, mPaint);
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
    public int flags;
    public NumSet crossedOut = NumSet.NONE;
    public NumSet overlaps = NumSet.NONE;
    public NumSet possibles = NumSet.NONE;

    public void addUnit(Unit unit) {
      flags |= unitFlag(unit.getType());
    }

    public boolean hasUnit(Unit.Type type) {
      return (flags & unitFlag(type)) != 0;
    }

    public void crossOut(NumSet set) {
      crossedOut = crossedOut.or(set);
    }

    public void updatePossibles(NumSet set) {
      if (possibles == NumSet.NONE) possibles = set;
      else possibles = possibles.and(set);
    }

    private int unitFlag(Unit.Type type) {
      return 1 << type.ordinal();
    }
  }
}
