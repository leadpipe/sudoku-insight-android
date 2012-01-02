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
  public boolean shown;

  public TrailItem(Trail trail, int color, boolean shown) {
    this.trail = trail;
    this.color = color;
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
