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
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

import javax.annotation.concurrent.Immutable;

/**
 * Describes a situation where there is only one possible numeral for a given
 * location.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class ForcedNum extends Insight {
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

  @Override public Assignment getImpliedAssignment() {
    return Assignment.of(location, numeral);
  }

  @Override public void apply(Marks.Builder builder) {
    builder.assign(location, numeral);
  }

  @Override public boolean isImpliedBy(Marks marks) {
    return marks.getSingleton(location) == numeral && !marks.hasAssignment(location);
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
    return ((location.index + 17) << 4)
        | (numeral.index + 7);
  }

  @Override public String toString() {
    return location + " \u2190 " + numeral.number;  // That's a left arrow
  }
}
