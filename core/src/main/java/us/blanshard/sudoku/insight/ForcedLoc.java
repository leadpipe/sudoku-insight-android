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
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumeral;

import javax.annotation.concurrent.Immutable;

/**
 * Describes a situation where there is only one possible location within a unit
 * for a given numeral.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class ForcedLoc extends Insight {
  private final Unit unit;
  private final Numeral numeral;
  private final Location location;

  public ForcedLoc(Unit unit, Numeral numeral, Location location) {
    super(Type.FORCED_LOCATION);
    this.unit = unit;
    this.numeral = numeral;
    this.location = location;
  }

  public Unit getUnit() {
    return unit;
  }

  public Numeral getNumeral() {
    return numeral;
  }

  public Location getLocation() {
    return location;
  }

  @Override public Assignment getImpliedAssignment() {
    return Assignment.of(location, numeral);
  }

  @Override public void apply(Marks.Builder builder) {
    builder.assign(location, numeral);
  }

  @Override public boolean isImpliedBy(Marks marks) {
    Location loc = marks.getSingleton(UnitNumeral.of(unit, numeral));
    return loc != null && marks.get(loc) == null;
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    return elimination.numeral == this.numeral && this.unit.contains(elimination.location);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    ForcedLoc that = (ForcedLoc) o;
    return this.unit.equals(that.unit)
        && this.numeral.equals(that.numeral)
        && this.location.equals(that.location);
  }

  @Override public int hashCode() {
    return ((unit.index + 5) << 11)
        | ((numeral.index + 7) << 7)
        | (location.index + 17);
  }

  @Override public String toString() {
    return numeral + " \u2208 " + unit + " \u2192 " + location;  // element-of, right-arrow
  }
}
