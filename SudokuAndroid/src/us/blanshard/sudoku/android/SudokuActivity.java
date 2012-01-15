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

import roboguice.activity.RoboFragmentActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectFragment;

import us.blanshard.sudoku.core.Generator;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Symmetry;
import us.blanshard.sudoku.game.Sudoku;

import android.os.AsyncTask;
import android.os.Bundle;

import java.util.Random;

import javax.inject.Inject;

@ContentView(R.layout.main)
public class SudokuActivity extends RoboFragmentActivity {
  @InjectFragment(R.id.board_fragment) SudokuFragment mFragment;
  @Inject Sudoku.Registry mRegistry;

  // Public methods

  public void setPuzzle(Grid puzzle) {
    mFragment.setGame(new Sudoku(puzzle, mRegistry));
  }

  // Activity overrides

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    new MakePuzzle().execute(new Random());
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
