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
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.UnitSubset;

import com.google.common.base.Objects;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * Describes an actual conflict on a Sudoku board: a set of locations within a
 * unit that are all assigned the same numeral.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class Conflict extends Insight {
  private final Numeral numeral;
  private final UnitSubset locations;

  Conflict(Grid grid, Numeral numeral, UnitSubset locations) {
    super(Type.CONFLICT);
    this.numeral = numeral;
    this.locations = locations;
  }

  public Numeral getNumeral() {
    return numeral;
  }

  public UnitSubset getLocations() {
    return locations;
  }

  @Override public void apply(GridMarks.Builder builder) {
    // Nothing to do, it's an error.
  }

  @Override public boolean isImpliedBy(GridMarks gridMarks) {
    for (Location loc : locations) {
      if (gridMarks.grid.get(loc) != numeral)
        return false;
    }
    return true;
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    return false;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    Conflict that = (Conflict) o;
    return this.numeral.equals(that.numeral)
        && this.locations.equals(that.locations);
  }

  @Override public int hashCode() {
    return Objects.hashCode(numeral, locations);
  }

  @Override public String toString() {
    return numeral + " \u2208 " + locations.unit + " \u219b " + locations;  // element-of, right-arrow
  }

  @Override public void addScanTargets(Collection<Location> locs, Collection<UnitNumeral> unitNums) {
    // Conflicts are not about scanning open locations.
  }
}
