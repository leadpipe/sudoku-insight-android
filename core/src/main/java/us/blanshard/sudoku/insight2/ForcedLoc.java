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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;

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
    super(Type.FORCED_LOCATION, Objects.hashCode(unit, numeral, location));
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

  @Override public Assignment getAssignment() {
    return Assignment.of(location, numeral);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    ForcedLoc that = (ForcedLoc) o;
    return this.unit.equals(that.unit)
        && this.numeral.equals(that.numeral)
        && this.location.equals(that.location);
  }

  @Override public String toString() {
    return numeral + " \u2208 " + unit + " \u2192 " + location;  // element-of, right-arrow
  }

  @Override protected ImmutableSet<Insight> collectAntecedents(Marks marks) {
    return marks.collectAntecedents(UnitSubset.of(unit, location).not(), numeral.asSet());
  }
}
