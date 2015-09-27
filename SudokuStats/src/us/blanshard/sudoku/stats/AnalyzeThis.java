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
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.Marks;

import java.io.IOException;

/**
 * Analyzes one grid, and prints the insights found.  Takes the grid from
 * standard in.
 */
public class AnalyzeThis {
  public static void main(String[] args) throws IOException {
    System.out.println("Enter a grid:");
    Grid grid = Grid.fromString(convertStreamToString(System.in));
    Analyzer.analyze(Marks.fromGrid(grid), new Analyzer.Callback() {
      @Override public void take(Insight insight) throws StopException {
        System.out.println(insight);
      }
    }, new Analyzer.Options(false, false));
  }

  static String convertStreamToString(java.io.InputStream is) {
    @SuppressWarnings("resource")
    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}

/*
Example grids:
 . 6 1 | . . . | 7 . .
 2 . 3 | 7 . 5 | . 6 9
 . 7 5 | 3 . 6 | . . 2
-------+-------+-------
 7 3 2 | 6 5 . | 8 1 4
 5 8 . | . 7 1 | 3 . 6
 6 1 . | . 3 . | 5 2 7
-------+-------+-------
 3 . 7 | 1 . 2 | 6 . 8
 . . 8 | . 6 7 | 2 3 .
 . 2 6 | . . 3 | . 7 .
(why no [3,5] set in upper right? -- now fixed)

 . . . | . 5 . | 2 . .
 . . 2 | . 3 . | . 8 .
 8 9 6 | 2 7 4 | 5 3 1
-------+-------+-------
 . . . | . 1 6 | 3 . 2
 . 7 5 | 3 8 2 | . 4 .
 . 2 3 | 7 4 . | . . 8
-------+-------+-------
 . . . | 1 6 7 | . 2 9
 9 . 1 | . 2 . | 7 6 3
 2 6 7 | . 9 3 | . 1 5
(why no [9 ∈ B4 ∩ R4]? -- now fixed)
*/
