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
package us.blanshard.sudoku.insight;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Marks;

/**
 * @author Luke Blanshard
 */
public class GridMarks {
  public final Grid grid;
  public final Marks marks;
  public final boolean hasErrors;

  public GridMarks(Grid grid) {
    this.grid = grid;
    Marks.Builder builder = Marks.builder();
    this.hasErrors = !builder.assignAll(grid);
    this.marks = builder.build();
  }

  GridMarks(Grid grid, Marks marks, boolean hasErrors) {
    this.grid = grid;
    this.marks = marks;
    this.hasErrors = hasErrors;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static class Builder {
    private final Grid.Builder gridBuilder;
    private final Marks.Builder marksBuilder;
    private boolean hasErrors;

    public Builder(GridMarks o) {
      this.gridBuilder = o.grid.toBuilder();
      this.marksBuilder = o.marks.toBuilder();
      this.hasErrors = o.hasErrors;
    }

    public Builder apply(Insight insight) {
      hasErrors |= !insight.apply(gridBuilder, marksBuilder);
      return this;
    }

    public GridMarks build() {
      return new GridMarks(gridBuilder.build(), marksBuilder.build(), hasErrors);
    }
  }
}
