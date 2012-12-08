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
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.insight.BarredLoc;
import us.blanshard.sudoku.insight.BarredNum;
import us.blanshard.sudoku.insight.Conflict;
import us.blanshard.sudoku.insight.DisprovedAssignment;
import us.blanshard.sudoku.insight.ForcedLoc;
import us.blanshard.sudoku.insight.ForcedNum;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Implication;
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.LockedSet;
import us.blanshard.sudoku.insight.Overlap;
import us.blanshard.sudoku.insight.UnfoundedAssignment;

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

import javax.annotation.Nullable;

/**
 * @author Luke Blanshard
 */
public class ReplayView extends SudokuView {

  private static final float ASGMT_SCALE = 0.8f;
  private static final float ASGMT_SCALE2 = 0.68f;
  private static final float ASGMT_SCALE3 = 0.55f;
  private static final float CLOCK_SCALE = 0.5f;
  private static final float QUESTION_SCALE = 0.6f;
  private static final int ASGMT_COLOR = Color.argb(192, 32, 160, 64);
  private static final int ELIM_COLOR = Color.argb(192, 255, 100, 100);
  private static final int OVERLAP_COLOR = Color.argb(128, 32, 96, 160);
  private static final int QUESTION_COLOR = Color.argb(128, 192, 96, 96);
  private static final int UNIT_COLOR = Color.argb(64, 96, 96, 96);
  private static final int SELECTION_COLOR = Color.argb(16, 0, 0, 255);

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

