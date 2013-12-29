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

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.concurrent.Immutable;

/**
 * Combines a {@link Location} and a {@link Numeral}.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class Assignment {

  /** The location. */
  public final Location location;

  /** The numeral. */
  public final Numeral numeral;

  public static Assignment of(Location location, Numeral numeral) {
    return INSTANCES[index(location, numeral)];
  }

  private Assignment(Location location, Numeral numeral) {
    this.location = checkNotNull(location);
    this.numeral = checkNotNull(numeral);
  }

  @Override public String toString() {
    return numeral.number + " \u2192 " + location;  // That's a right arrow
  }

  private static final Assignment[] INSTANCES;
  static {
    INSTANCES = new Assignment[Location.COUNT * Numeral.COUNT];
    for (Location loc : Location.all())
      for (Numeral num : Numeral.all())
        INSTANCES[index(loc, num)] = new Assignment(loc, num);
  }

  private static int index(Location location, Numeral numeral) {
    return location.index * Numeral.COUNT + numeral.index;
  }
}
