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
package us.blanshard.sudoku.android;

import us.blanshard.sudoku.game.Sudoku.Trail;

/**
 * A struct for the items in the list of trails.
 *
 * @author Luke Blanshard
 */
public class TrailItem {
  public final Trail trail;
  public final int color;
  public final int dimColor;
  public boolean shown;

  public TrailItem(Trail trail, int color, int dimColor, boolean shown) {
    this.trail = trail;
    this.color = color;
    this.dimColor = dimColor;
    this.shown = shown;
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(trail.getId() + 1).append(": ");
    if (trail.getTrailhead() == null) {
      sb.append('\u2014');  // em dash
    } else {
      sb.append(trail.getTrailhead())
          .append(" \u2190 ")  // left arrow
          .append(trail.get(trail.getTrailhead()));
    }
    return sb.toString();
  }
}
