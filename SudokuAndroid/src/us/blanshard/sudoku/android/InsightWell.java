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

import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Analyzer.Phase;
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.InsightSum;

import android.util.Log;

/**
 * Maintains the insights available for the current game, managing background
 * tasks to keep them up to date.
 *
 * @author Luke Blanshard
 */
public class InsightWell implements Analyzer.Callback {

  private Analyzer mAnalyzer;
  private Analyze mCurrentTask;

  /**
   * Refills the well based on the current game state.
   */
  public void refill(SudokuFragment fragment) {
    if (mCurrentTask != null) mCurrentTask.cancel();
    if (mAnalyzer == null || mAnalyzer.getGame() != fragment.getGame()) {
      mAnalyzer = new Analyzer(fragment.getGame(), this);
    }
    mCurrentTask = new Analyze(fragment);
    mCurrentTask.execute();
  }

  // Analyzer.Callback method, called from background thread
  @Override public synchronized void phase(Phase phase) {
    mCurrentTask.phase(phase);
  }

  // Analyzer.Callback method, called from background thread
  @Override public synchronized void take(Insight insight) {
    mCurrentTask.take(insight);
  }

  private class Analyze extends WorkerFragment.Task<SudokuFragment, Void, InsightSum, Void> {
    private final Analyzer mAnalyzer;
    private final InsightSum mInsightSum = new InsightSum();

    Analyze(SudokuFragment fragment) {
      super(fragment);
      mAnalyzer = InsightWell.this.mAnalyzer;
      mAnalyzer.setAnalysisTargetId(fragment.mSudokuView.getInputState().getId());
    }

    @Override protected Void doInBackground(Void... params) {
      try {
        mAnalyzer.analyze();
      } catch (Exception e) {
        Log.e("InsightWell", "Analyzer failed", e);
        this.phase(Phase.INTERRUPTED);
      }
      return null;
    }

    @Override protected void onProgressUpdate(SudokuFragment fragment, InsightSum... values) {
      fragment.setInsights(values[0]);
    }

    void phase(Phase phase) {
      switch (phase) {
        case COMPLETE:
        case INTERRUPTED:
          mInsightSum.setComplete();
          break;
      }
      publishProgress(mInsightSum.clone());
    }

    void take(Insight insight) {
      mInsightSum.append(insight);
    }
  }
}
