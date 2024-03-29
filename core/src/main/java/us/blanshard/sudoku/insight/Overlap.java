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
import us.blanshard.sudoku.core.UnitSubset;

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
  private final UnitSubset extra;
  private volatile List<Assignment> eliminations;

  public Overlap(Unit unit, Numeral numeral, UnitSubset extra) {
    super(Type.OVERLAP);
    this.unit = unit;
    this.numeral = numeral;
    this.extra = extra;
  }

  @Override public List<Assignment> getEliminations() {
    List<Assignment> answer = eliminations;
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

  @Override public void apply(Marks.Builder builder) {
    for (int i = 0; i < extra.size(); ++i)
      builder.eliminate(extra.get(i), numeral);
  }

  @Override public boolean isImpliedBy(Marks marks) {
    // Ensures that all possible locations for the numeral in the unit lie in
    // the overlapping unit.
    return marks.getSet(UnitNumeral.of(unit, numeral)).minus(extra.unit).isEmpty();
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
    return ((unit.index + 5) << 9)
        | ((extra.unit.index + 5) << 5)
        | (numeral.index + 7);
  }
}
