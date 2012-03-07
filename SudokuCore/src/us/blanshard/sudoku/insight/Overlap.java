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

import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;

/**
 * Describes an overlap, where the only locations within one unit that a numeral
 * may inhabit also lie within an overlapping unit.
 *
 * @author Luke Blanshard
 */
public class Overlap implements Insight {
  private final Unit unit;
  private final Numeral numeral;
  private final Unit overlappingUnit;

  public Overlap(Unit unit, Numeral numeral, Unit overlappingUnit) {
    this.unit = unit;
    this.numeral = numeral;
    this.overlappingUnit = overlappingUnit;
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
}
