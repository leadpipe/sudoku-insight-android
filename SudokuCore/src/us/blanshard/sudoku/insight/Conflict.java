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
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.UnitSubset;

/**
 * Describes an actual conflict on a Sudoku board: a set of locations within a
 * unit that are all assigned the same numeral.
 *
 * @author Luke Blanshard
 */
public class Conflict extends Insight.Atom {
  private final Numeral numeral;
  private final UnitSubset locations;

  Conflict(Grid grid, Numeral numeral, UnitSubset locations) {
    super(grid, Pattern.conflict(locations.unit));
    this.numeral = numeral;
    this.locations = locations;
  }

  public Numeral getNumeral() {
    return numeral;
  }

  public UnitSubset getLocations() {
    return locations;
  }
}
