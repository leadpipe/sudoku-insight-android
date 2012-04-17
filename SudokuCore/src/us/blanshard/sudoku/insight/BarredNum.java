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
import us.blanshard.sudoku.core.Unit;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Describes a situation where there are no possible locations within a unit for
 * a given numeral.
 *
 * @author Luke Blanshard
 */
@ThreadSafe
public class BarredNum extends Insight.Atom {
  private final Unit unit;
  private final Numeral numeral;

  public BarredNum(Grid grid, Unit unit, Numeral numeral) {
    super(grid, Pattern.barredNumeral(grid, unit, numeral));
    this.unit = unit;
    this.numeral = numeral;
  }

  public Unit getUnit() {
    return unit;
  }

  public Numeral getNumeral() {
    return numeral;
  }
}
