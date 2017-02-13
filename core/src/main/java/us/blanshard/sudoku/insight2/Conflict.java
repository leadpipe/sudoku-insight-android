/*
Copyright 2016 Luke Blanshard

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
package us.blanshard.sudoku.insight2;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.UnitSubset;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;

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

  Conflict(Numeral numeral, UnitSubset locations) {
    super(Type.CONFLICT, Objects.hashCode(numeral, locations));
    this.numeral = numeral;
    this.locations = locations;
  }

  public Numeral getNumeral() {
    return numeral;
  }

  public UnitSubset getLocations() {
    return locations;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    Conflict that = (Conflict) o;
    return this.numeral.equals(that.numeral)
        && this.locations.equals(that.locations);
  }

  @Override public String toString() {
    return numeral + " \u2208 " + locations;  // element-of
  }

  @Override protected ImmutableSet<Insight> getAntecedents(Marks marks) {
    ImmutableSet.Builder<Insight> builder = ImmutableSet.builder();
    for (Location loc : locations) {
      builder.add(marks.getAssignmentInsight(Assignment.of(loc, numeral)));
    }
    return builder.build();
  }
}
