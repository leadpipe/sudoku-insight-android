package us.blanshard.sudoku.android;

import us.blanshard.sudoku.core.Generator;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Symmetry;
import us.blanshard.sudoku.game.Sudoku;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;

import java.util.Random;

public class SudokuActivity extends Activity {
  private GridWidget mGridWidget;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.board);
    mGridWidget = (GridWidget) findViewById(R.id.gridWidget);

    Object prevGame = getLastNonConfigurationInstance();
    if (prevGame == null) new MakePuzzle().execute(new Random());
    else mGridWidget.setGame((Sudoku) prevGame);
  }

  @Override public Object onRetainNonConfigurationInstance() {
    return mGridWidget.getGame();
  }

  private class MakePuzzle extends AsyncTask<Random, Void, Grid> {

    @Override protected Grid doInBackground(Random... params) {
      return Generator.SUBTRACTIVE_RANDOM.generate(params[0], Symmetry.choosePleasing(params[0]));
    }

    @Override protected void onPostExecute(Grid result) {
      mGridWidget.setGame(new Sudoku(result));
    }

  }
}
