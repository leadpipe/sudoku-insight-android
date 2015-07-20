/*
Copyright 2015 Luke Blanshard

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
package us.blanshard.sudoku.stats;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Analyzer.StopException;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Insight;

import java.io.IOException;

/**
 * Analyzes one grid, and prints the insights found.  Takes the grid from
 * standard in.
 */
public class AnalyzeThis {
  public static void main(String[] args) throws IOException {
    Grid grid = Grid.fromString(convertStreamToString(System.in));
    Analyzer.analyze(new GridMarks(grid), new Analyzer.Callback() {
      @Override public void take(Insight insight) throws StopException {
        System.out.println(insight);
      }
    });
  }

  static String convertStreamToString(java.io.InputStream is) {
    @SuppressWarnings("resource")
    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
