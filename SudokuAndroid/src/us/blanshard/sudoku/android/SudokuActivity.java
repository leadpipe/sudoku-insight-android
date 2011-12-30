package us.blanshard.sudoku.android;

import roboguice.activity.RoboActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;

import us.blanshard.sudoku.core.Generator;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Symmetry;
import us.blanshard.sudoku.game.Command;
import us.blanshard.sudoku.game.CommandException;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.UndoStack;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import java.util.Random;

@ContentView(R.layout.board)
public class SudokuActivity extends RoboActivity implements OnClickListener {
  private UndoStack mUndoStack = new UndoStack();
  private Sudoku mGame;
  private Sudoku.Registry mRegistry = Sudoku.newRegistry();
  @InjectView(R.id.gridWidget) GridWidget mGridWidget;
  @InjectView(R.id.undo) Button mUndoButton;
  @InjectView(R.id.redo) Button mRedoButton;

  // Public methods

  public void doCommand(Command command) {
    try {
      mUndoStack.doCommand(command);
      updateButtonStates();
    } catch (CommandException e) {
      showError(e.getMessage());
    }
  }

  public void setPuzzle(Grid puzzle) {
    setGame(new Sudoku(puzzle, mRegistry));
  }

  public Sudoku.Registry getRegistry() {
    return mRegistry;
  }

  public Sudoku getGame() {
    return mGame;
  }

  public void setGame(Sudoku game) {
    mGame = game;
    mGridWidget.setGame(game);
  }

  public void showError(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG);
  }

  // OnClickListener

  @Override public void onClick(View v) {
    if (v == mUndoButton) {
      try {
        mUndoStack.undo();
        updateButtonStates();
      } catch (CommandException e) {
        showError(e.getMessage());
      }
    } else if (v == mRedoButton) {
      try {
        mUndoStack.redo();
        updateButtonStates();
      } catch (CommandException e) {
        showError(e.getMessage());
      }
    } else
      showError("Unrecognized click view " + v);
  }

  // Activity overrides

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mUndoButton.setOnClickListener(this);
    mRedoButton.setOnClickListener(this);
    updateButtonStates();

    mGridWidget.setActivity(this);

    new MakePuzzle().execute(new Random());
  }

  // Private methods

  private void updateButtonStates() {
    mUndoButton.setEnabled(mUndoStack.canUndo());
    mRedoButton.setEnabled(mUndoStack.canRedo());
  }

  private class MakePuzzle extends AsyncTask<Random, Void, Grid> {

    @Override protected Grid doInBackground(Random... params) {
      return Generator.SUBTRACTIVE_RANDOM.generate(params[0], Symmetry.choosePleasing(params[0]));
    }

    @Override protected void onPostExecute(Grid result) {
      setPuzzle(result);
    }
  }
}
