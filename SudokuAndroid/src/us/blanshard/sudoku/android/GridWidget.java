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
import android.view.View;

import java.util.Map;

public class GridWidget extends View {

  private static final int THIN_LINE_WIDTH = 1;
  private static final int NORMAL_THICK_LINE_WIDTH = 3;
  private static final int SMALL_THICK_LINE_WIDTH = 2;
  private static final int SMALL_SIZE_CUTOFF = 200;

  private int mThickLineWidth = NORMAL_THICK_LINE_WIDTH;
  private Sudoku mGame;

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
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int width = getWidth() - getPaddingLeft() - getPaddingRight();
    int height = getHeight() - getPaddingTop() - getPaddingBottom();

    int squareSize = (Math.min(width, height)
                      - 4 * mThickLineWidth
                      - 6 * THIN_LINE_WIDTH) / 9;
    float sizePlus = THIN_LINE_WIDTH + squareSize;
    float blockSize = mThickLineWidth + 2 * sizePlus + squareSize;
    float start = mThickLineWidth;
    float end = 3 * blockSize;

    float total = start + end;

    canvas.translate(getPaddingLeft() + (width - total) / 2.0f,
                     getPaddingTop() + (height - total) / 2.0f);

    Paint paint = new Paint();
    paint.setColor(Color.BLACK);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(THIN_LINE_WIDTH);

    for (int i = 0; i < 3; ++i)
      for (int j = 1; j < 3; ++j) {
        float level = start + i * blockSize + j * sizePlus;
        canvas.drawLine(start, level, end, level, paint);
        canvas.drawLine(level, start, level, end, paint);
      }

    paint.setStrokeWidth(mThickLineWidth);

    canvas.drawRect(start, start, end, end, paint);

    for (int i = 1; i < 3; ++i) {
      float level = start + i * blockSize;
      canvas.drawLine(start, level, end, level, paint);
      canvas.drawLine(level, start, level, end, paint);
    }

    if (mGame != null) {
      paint.setTypeface(Typeface.DEFAULT_BOLD);
      paint.setAntiAlias(true);
      paint.setStyle(Paint.Style.FILL);
      paint.setTextSize(squareSize * 0.75f);
      paint.setTextAlign(Align.CENTER);
      paint.setFakeBoldText(true);
      float toBaseline = (squareSize - paint.getTextSize()) / 2 - paint.ascent();
      float toCenter = squareSize / 2.0f;

      for (Map.Entry<Location, Numeral> entry : mGame.getPuzzle().entrySet()) {
        Location loc = entry.getKey();
        int br = loc.block.rowIndex(), bc = loc.block.columnIndex();
        int rb = loc.row.index - 3 * br, cb = loc.column.index - 3 * bc;
        float top = start + br * blockSize + rb * sizePlus;
        float left = start + bc * blockSize + cb * sizePlus;
        canvas.drawText(entry.getValue().toString(), left + toCenter, top + toBaseline, paint);
      }
    }
  }

}
