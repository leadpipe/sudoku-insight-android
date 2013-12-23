/*
Copyright 2013 Luke Blanshard

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

import static com.google.common.base.Preconditions.checkArgument;
import static us.blanshard.sudoku.core.Numeral.number;
import static us.blanshard.sudoku.core.Numeral.numeral;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.game.Sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class SudokuView extends View {
  public static final int MAX_VISIBLE_TRAILS = 4;
  public static final int ERROR_COLOR = Color.RED;

  private static final float CLOCK_RADIUS_FACTOR = 0.85f;
  private static final int CLOCK_RADIUS_DP = 60;  // 3/8"
  private static final int THIN_LINE_WIDTH = 1;
  private static final int NORMAL_THICK_LINE_WIDTH = 3;
  private static final int SMALL_THICK_LINE_WIDTH = 2;
  private static final int SMALL_SIZE_CUTOFF = 200;
  private static final int TINY_THICK_LINE_WIDTH = 1;
  private static final int TINY_SIZE_CUTOFF = 150;

  private static final int INVALID_POINTER_ID = -1;  // MotionEvent.INVALID_POINTER_ID;
  private static final long DEBOUNCE_MS = 50;  // A borderline choice won't flip as you lift your finger

  private OnMoveListener mOnMoveListener;
  protected Sudoku mGame;
  private boolean mEditable;
  private boolean mPuzzleEditor;
  private Collection<Location> mBroken;
  private List<TrailItem> mTrails = ImmutableList.<TrailItem> of();
  private boolean mTrailActive;

  protected int mThickLineWidth = NORMAL_THICK_LINE_WIDTH;
  protected int mSquareSize;
  private float mClockRadius;
  protected int[] mOffsetsX;
  protected int[] mOffsetsY;
  protected final Paint mPaint = new Paint();
  protected float mTextSize;

  private Sudoku.State mState;
  private int mPointerId = INVALID_POINTER_ID;
  private Location mLocation;

  private float mPreviewX, mPreviewY, mPreviewX2;
  private float mPointerX, mPointerY;
  private boolean mChanging;

  private int mChoice;
  private int mPreviousChoice;
  private long mChoiceChangeTimestamp;
  private Numeral mDefaultChoice;

  private static final float[] TRAIL_X_CENTER = { 0.2f, 0.85f, 0.15f, 0.85f };
  private static final float[] TRAIL_Y_TOP = { 0f, 0f, 0.6f, 0.6f };

  public SudokuView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SudokuView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public interface OnMoveListener {
    /** Called when the user has indicated a desire to make the given move. */
    void onMove(Sudoku.State state, Location loc, Numeral num);
  }

  public void setOnMoveListener(OnMoveListener onMoveListener) {
    mOnMoveListener = onMoveListener;
  }

  public void invalidateLocation(Location loc) {
    int x = mOffsetsX[loc.column.index];
    int y = mOffsetsY[loc.row.index];
    invalidate(x, y, x + mSquareSize, y + mSquareSize);
  }

  public Sudoku getGame() {
    return mGame;
  }

  public void setGame(Sudoku game) {
    this.mGame = game;
    setEditable(true);
    mBroken = Collections.<Location>emptySet();
    mPointerId = INVALID_POINTER_ID;
    mLocation = null;
    invalidate();
  }

  public void setPuzzle(Grid puzzle) {
    setGame(new Sudoku(puzzle));
    setEditable(false);
  }

  public void setPuzzleEditor(Grid start) {
    Sudoku game = new Sudoku(Grid.BLANK).resume();
    for (Map.Entry<Location, Numeral> entry : start.entrySet()) {
      game.getState().set(entry.getKey(), entry.getValue());
    }
    mPuzzleEditor = true;
    setGame(game);
  }

  public boolean isEditable() {
    return mEditable;
  }

  public void setEditable(boolean editable) {
    mEditable = mGame != null && editable;
  }

  public void setBrokenLocations(Collection<Location> broken) {
    mBroken = broken;
    invalidate();
  }

  public void setTrails(List<TrailItem> trails) {
    checkArgument(trails.size() <= MAX_VISIBLE_TRAILS);
    mTrails = ImmutableList.copyOf(trails);
    invalidate();
  }

  public boolean isTrailActive() {
    return mTrailActive;
  }

  public void setTrailActive(boolean flag) {
    mTrailActive = flag;
    invalidate();
  }

  @Nullable public Numeral getDefaultChoice() {
    return mDefaultChoice;
  }

  public void setDefaultChoice(@Nullable Numeral num) {
    mDefaultChoice = num;
  }

  public Sudoku.State getInputState() {
    if (mGame == null) return null;
    if (mTrailActive && !mTrails.isEmpty())
      return mTrails.get(0).trail;
    return mGame.getState();
  }

  public int getInputColor() {
    if (mTrailActive && !mTrails.isEmpty())
      return mTrails.get(0).color;
    return Color.BLACK;
  }

  public boolean hasGridDimensions() {
    return mOffsetsX != null;
  }

  public int getGridWidth() {
    return mOffsetsX[9] - mOffsetsX[0];
  }

  public int getGridHeight() {
    return mOffsetsY[9] - mOffsetsY[0];
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int wMode = MeasureSpec.getMode(widthMeasureSpec);
    int wSize = MeasureSpec.getSize(widthMeasureSpec);
    int hMode = MeasureSpec.getMode(heightMeasureSpec);
    int hSize = MeasureSpec.getSize(heightMeasureSpec);

    int wExtra = getPaddingLeft() + getPaddingRight();
    int hExtra = getPaddingTop() + getPaddingBottom();

    int size = Math.min(wSize - wExtra, hSize - hExtra);

    int width = wMode == MeasureSpec.EXACTLY ? wSize : size + wExtra;
    int height = hMode == MeasureSpec.EXACTLY ? hSize : size + hExtra;

    setMeasuredDimension(width, height);

    mThickLineWidth = size < TINY_SIZE_CUTOFF ? TINY_THICK_LINE_WIDTH
        : size < SMALL_SIZE_CUTOFF ? SMALL_THICK_LINE_WIDTH
        : NORMAL_THICK_LINE_WIDTH;
    mSquareSize = (size - 4 * mThickLineWidth - 6 * THIN_LINE_WIDTH) / 9;
    mTextSize = mSquareSize * (mThickLineWidth > THIN_LINE_WIDTH ? 0.75f : 0.85f);
    mClockRadius = Math.max(mSquareSize * CLOCK_RADIUS_FACTOR,
        getResources().getDisplayMetrics().density * CLOCK_RADIUS_DP);

    int sizePlus = THIN_LINE_WIDTH + mSquareSize;
    int blockSize = mThickLineWidth + 2 * sizePlus + mSquareSize;

    int total = mThickLineWidth + 3 * blockSize;
    int extra = (size - total) / 2;
    int xOffset = getPaddingLeft() + extra;
    int yOffset = getPaddingTop() + extra;
    mOffsetsX = new int[10];
    mOffsetsY = new int[10];
    for (int i = 0; i < 3; ++i)
      for (int j = 0; j < 3; ++j) {
        mOffsetsX[3 * i + j] = xOffset + mThickLineWidth + i * blockSize + j * sizePlus;
        mOffsetsY[3 * i + j] = yOffset + mThickLineWidth + i * blockSize + j * sizePlus;
      }
    mOffsetsX[9] = xOffset + total;
    mOffsetsY[9] = yOffset + total;
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int halfThick = (mThickLineWidth + 1) / 2;
    int halfThin = (THIN_LINE_WIDTH + 1) / 2;

    mPaint.reset();
    mPaint.setColor(mThickLineWidth > THIN_LINE_WIDTH ? Color.BLACK : Color.LTGRAY);
    mPaint.setStyle(Paint.Style.STROKE);
    mPaint.setStrokeWidth(THIN_LINE_WIDTH);

    for (int i = 0; i < 3; ++i)
      for (int j = 1; j < 3; ++j) {
        int index = 3 * i + j;
        canvas.drawLine(mOffsetsX[0], mOffsetsY[index] - halfThin, mOffsetsX[9], mOffsetsY[index]
            - halfThin, mPaint);
        canvas.drawLine(mOffsetsX[index] - halfThin, mOffsetsY[0], mOffsetsX[index] - halfThin,
            mOffsetsY[9], mPaint);
      }

    mPaint.setColor(Color.BLACK);
    mPaint.setStrokeWidth(mThickLineWidth);

    canvas.drawRect(mOffsetsX[0] - halfThick, mOffsetsY[0] - halfThick, mOffsetsX[9] - halfThick,
        mOffsetsY[9] - halfThick, mPaint);

    for (int i = 1; i < 3; ++i) {
      int index = 3 * i;
      canvas.drawLine(mOffsetsX[0], mOffsetsY[index] - halfThick, mOffsetsX[9], mOffsetsY[index]
          - halfThick, mPaint);
      canvas.drawLine(mOffsetsX[index] - halfThick, mOffsetsY[0], mOffsetsX[index] - halfThick,
          mOffsetsY[9], mPaint);
    }

    mPaint.setAntiAlias(true);
    mPaint.setStyle(Paint.Style.FILL);
    mPaint.setTextAlign(Align.CENTER);

    mPaint.setTextSize(mTextSize);
    float toBaseline = calcToBaseline();
    float half = mSquareSize * 0.5f;
    float toCenter = half;

    if (mGame != null) {
      for (Location loc : Location.all()) {
        Numeral num = mPuzzleEditor ? mGame.getState().get(loc) : mGame.getPuzzle().get(loc);
        boolean given = num != null;
        if (given) {
          mPaint.setTypeface(Typeface.DEFAULT_BOLD);
          mPaint.setFakeBoldText(true);
        } else if ((num = mGame.getState().get(loc)) != null) {
          mPaint.setTypeface(Typeface.DEFAULT);
          mPaint.setFakeBoldText(false);
        }
        boolean broken = mBroken.contains(loc);
        mPaint.setColor(broken ? ERROR_COLOR : given || !mTrailActive ? Color.BLACK : Color.GRAY);
        float left = mOffsetsX[loc.column.index];
        float top = mOffsetsY[loc.row.index];
        if (num != null) {
          canvas.drawText(num.toString(), left + toCenter, top + toBaseline, mPaint);
        }
        if (!given && !mTrails.isEmpty()) {
          mPaint.setFakeBoldText(false);
          for (int i = 0; i < mTrails.size(); ++i) {
            TrailItem item = mTrails.get(i);
            if ((num = item.trail.get(loc)) != null) {
              mPaint.setTextSize(mSquareSize * (i == 0 ? 0.5f : 0.4f));
              mPaint.setColor(broken ? ERROR_COLOR : i == 0 && mTrailActive ? item.color : item.dimColor);
              boolean isTrailhead = loc == item.trail.getTrailhead();
              mPaint.setTypeface(isTrailhead ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
              mPaint.setTextSkewX(isTrailhead ? -0.125f : 0);
              canvas.drawText(num.toString(), left + mSquareSize * TRAIL_X_CENTER[i], top
                  + mSquareSize * TRAIL_Y_TOP[i] - mPaint.ascent(), mPaint);
            }
          }
          mPaint.setTextSkewX(0);
          mPaint.setTextSize(mTextSize);
          mPaint.setColor(Color.BLACK);
        }
      }
    }

    if (mLocation != null) {
      float x = mOffsetsX[mLocation.column.index];
      float y = mOffsetsY[mLocation.row.index];
      float cx = mPointerX;
      float cy = mPointerY;
      float r = mClockRadius;
      mPaint.setColor(Color.argb(0xe0, 0xf0, 0xf0, 0xf0));
      canvas.drawCircle(cx, cy, r, mPaint);
      canvas.drawCircle(mPreviewX, mPreviewY, half, mPaint);
      if (mPreviewX2 > mPreviewX)
        canvas.drawCircle(mPreviewX2, mPreviewY, half, mPaint);
      mPaint.setColor(Color.argb(0xe0, 0xe0, 0xe0, 0xe0));
      canvas.drawRect(x, y, x + mSquareSize, y + mSquareSize, mPaint);

      mPaint.setTypeface(Typeface.DEFAULT);
      mPaint.setFakeBoldText(mPuzzleEditor);
      int color = getInputColor();
      mPaint.setColor(color);

      if (mChoice >= 0) {
        drawChoice(mChoice, canvas, x + toCenter, y + toBaseline);
        drawChoice(mChoice, canvas, mPreviewX, mPreviewY - half + toBaseline);
        if (mPreviewX2 > mPreviewX)
          drawChoice(mChoice, canvas, mPreviewX2, mPreviewY - half + toBaseline);
      }

      mPaint.setTextSize(Math.max(7, mSquareSize * 0.33f));

      toBaseline = -mPaint.ascent() / 2.0f;
      double r2 = r - toBaseline;
      for (int i = 0; i <= 9; ++i) {
        float radians = calcRadians(i);
        x = (float) (r2 * Math.cos(radians));
        y = (float) (r2 * Math.sin(radians));
        drawChoice(i, canvas, x + cx, y + cy + toBaseline);
      }
    }
  }

  protected float calcRadians(int num) {
    return (float) (num * Math.PI / 6 - Math.PI / 2);
  }

  protected float calcToBaseline() {
    return (mSquareSize - mPaint.getTextSize()) / 2 - mPaint.ascent() - 1;
  }

  protected void drawChoice(int choice, Canvas canvas, float x, float y) {
    String text = choice > 0 ? String.valueOf(choice) : "\u25a1";  // white square
    canvas.drawText(text, x, y, mPaint);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    if (!mEditable) return super.onTouchEvent(event);
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        if (mPointerId == INVALID_POINTER_ID) {
          int index = event.getActionIndex();
          float x = event.getX(index), y = event.getY(index);
          Location loc = getLocation(x, y);
          mState = getInputState();
          if (loc != null && mState.canModify(loc)) {
            mLocation = loc;
            mPointerId = event.getPointerId(index);
            mPointerX = x;
            mPointerY = y;
            mChanging = false;
            float half = mSquareSize * 0.5f;
            float cx = mOffsetsX[loc.column.index] + half;
            float cy = mOffsetsY[loc.row.index] + half;
            if (cy > mClockRadius + mSquareSize) {
              mPreviewX2 = mPreviewX = cx;
              mPreviewY = cy - mClockRadius - half;
            } else {
              mPreviewY = half;
              float h = mClockRadius + half;
              y = cy - half;
              x = (float) Math.sqrt(h*h - y*y);
              if (x < half) {
                x = half;
                y = (float) Math.sqrt(h*h - x*x);
                mPreviewY = cy - y;
              }
              mPreviewX = cx - x;
              mPreviewX2 = cx + x;
            }

            Numeral num = mState.get(mLocation);
            mChoice = number(num == null ? mDefaultChoice : num);
            mPreviousChoice = mChoice;
            mChoiceChangeTimestamp = event.getEventTime();
            invalidateTouchPoint();
          }
        }
        break;

      case MotionEvent.ACTION_CANCEL:
        if (mPointerId != INVALID_POINTER_ID) {
          invalidateTouchPoint();
          mPointerId = INVALID_POINTER_ID;
          mLocation = null;
        }
        break;

      case MotionEvent.ACTION_MOVE:
      case MotionEvent.ACTION_OUTSIDE:
        if (mPointerId != INVALID_POINTER_ID) {
          int index = event.findPointerIndex(mPointerId);
          int choice = mChoice;
          if (index >= 0) {
            float x = event.getX(index);
            float y = event.getY(index);
            float cx = mPointerX;
            float cy = mPointerY;
            float r = mClockRadius;
            double d = Math.hypot(x - cx, y - cy);
            if (d > r * 3) {
              choice = -1;  // Pull away from center to cancel
            } else if (mChanging || d > mSquareSize * 0.5) {
              // Don't change anything until there's some perceptible movement
              mChanging = true;
              double radians =
                x >= cx ? Math.acos((cy - y) / d) : Math.PI + Math.acos((y - cy) / d);
              int num = (int) (radians / Math.PI * 6 + 0.5);
              choice = num == 12 ? 0 : num > 9 ? -1 : num;
            }
          }

          if (choice != mChoice) {
            mPreviousChoice = mChoice;
            mChoice = choice;
            mChoiceChangeTimestamp = event.getEventTime();
            invalidateTouchPoint();
          }
        }
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        if (mPointerId == event.getPointerId(event.getActionIndex())) {
          if (mChoice != mPreviousChoice && event.getEventTime() - mChoiceChangeTimestamp < DEBOUNCE_MS)
            mChoice = mPreviousChoice;
          if (mState != null && mChoice >= 0) {
            Numeral num = numeral(mChoice);
            if (num != mState.get(mLocation)) {
              if (num != null) {
                mDefaultChoice = num;
              }
              if (mOnMoveListener != null) {
                mOnMoveListener.onMove(mState, mLocation, num);
              }
            }
          }
          invalidateTouchPoint();
          mPointerId = INVALID_POINTER_ID;
          mLocation = null;
        }
        break;
    }
    return true;
  }

  protected Location getLocation(float x, float y) {
    int xi = findGridIndex(x, mOffsetsX);
    int yi = findGridIndex(y, mOffsetsY);
    if (xi < 0 || yi < 0 || xi >= 9 || yi >= 9)
      return null;
    // Ignore touches too close to the edges of squares.
    if (x < mOffsetsX[xi] + 2 || x > mOffsetsX[xi] + mSquareSize - 2)
      return null;
    if (y < mOffsetsY[yi] + 2 || y > mOffsetsY[yi] + mSquareSize - 2)
      return null;

    return Location.ofIndices(yi, xi);
  }

  private int findGridIndex(float value, int[] offsets) {
    int i = Arrays.binarySearch(offsets, (int) value);
    if (i < 0) {
      i = -i - 2;  // -i - 1 is the insertion point, so this is the slot the value is in.
    }
    return i;
  }

  private void invalidateTouchPoint() {
    invalidateCircle(mPointerX, mPointerY, mClockRadius);
    int x = mOffsetsX[mLocation.column.index];
    int y = mOffsetsY[mLocation.row.index];
    invalidate(x, y, x + mSquareSize, y + mSquareSize);
    invalidateCircle(mPreviewX, mPreviewY, mSquareSize * 0.5f);
    if (mPreviewX2 > mPreviewX) {
      invalidateCircle(mPreviewX2, mPreviewY, mSquareSize * 0.5f);
    }
  }

  private void invalidateCircle(float cx, float cy, float r) {
    invalidate((int) (cx - r), (int) (cy - r), (int) (cx + r + 1), (int) (cy + r + 1));
  }
}
