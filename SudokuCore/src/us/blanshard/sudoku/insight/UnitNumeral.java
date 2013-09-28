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

import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;

import com.google.common.base.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * A pair of {@link Unit} and {@link Numeral}.
 */
@Immutable
public class UnitNumeral {
  /** The unit. */
  public final Unit unit;

  /** The numeral. */
  public final Numeral numeral;

  public static UnitNumeral of(Unit unit, Numeral numeral) {
    return new UnitNumeral(unit, numeral);
  }

  private UnitNumeral(Unit unit, Numeral numeral) {
    this.unit = checkNotNull(unit);
    this.numeral = checkNotNull(numeral);
  }

  @Override public String toString() {
    return unit.toString() + ':' + numeral;
  }

  @Override public boolean equals(Object o) {
    if (!(o instanceof UnitNumeral)) return false;
    UnitNumeral that = (UnitNumeral) o;
    return this.unit.equals(that.unit)
        && this.numeral.equals(that.numeral);
  }

  @Override public int hashCode() {
    return Objects.hashCode(unit, numeral);
  }
}
