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

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * Holds a location that is prevented by the rules of the game from being
 * assigned any numeral.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class BarredLoc extends Insight {
  private final Location location;

  BarredLoc(Location location) {
    super(Type.BARRED_LOCATION);
    this.location = location;
  }

  public Location getLocation() {
    return location;
  }

  @Override public void apply(GridMarks.Builder builder) {
    // Nothing to do, it's an error.
  }

  @Override public boolean isImpliedBy(GridMarks gridMarks) {
    return gridMarks.marks.get(location).isEmpty();
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    return elimination.location == this.location;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    BarredLoc that = (BarredLoc) o;
    return this.location.equals(that.location);
  }

  @Override public int hashCode() {
    return location.hashCode();
  }

  @Override public String toString() {
    return location + " \u2190 \u2205";  // assignment empty-set
  }

  @Override public void addScanTargets(Collection<Location> locs, Collection<UnitNumeral> unitNums) {
    locs.add(location);
  }
}
