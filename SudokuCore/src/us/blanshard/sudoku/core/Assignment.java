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
    return new Assignment(location, numeral);
  }

  private Assignment(Location location, Numeral numeral) {
    this.location = checkNotNull(location);
    this.numeral = checkNotNull(numeral);
  }

  @Override public String toString() {
    return numeral.number + " \u2192 " + location;  // That's a right arrow
  }

  @Override public boolean equals(Object o) {
    if (!(o instanceof Assignment)) return false;
    Assignment that = (Assignment) o;
    return this.location.equals(that.location)
        && this.numeral.equals(that.numeral);
  }

  @Override public int hashCode() {
    return location.hashCode() * 11 + numeral.hashCode();
  }
}
