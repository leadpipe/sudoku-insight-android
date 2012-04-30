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

import android.os.AsyncTask;
import android.util.Log;

/**
 * Maintains the insights available for the current game, managing background
 * tasks to keep them up to date.
 *
 * @author Luke Blanshard
 */
public class InsightWell implements Analyzer.Callback {

  private final SudokuFragment mFragment;
  private Analyze mCurrentTaskUi;
  private Analyze mCurrentTaskBackground;

  public InsightWell(SudokuFragment fragment) {
    mFragment = fragment;
  }

  /**
   * Refills the well based on the current game state.
   */
  public void refill() {
    if (mCurrentTaskUi != null) mCurrentTaskUi.cancel(true);
    if (mFragment.mAnalyzer != null) {
      mCurrentTaskUi = new Analyze(mFragment.mAnalyzer);
      mCurrentTaskUi.execute();
    }
  }

  // Analyzer.Callback method, called from background thread
  @Override public void phase(Phase phase) {
    mCurrentTaskBackground.phase(phase);
  }

  // Analyzer.Callback method, called from background thread
  @Override public void take(Insight insight) {
    mCurrentTaskBackground.take(insight);
  }

  private class Analyze extends AsyncTask<Void, InsightSum, Void> {
    private final Analyzer mAnalyzer;
    private final InsightSum mInsightSum = new InsightSum();

    Analyze(Analyzer analyzer) {
      mAnalyzer = analyzer;
      analyzer.setAnalysisTargetId(mFragment.mSudokuView.getInputState().getId());
    }

    @Override protected Void doInBackground(Void... params) {
      mCurrentTaskBackground = this;
      try {
        mAnalyzer.analyze();
      } catch (Exception e) {
        Log.e("SudokuInsight", "Analyzer failed", e);
        this.phase(Phase.INTERRUPTED);
      }
      return null;
    }

    @Override protected void onProgressUpdate(InsightSum... values) {
      if (mCurrentTaskUi == this)
        mFragment.setInsights(values[0]);
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
