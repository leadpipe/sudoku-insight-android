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

import static us.blanshard.sudoku.core.Numeral.number;
import static us.blanshard.sudoku.core.Numeral.numeral;

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
import java.util.List;

import javax.annotation.Nullable;

public class SudokuView extends View {

  public interface OnMoveListener {
    /** Called when the user has indicated a desire to make the given move. */
    void onMove(Sudoku.State state, Location loc, Numeral num);
  }

  private static final float RADIUS_FACTOR = 0.8f;
  private static final int THIN_LINE_WIDTH = 1;
  private static final int NORMAL_THICK_LINE_WIDTH = 3;
  private static final int SMALL_THICK_LINE_WIDTH = 2;
  private static final int SMALL_SIZE_CUTOFF = 200;

  private static final int INVALID_POINTER_ID = -1; // MotionEvent.INVALID_POINTER_ID;

  private OnMoveListener mOnMoveListener;
  private Sudoku mGame;
  private List<TrailItem> mTrails = ImmutableList.<TrailItem> of();
  private boolean mTrailActive;

  private int mThickLineWidth = NORMAL_THICK_LINE_WIDTH;
  private int mSquareSize;
  private int[] mOffsetsX;
  private int[] mOffsetsY;

  private Sudoku.State mState;
  private int mPointerId = INVALID_POINTER_ID;
  private Location mLocation;
  private int mChoice;
  private Numeral mDefaultChoice;
  private static final float[] TRAIL_X_CENTER = { 0.8f, 0.15f, 0.85f, 0.15f };
  private static final float[] TRAIL_Y_TOP = { 0.5f, 0f, 0f, 0.6f };

