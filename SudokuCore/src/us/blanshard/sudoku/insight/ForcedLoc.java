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
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;

/**
 * Describes a situation where there is only one possible location within a unit
 * for a given numeral.
 *
 * @author Luke Blanshard
 */
public class ForcedLoc extends Insight.Atom {
  private final Unit unit;
  private final Numeral numeral;
  private final Location location;

  public ForcedLoc(Grid grid, Unit unit, Numeral numeral, Location location) {
    super(grid, getPattern(grid, unit, numeral, location));
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
}
