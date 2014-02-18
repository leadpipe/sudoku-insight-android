/*
Copyright 2013 Luke Blanshard

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

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Marks;
import us.blanshard.sudoku.core.Numeral;

import java.util.List;

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

    public Builder assign(Assignment assignment) {
      return assign(assignment.location, assignment.numeral);
    }

    public Builder assign(Location loc, Numeral num) {
      gridBuilder.put(loc, num);
      hasErrors |= !marksBuilder.assign(loc, num);
      return this;
    }

    public Builder eliminate(Assignment assignment) {
      return eliminate(assignment.location, assignment.numeral);
    }

    public Builder eliminate(Location loc, Numeral num) {
      hasErrors |= !marksBuilder.eliminate(loc, num);
      return this;
    }

    public Builder apply(Insight insight) {
      insight.apply(this);
      return this;
    }

    public Builder apply(Iterable<Insight> insights) {
      for (Insight insight : insights)
        insight.apply(this);
      return this;
    }

    public Builder apply(List<Insight> insights) {
      for (int i = 0, c = insights.size(); i < c; ++i)
        insights.get(i).apply(this);
      return this;
    }

    public boolean hasErrors() {
      return hasErrors;
    }

    public GridMarks build() {
      return new GridMarks(gridBuilder.build(), marksBuilder.build(), hasErrors);
    }
  }
}