  public SudokuView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initWidget();
  }

  public SudokuView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initWidget();
  }

  private void initWidget() {
    setBackgroundColor(Color.WHITE);
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
    mPointerId = INVALID_POINTER_ID;
    mLocation = null;
    setKeepScreenOn(game != null);
    invalidate();
  }

  public void setTrails(List<TrailItem> trails) {
    mTrails = trails;
    invalidate();
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

    mThickLineWidth = size < SMALL_SIZE_CUTOFF ? SMALL_THICK_LINE_WIDTH : NORMAL_THICK_LINE_WIDTH;
    mSquareSize = (size - 4 * mThickLineWidth - 6 * THIN_LINE_WIDTH) / 9;

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

    Paint paint = new Paint();
    paint.setColor(Color.BLACK);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(THIN_LINE_WIDTH);

    for (int i = 0; i < 3; ++i)
      for (int j = 1; j < 3; ++j) {
        int index = 3 * i + j;
        canvas.drawLine(mOffsetsX[0], mOffsetsY[index] - halfThin, mOffsetsX[9], mOffsetsY[index]
            - halfThin, paint);
        canvas.drawLine(mOffsetsX[index] - halfThin, mOffsetsY[0], mOffsetsX[index] - halfThin,
            mOffsetsY[9], paint);
      }

    paint.setStrokeWidth(mThickLineWidth);

    canvas.drawRect(mOffsetsX[0] - halfThick, mOffsetsY[0] - halfThick, mOffsetsX[9] - halfThick,
        mOffsetsY[9] - halfThick, paint);

    for (int i = 1; i < 3; ++i) {
      int index = 3 * i;
      canvas.drawLine(mOffsetsX[0], mOffsetsY[index] - halfThick, mOffsetsX[9], mOffsetsY[index]
          - halfThick, paint);
      canvas.drawLine(mOffsetsX[index] - halfThick, mOffsetsY[0], mOffsetsX[index] - halfThick,
          mOffsetsY[9], paint);
    }

    paint.setAntiAlias(true);
    paint.setStyle(Paint.Style.FILL);
    paint.setTextAlign(Align.CENTER);

    float textSize = mSquareSize * 0.75f;
    paint.setTextSize(textSize);
    float toBaseline = (mSquareSize - paint.getTextSize()) / 2 - paint.ascent() - 1;
    float toCenter = mSquareSize / 2.0f;

    if (mGame != null) {
      for (Location loc : Location.ALL) {
        Numeral num = mGame.getPuzzle().get(loc);
        boolean given = num != null;
        if (given) {
          paint.setTypeface(Typeface.DEFAULT_BOLD);
          paint.setFakeBoldText(true);
        } else if ((num = mGame.getState().get(loc)) != null) {
          paint.setTypeface(Typeface.DEFAULT);
          paint.setFakeBoldText(false);
        }
        float left = mOffsetsX[loc.column.index];
        float top = mOffsetsY[loc.row.index];
        if (num != null) {
          canvas.drawText(num.toString(), left + toCenter, top + toBaseline, paint);
        }
        if (!given && !mTrails.isEmpty()) {
          paint.setFakeBoldText(false);
          for (int i = 0; i < mTrails.size(); ++i) {
            TrailItem item = mTrails.get(i);
            if ((num = item.trail.get(loc)) != null) {
              paint.setTextSize(mSquareSize * (i == 0 ? 0.5f : 0.35f));
              paint.setColor(item.color);
              boolean isTrailhead = loc == item.trail.getTrailhead();
              paint.setTypeface(isTrailhead ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
              paint.setTextSkewX(isTrailhead ? -0.125f : 0);
              canvas.drawText(num.toString(), left + mSquareSize * TRAIL_X_CENTER[i], top
                  + mSquareSize * TRAIL_Y_TOP[i] - paint.ascent(), paint);
            }
          }
          paint.setTextSkewX(0);
          paint.setTextSize(textSize);
          paint.setColor(Color.BLACK);
        }
      }
    }

    if (mLocation != null) {
      float x = mOffsetsX[mLocation.column.index];
      float y = mOffsetsY[mLocation.row.index];
      float cx = x + mSquareSize / 2.0f;
      float cy = y + mSquareSize / 2.0f;
      float r = mSquareSize * RADIUS_FACTOR;
      paint.setColor(Color.argb(0xe0, 0xf0, 0xf0, 0xf0));
      canvas.drawCircle(cx, cy, r, paint);

      paint.setTypeface(Typeface.DEFAULT);
      paint.setFakeBoldText(false);
      int color = Color.BLACK;
      if (mTrailActive && !mTrails.isEmpty())
        color = mTrails.get(0).color;
      paint.setColor(color);

      if (mChoice >= 0) {
        drawChoice(mChoice, canvas, x + toCenter, y + toBaseline, paint);
      }

      paint.setTextSize(Math.max(7, mSquareSize * 0.33f));

      toBaseline = -paint.ascent() / 2.0f;
      float r2 = r - toBaseline;
      for (int i = 0; i <= 9; ++i) {
        double radians = i * Math.PI / 6 - Math.PI / 2;
        x = r2 * (float) Math.cos(radians);
        y = r2 * (float) Math.sin(radians);
        drawChoice(i, canvas, x + cx, y + cy + toBaseline, paint);
      }
    }
  }

  private void drawChoice(int choice, Canvas canvas, float x, float y, Paint paint) {
    String text = choice > 0 ? String.valueOf(choice) : "\u25a1"; // white
                                                                  // square
    canvas.drawText(text, x, y, paint);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        if (mPointerId == INVALID_POINTER_ID && mGame != null) {
          int index = event.getActionIndex();
          Location loc = getLocation(event.getX(index), event.getY(index));
          mState = mGame.getState();
          if (mTrailActive && !mTrails.isEmpty())
            mState = mTrails.get(0).trail;
          if (loc != null && mState.canModify(loc)) {
            mLocation = loc;
            mPointerId = event.getPointerId(index);
            Numeral num = mState.get(mLocation) == null ? mDefaultChoice : mState.get(mLocation);
            mChoice = number(num);
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
            float cx = mOffsetsX[mLocation.column.index] + mSquareSize / 2.0f;
            float cy = mOffsetsY[mLocation.row.index] + mSquareSize / 2.0f;
            float r = mSquareSize * RADIUS_FACTOR;
            double d = Math.hypot(x - cx, y - cy);
            if (d > r * 1.5) {
              choice = -1; // Pull away from center to cancel
            } else if (d > r * 0.5) {
              double radians =
                  x >= cx ? Math.acos((cy - y) / d) : Math.PI + Math.acos((y - cy) / d);
              int num = (int) (radians / Math.PI * 6 + 0.5);
              choice = num == 12 ? 0 : num > 9 ? -1 : num;
            }
          }

          if (choice != mChoice) {
            mChoice = choice;
            invalidateTouchPoint();
          }
        }
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        if (mPointerId == event.getPointerId(event.getActionIndex())) {
          if (mState != null && mChoice >= 0) {
            Numeral num = numeral(mChoice);
            if (num != mState.get(mLocation)) {
              mDefaultChoice = num;
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

  private Location getLocation(float x, float y) {
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
      i = -i - 2; // -i - 1 is the insertion point, so this is the slot the
                  // value is in.
    }
    return i;
  }

  private void invalidateTouchPoint() {
    float cx = mOffsetsX[mLocation.column.index] + mSquareSize / 2.0f;
    float cy = mOffsetsY[mLocation.row.index] + mSquareSize / 2.0f;
    float r = mSquareSize * RADIUS_FACTOR;
    invalidate((int) (cx - r), (int) (cy - r), (int) (cx + r + 1), (int) (cy + r + 1));
  }
}
