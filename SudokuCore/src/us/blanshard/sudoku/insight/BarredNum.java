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
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumeral;

import javax.annotation.concurrent.Immutable;

/**
 * Describes a situation where there are no possible locations within a unit for
 * a given numeral.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class BarredNum extends Insight {
  private final UnitNumeral unitNum;

  public BarredNum(Unit unit, Numeral numeral) {
    super(Type.BARRED_NUMERAL);
    this.unitNum = UnitNumeral.of(unit, numeral);
  }

  public Unit getUnit() {
    return unitNum.unit;
  }

  public Numeral getNumeral() {
    return unitNum.numeral;
  }

  @Override public void apply(GridMarks.Builder builder) {
    // Nothing to do, it's an error.
  }

  @Override public boolean isImpliedBy(GridMarks gridMarks) {
    return gridMarks.marks.get(unitNum).isEmpty();
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    return elimination.numeral == unitNum.numeral && unitNum.unit.contains(elimination.location);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    BarredNum that = (BarredNum) o;
    return this.unitNum.equals(that.unitNum);
  }

  @Override public int hashCode() {
    return unitNum.hashCode() + getClass().hashCode();
  }

  @Override public String toString() {
    return unitNum.numeral + " \u2209 " + unitNum.unit;  // not-element-of
  }
}
