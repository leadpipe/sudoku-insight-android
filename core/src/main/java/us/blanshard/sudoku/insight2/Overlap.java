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
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * Describes an overlap, where the only locations within one unit that a numeral
 * may inhabit also lie within an overlapping unit.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class Overlap extends Insight {
  private final Unit unit;
  private final Numeral numeral;
  private final UnitSubset eliminatedLocations;
  private volatile List<Assignment> eliminations;

  public Overlap(Unit unit, Numeral numeral, UnitSubset eliminatedLocations) {
    super(Type.OVERLAP, Objects.hashCode(unit, numeral, eliminatedLocations.unit));
    this.unit = unit;
    this.numeral = numeral;
    this.eliminatedLocations = eliminatedLocations;
  }

  @Override public List<Assignment> getEliminations() {
    List<Assignment> answer = eliminations;
    if (answer == null) {
      synchronized (this) {
        if ((answer = eliminations) == null) {
          ImmutableList.Builder<Assignment> builder = ImmutableList.builder();
          for (Location loc : eliminatedLocations)
            builder.add(Assignment.of(loc, numeral));
          eliminations = answer = builder.build();
        }
      }
    }
    return answer;
  }

  public Unit getUnit() {
    return unit;
  }

  public Numeral getNumeral() {
    return numeral;
  }

  public Unit getOverlappingUnit() {
    return eliminatedLocations.unit;
  }

  public UnitSubset getEliminatedLocations() {
    return eliminatedLocations;
  }

  public String toBasicString() {
    return unit + ":" + numeral + ":" + eliminatedLocations.unit;
  }

  @Override public String toString() {
    return numeral + " \u2208 " + unit + " \u2229 " + eliminatedLocations.unit;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    Overlap that = (Overlap) o;
    return this.unit.equals(that.unit)
        && this.numeral.equals(that.numeral)
        && this.eliminatedLocations.unit.equals(that.eliminatedLocations.unit);
    // Note that the "eliminatedLocations" ivar doesn't identify the insight.
  }
}
