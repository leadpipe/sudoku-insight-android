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

/**
 * Holds a location that is prevented by the rules of the game from being
 * assigned any numeral.
 *
 * @author Luke Blanshard
 */
public class BarredLocation extends Insight.Atom {
  private final Location location;

  BarredLocation(Location location, Pattern pattern) {
    super(Insight.Type.BARRED_LOCATION, pattern);
    this.location = location;
  }

  public Location getLocation() {
    return location;
  }

  @Override boolean isError() {
    return true;
  }

  @Override @Nullable public Assignment getAssignment() {
    return null;
  }

  @Override Collection<Assignment> getEliminations() {
    return Collections.<Assignment>emptySet();
  }
}
