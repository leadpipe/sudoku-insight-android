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
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;

import com.google.common.base.Objects;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.concurrent.Immutable;

/**
 * Describes a situation where there is only one possible location within a unit
 * for a given numeral.
 *
 * @author Luke Blanshard
 */
@Immutable
public class ForcedLoc extends Insight.Atom {
  private final Unit unit;
  private final Numeral numeral;
  private final Location location;

  public ForcedLoc(Grid grid, Unit unit, Numeral numeral, Location location) {
    super(getPattern(grid, unit, numeral, location));
    this.unit = unit;
    this.numeral = numeral;
    this.location = location;
  }

  /** Chooses the appropriate pattern, LastLoc or ForcedLoc. */
  private static Pattern getPattern(Grid grid, Unit unit, Numeral numeral, Location location) {
    for (Location loc : unit)
      if (loc != location && !grid.containsKey(loc))
        return Pattern.forcedLocation(grid, unit, numeral);
    return Pattern.lastLocation(unit);
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

  @Override public Collection<Assignment> getAssignments() {
    return Collections.singleton(Assignment.of(location, numeral));
  }

  @Override public boolean apply(Grid.Builder gridBuilder, Marks.Builder marksBuilder) {
    gridBuilder.put(location, numeral);
    return marksBuilder.assign(location, numeral);
  }

  @Override public boolean isImpliedBy(Grid grid, Marks marks) {
    UnitSubset set = marks.get(unit, numeral);
    return set.size() == 1 && set.get(0) == location && !grid.containsKey(location);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    ForcedLoc that = (ForcedLoc) o;
    return this.pattern.equals(that.pattern)
        && this.unit.equals(that.unit)
        && this.numeral.equals(that.numeral)
        && this.location.equals(that.location);
  }

  @Override public int hashCode() {
    return Objects.hashCode(pattern, unit, numeral, location);
  }
}
