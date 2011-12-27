package us.blanshard.sudoku.android;

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

import java.util.Arrays;
import java.util.Map;

public class GridWidget extends View {

  private static final float RADIUS_FACTOR = 0.65f;
  private static final int THIN_LINE_WIDTH = 1;
  private static final int NORMAL_THICK_LINE_WIDTH = 3;
  private static final int SMALL_THICK_LINE_WIDTH = 2;
  private static final int SMALL_SIZE_CUTOFF = 200;

  private static final int INVALID_POINTER_ID = -1; // MotionEvent.INVALID_POINTER_ID;

  private Sudoku mGame;

  private int mThickLineWidth = NORMAL_THICK_LINE_WIDTH;
  private int mSquareSize;
  private int[] mOffsetsX;
  private int[] mOffsetsY;

  private int mPointerId = INVALID_POINTER_ID;
  private Location mLocation;

  public GridWidget(Context context, AttributeSet attrs) {
    super(context, attrs);
    initWidget();
  }

  public GridWidget(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initWidget();
  }

  private void initWidget() {
    setBackgroundColor(Color.WHITE);
  }

  public Sudoku getGame() {
    return mGame;
  }

  public void setGame(Sudoku game) {
    this.mGame = game;
    invalidate();
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
        canvas.drawLine(mOffsetsX[0], mOffsetsY[index] - halfThin,
                        mOffsetsX[9], mOffsetsY[index] - halfThin, paint);
        canvas.drawLine(mOffsetsX[index] - halfThin, mOffsetsY[0],
                        mOffsetsX[index] - halfThin, mOffsetsY[9], paint);
      }

    paint.setStrokeWidth(mThickLineWidth);

    canvas.drawRect(mOffsetsX[0] - halfThick, mOffsetsY[0] - halfThick,
                    mOffsetsX[9] - halfThick, mOffsetsY[9] - halfThick, paint);

    for (int i = 1; i < 3; ++i) {
      int index = 3 * i;
      canvas.drawLine(mOffsetsX[0], mOffsetsY[index] - halfThick,
                      mOffsetsX[9], mOffsetsY[index] - halfThick, paint);
      canvas.drawLine(mOffsetsX[index] - halfThick, mOffsetsY[0],
                      mOffsetsX[index] - halfThick, mOffsetsY[9], paint);
    }

    paint.setAntiAlias(true);
    paint.setStyle(Paint.Style.FILL);

    if (mGame != null) {
      paint.setTypeface(Typeface.DEFAULT_BOLD);
      paint.setTextSize(mSquareSize * 0.75f);
      paint.setTextAlign(Align.CENTER);
      paint.setFakeBoldText(true);
      float toBaseline = (mSquareSize - paint.getTextSize()) / 2 - paint.ascent() - 1;
      float toCenter = mSquareSize / 2.0f;

      for (Map.Entry<Location, Numeral> entry : mGame.getPuzzle().entrySet()) {
        Location loc = entry.getKey();
        float left = mOffsetsX[loc.column.index];
        float top = mOffsetsY[loc.row.index];
        canvas.drawText(entry.getValue().toString(), left + toCenter, top + toBaseline, paint);
      }
    }

    if (mLocation != null) {
      float cx = mOffsetsX[mLocation.column.index] + mSquareSize / 2.0f;
      float cy = mOffsetsY[mLocation.row.index] + mSquareSize / 2.0f;
      float r = mSquareSize * RADIUS_FACTOR;
      paint.setColor(Color.argb(128, 180, 180, 180));
      canvas.drawCircle(cx, cy, r, paint);
    }
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        if (mPointerId == INVALID_POINTER_ID) {
          int index = event.getActionIndex();
          mLocation = getLocation(event.getX(index), event.getY(index));
          if (mLocation != null) {
            mPointerId = event.getPointerId(index);
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
          // light up a different sector?
          // invalidate region?
        }
        break;

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        if (mPointerId == event.getPointerId(event.getActionIndex())) {
          // set value
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
    if (xi < 0 || yi < 0 || xi >= 9 || yi >= 9) return null;
    // TODO(leadpipe): probably want to ignore values too close to edges.
    // Also ignore locations containing the givens?
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
    float cx = mOffsetsX[mLocation.column.index] + mSquareSize / 2.0f;
    float cy = mOffsetsY[mLocation.row.index] + mSquareSize / 2.0f;
    float r = mSquareSize * RADIUS_FACTOR;
    invalidate((int) (cx - r), (int) (cy - r), (int) (cx + r + 1), (int) (cy + r + 1));
  }
}