  private float[] mToBaseline = new float[3];
  private float[] mPossiblesSize = new float[3];
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
    /**
     * Called when the user has touched the given location, or the location has
     * been selected programmatically.
     */
    void onSelect(Location loc, boolean byUser);
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
    setSelected(loc, false);
  }

  private void setSelected(Location loc, boolean byUser) {
    Location old = mSelected;
    mSelected = loc;
    if (mOnSelectListener != null) mOnSelectListener.onSelect(loc, byUser);
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

  public GridMarks getGridMarks(@Nullable Location clear) {
    Grid grid = getInputState().getGrid();
    if (clear != null)
      grid = grid.toBuilder().remove(clear).build();
    GridMarks gm = new GridMarks(grid);
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
    if (mInsights == null) mInsights = Sets.newLinkedHashSet();
    switch (insight.type) {
      case IMPLICATION:
        Implication implication = (Implication) insight;
        addInsights(implication.getAntecedents());
        addInsight(implication.getConsequent());
        break;
      case CONFLICT:
        Conflict conflict = (Conflict) insight;
        mConflicts.addAll(conflict.getLocations());
        mInsights.add(insight);
        invalidate();
        break;
      case DISPROVED_ASSIGNMENT:
        DisprovedAssignment disprovedAssignment = (DisprovedAssignment) insight;
        addInsight(disprovedAssignment.getUnfoundedAssignment());
        addInsight(disprovedAssignment.getResultingError());
        break;
      default:
        mInsights.add(insight);
        mLocDisplays = null;
        invalidate();
        break;
    }
  }

  public void addInsights(Iterable<Insight> insights) {
    for (Insight insight : insights)
      addInsight(insight);
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    setPossiblesSize(0, mTextSize * ASGMT_SCALE);
    setPossiblesSize(1, mTextSize * ASGMT_SCALE2);
    setPossiblesSize(2, mTextSize * ASGMT_SCALE3);

    mPaint.setTextSize(mTextSize * CLOCK_SCALE);
    float a = mPaint.ascent() * -0.5f;
    float h = mSquareSize * 0.5f;
    float r = h - a;

    mClockX = new float[10];
    mClockY = new float[10];
    for (Numeral num : Numeral.ALL) {
      float radians = calcRadians(num.number);
      mClockX[num.number] = h + r * (float) Math.cos(radians);
      mClockY[num.number] = h + r * (float) Math.sin(radians) + a;
    }
  }

  private void setPossiblesSize(int index, float size) {
    mPossiblesSize[index] = size;
    mPaint.setTextSize(size);
    mToBaseline[index] = calcToBaseline();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (mLocDisplays == null) buildLocDisplays();
    mPaint.setStrokeWidth(mThickLineWidth);
    mPaint.setTypeface(Typeface.DEFAULT);
    mPaint.setFakeBoldText(false);
    for (Location loc : Location.ALL) {
      drawSelectable(canvas, loc);
      drawInsights(canvas, loc, mLocDisplays.get(loc));
    }
    if (mErrorUnits != null)
      for (Unit unit : mErrorUnits)
        drawErrorUnit(canvas, unit);
  }

  private void buildLocDisplays() {
    mLocDisplays = Maps.newHashMap();
    mErrorUnits = null;
    LocDisplay locDisplay;
    if (mInsights != null)
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
              if (isOpen(loc)) {
                locDisplay = getLocDisplay(loc);
                locDisplay.crossOut(barredNum.getNumeral().asSet());
              }
            }
            break;
          }
          case CONFLICT: {
            Conflict conflict = (Conflict) insight;
            if (mErrorUnits == null) mErrorUnits = Sets.newHashSet();
            mErrorUnits.add(conflict.getLocations().unit);
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
            }
            break;
          }
          case OVERLAP: {
            Overlap overlap = (Overlap) insight;
            for (Location loc : overlap.getUnit().intersect(overlap.getOverlappingUnit())) {
              locDisplay = getLocDisplay(loc);
              locDisplay.overlaps = locDisplay.overlaps.or(overlap.getNumeral().asSet());
              locDisplay.addUnit(overlap.getOverlappingUnit());
            }
            break;
          }
          case UNFOUNDED_ASSIGNMENT: {
            UnfoundedAssignment unfoundedAssignment = (UnfoundedAssignment) insight;
            Assignment assignment = unfoundedAssignment.getImpliedAssignment();
            locDisplay = getLocDisplay(assignment.location);
            locDisplay.updatePossibles(assignment.numeral.asSet());
            locDisplay.flags |= QUESTION_MASK;
            break;
          }
          default:
            break;
        }
      }
    if (mEliminations != null)
      for (Map.Entry<Location, NumSet> entry : mEliminations.entrySet()) {
        if (isOpen(entry.getKey()))
          getLocDisplay(entry.getKey()).crossOut(entry.getValue());
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
    float q = h * 0.5f;
    float x = mOffsetsX[loc.column.index];
    float y = mOffsetsY[loc.row.index];

    if ((locDisplay.flags & ERROR_BORDER_MASK) != 0) {
      mPaint.setStyle(Style.STROKE);
      mPaint.setColor(ERROR_COLOR);
      canvas.drawRect(x, y, x + s, y + s, mPaint);
    }

    if ((locDisplay.flags & QUESTION_MASK) != 0) {
      mPaint.setStyle(Style.FILL);
      mPaint.setColor(QUESTION_COLOR);
      mPaint.setTextSize(mTextSize * QUESTION_SCALE);
      float a = -mPaint.ascent() * 0.5f;
      canvas.drawText("?", x + q, y + q + a, mPaint);
      canvas.drawText("?", x + h + q, y + q + a, mPaint);
      canvas.drawText("?", x + q, y + h + q + a, mPaint);
      canvas.drawText("?", x + h + q, y + h + q + a, mPaint);
    }

    if ((locDisplay.flags & UNIT_MASK) != 0) {
      mPaint.setStyle(Style.STROKE);
      mPaint.setColor(UNIT_COLOR);
      if (locDisplay.hasUnit(Unit.Type.ROW))
        canvas.drawLine(x, y + h, x + s, y + h, mPaint);
      if (locDisplay.hasUnit(Unit.Type.COLUMN))
        canvas.drawLine(x + h, y, x + h, y + s, mPaint);
      if (locDisplay.hasUnit(Unit.Type.BLOCK))
        canvas.drawRect(x + q, y + q, x + h + q, y + h + q, mPaint);
    }

    if (!locDisplay.crossedOut.isEmpty()) {
      mPaint.setTextSize(mTextSize * CLOCK_SCALE);
      mPaint.setStyle(Style.FILL);
      mPaint.setColor(ELIM_COLOR);
      for (Numeral num : locDisplay.crossedOut) {
        if (locDisplay.crossedOut.size() == 1) {
          mPaint.setColor(OVERLAP_COLOR);
          canvas.drawText(num.toString(), x + mClockX[num.number], y + mClockY[num.number], mPaint);
          mPaint.setColor(ELIM_COLOR);
        }
        canvas.drawText("\u00d7", x + mClockX[num.number], y + mClockY[num.number], mPaint);
      }
    }

    boolean open = isOpen(loc);
    NumSet drawnOverlaps = locDisplay.overlaps.minus(locDisplay.possiblesUnion);
    if (open && !drawnOverlaps.isEmpty()) {
      mPaint.setTextSize(mTextSize * CLOCK_SCALE);
      mPaint.setStyle(Style.FILL);
      mPaint.setColor(OVERLAP_COLOR);
      for (Numeral num : drawnOverlaps) {
        canvas.drawText(num.toString(), x + mClockX[num.number], y + mClockY[num.number], mPaint);
      }
    }

    if (open && !locDisplay.possiblesUnion.isEmpty()) {
      mPaint.setTextSize(mTextSize * ASGMT_SCALE);
      mPaint.setStyle(Style.FILL);
      boolean problem = locDisplay.possibles.isEmpty();
      mPaint.setColor(mConflicts.contains(loc) ? ERROR_COLOR : problem ? QUESTION_COLOR : ASGMT_COLOR);
      StringBuilder sb = new StringBuilder();
      int breakpoint = 0;
      if (problem) {
        for (Numeral num : locDisplay.possiblesUnion) {
          sb.append(num.number).append('?');
        }
        if (sb.length() > 4) breakpoint = sb.length() - 4;
      } else {
        for (Numeral num : locDisplay.possibles) {
          if (sb.length() > 0) sb.append(',');
          sb.append(num.number);
        }
        if (sb.length() > 3) breakpoint = sb.length() - 3;
      }
      int index = sb.length() < 3 ? 0 : breakpoint == 0 ? 1 : 2;
      mPaint.setTextSize(mPossiblesSize[index]);
      if (breakpoint == 0) {
        canvas.drawText(sb.toString(), x + h, y + mToBaseline[index], mPaint);
      } else {
        String line = sb.substring(0, breakpoint);
        canvas.drawText(line, x + h, y + h, mPaint);
        line = sb.substring(breakpoint);
        canvas.drawText(line, x + h, y + h - mPaint.ascent(), mPaint);
      }
    }
  }

  private void drawErrorUnit(Canvas canvas, Unit unit) {
    mPaint.setStyle(Style.STROKE);
    mPaint.setColor(ERROR_COLOR);
    float s = mSquareSize;
    float top = mOffsetsX[unit.get(0).row.index];
    float left = mOffsetsY[unit.get(0).column.index];
    float bottom = s + mOffsetsX[unit.get(9 - 1).row.index];
    float right = s + mOffsetsY[unit.get(9 - 1).column.index];
    canvas.drawRect(left, top, right, bottom, mPaint);
  }

  private void drawSelectable(Canvas canvas, Location loc) {
    if (loc == mSelected) {
      mPaint.setStyle(Style.FILL);
      mPaint.setColor(SELECTION_COLOR);
      float s = mSquareSize;
      float x = mOffsetsX[loc.column.index];
      float y = mOffsetsY[loc.row.index];
      canvas.drawRect(x, y, x + s, y + s, mPaint);
    }
    Integer color = mSelectableColors.apply(loc);
    if (color == null) return;
    mPaint.setStyle(Style.STROKE);
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
          setSelected(loc, true);
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
    public NumSet possibles = NumSet.ALL;
    public NumSet possiblesUnion = NumSet.NONE;

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
      possiblesUnion = possiblesUnion.or(set);
      possibles = possibles.and(set);
    }

    private int unitFlag(Unit.Type type) {
      return 1 << type.ordinal();
    }
  }
}
