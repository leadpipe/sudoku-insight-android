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
package us.blanshard.sudoku.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * A pair of {@link Unit} and {@link Numeral}.
 */
@Immutable
public class UnitNumeral {

  /** The number of distinct unit-numeral pairs. */
  public static final int COUNT = Unit.COUNT * Numeral.COUNT;

  /** The unit. */
  public final Unit unit;

  /** The numeral. */
  public final Numeral numeral;

  /** A number in the range [0, COUNT). */
  public final int index;

  public static UnitNumeral of(Unit unit, Numeral numeral) {
    return instances[unit.unitIndex() * Numeral.COUNT + numeral.index];
  }

  public static UnitNumeral of(int index) {
    return instances[index];
  }

  public static List<UnitNumeral> all() {
    return ALL;
  }

  private UnitNumeral(int index) {
    this.unit = Unit.ofIndex(index / Numeral.COUNT);
    this.numeral = Numeral.ofIndex(index % Numeral.COUNT);
    this.index = index;
  }

  @Override public String toString() {
    return unit.toString() + ':' + numeral;
  }

  private static final UnitNumeral[] instances;
  private static final List<UnitNumeral> ALL;
  static {
    instances = new UnitNumeral[COUNT];
    for (int i = 0; i < COUNT; ++i)
      instances[i] = new UnitNumeral(i);
    ALL = Collections.unmodifiableList(Arrays.asList(instances));
  }
}
