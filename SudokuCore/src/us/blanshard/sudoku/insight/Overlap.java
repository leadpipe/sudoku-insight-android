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
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;

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
public class Overlap extends Insight.Atom {
  private final Unit unit;
  private final Numeral numeral;
  private final Unit overlappingUnit;
  private final UnitSubset overlap;
  private volatile Collection<Assignment> eliminations;

  public Overlap(Grid grid, Unit unit, Numeral numeral, Unit overlappingUnit, UnitSubset overlap) {
    super(grid, Pattern.overlap(grid, unit, overlappingUnit, numeral));
    this.unit = unit;
    this.numeral = numeral;
    this.overlappingUnit = overlappingUnit;
    this.overlap = overlap;
  }

  @Override public Collection<Assignment> getEliminations() {
    Collection<Assignment> answer = eliminations;
    if (answer == null) {
      synchronized (this) {
        if ((answer = eliminations) == null) {
          ImmutableList.Builder<Assignment> builder = ImmutableList.builder();
          for (Location loc : overlap)
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
    return overlappingUnit;
  }

  public UnitSubset getOverlap() {
    return overlap;
  }
}
