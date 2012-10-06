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
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.util.Collection;

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
  private final UnitSubset extra;
  private volatile Collection<Assignment> eliminations;

  public Overlap(Unit unit, Numeral numeral, UnitSubset extra) {
    super(Type.OVERLAP);
    this.unit = unit;
    this.numeral = numeral;
    this.extra = extra;
  }

  @Override public Collection<Assignment> getEliminations() {
    Collection<Assignment> answer = eliminations;
    if (answer == null) {
      synchronized (this) {
        if ((answer = eliminations) == null) {
          ImmutableList.Builder<Assignment> builder = ImmutableList.builder();
          for (Location loc : extra)
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
    return extra.unit;
  }

  public UnitSubset getExtra() {
    return extra;
  }

  public String toBasicString() {
    return unit + ":" + numeral + ":" + extra.unit;
  }

  @Override public void apply(GridMarks.Builder builder) {
    for (Location loc : extra)
      builder.eliminate(loc, numeral);
  }

  @Override public boolean isImpliedBy(GridMarks gridMarks) {
    // Ensures that all possible locations for the numeral in the unit lie in
    // the overlapping unit.
    return gridMarks.marks.get(unit, numeral).minus(extra.unit).isEmpty();
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    return elimination.numeral == this.numeral
        && this.unit.contains(elimination.location)
        && !this.extra.unit.contains(elimination.location);
  }

  @Override public String toString() {
    return numeral + " \u2208 " + unit + " \u2229 " + extra.unit;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    Overlap that = (Overlap) o;
    return this.unit.equals(that.unit)
        && this.numeral.equals(that.numeral)
        && this.extra.unit.equals(that.extra.unit);
    // Note that the "extra" ivar doesn't identify the insight.
  }

  @Override public int hashCode() {
    return Objects.hashCode(unit, numeral, extra.unit);
  }
}
