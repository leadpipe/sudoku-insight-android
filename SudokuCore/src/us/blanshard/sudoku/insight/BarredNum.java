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

import com.google.common.base.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Describes a situation where there are no possible locations within a unit for
 * a given numeral.
 *
 * @author Luke Blanshard
 */
@Immutable
public class BarredNum extends Insight.Atom {
  private final Unit unit;
  private final Numeral numeral;

  public BarredNum(Pattern.BarredNum pattern, Unit unit, Numeral numeral) {
    super(pattern);
    this.unit = unit;
    this.numeral = numeral;
  }

  public Unit getUnit() {
    return unit;
  }

  public Numeral getNumeral() {
    return numeral;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o == null || o.getClass() != getClass()) return false;
    BarredNum that = (BarredNum) o;
    return this.pattern.equals(that.pattern)
        && this.unit.equals(that.unit)
        && this.numeral.equals(that.numeral);
  }

  @Override public int hashCode() {
    return Objects.hashCode(pattern, unit, numeral);
  }
}
