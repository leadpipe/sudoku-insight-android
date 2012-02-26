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

import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;

/**
 * Describes a situation where there is only one possible numeral for a given
 * location.
 *
 * @author Luke Blanshard
 */
public class ForcedNumeral implements Insight {
  private final Location location;
  private final Numeral numeral;

  public ForcedNumeral(Location location, Numeral numeral) {
    this.location = location;
    this.numeral = numeral;
  }

  public Location getLocation() {
    return location;
  }

  public Numeral getNumeral() {
    return numeral;
  }
}
