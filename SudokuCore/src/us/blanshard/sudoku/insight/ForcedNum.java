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

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Marks;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;

import com.google.common.base.Objects;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.concurrent.Immutable;

/**
 * Describes a situation where there is only one possible numeral for a given
 * location.
 *
 * @author Luke Blanshard
 */
@Immutable
public class ForcedNum extends Insight.Atom {
  private final Location location;
  private final Numeral numeral;

  public ForcedNum(Location location, Numeral numeral) {
    super(Type.FORCED_NUMERAL);
    this.location = location;
    this.numeral = numeral;
  }

  public Location getLocation() {
    return location;
  }

  public Numeral getNumeral() {
    return numeral;
  }

  @Override public Collection<Assignment> getAssignments() {
    return Collections.singleton(Assignment.of(location, numeral));
  }

  @Override public boolean apply(Grid.Builder gridBuilder, Marks.Builder marksBuilder) {
    gridBuilder.put(location, numeral);
    return marksBuilder.assign(location, numeral);
  }

  @Override public boolean isImpliedBy(Grid grid, Marks marks) {
    NumSet set = marks.get(location);
    return set.size() == 1 && set.get(0) == numeral && !grid.containsKey(location);
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    return elimination.location == this.location;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    ForcedNum that = (ForcedNum) o;
    return this.location.equals(that.location)
        && this.numeral.equals(that.numeral);
  }

  @Override public int hashCode() {
    return Objects.hashCode(location, numeral);
  }
}
