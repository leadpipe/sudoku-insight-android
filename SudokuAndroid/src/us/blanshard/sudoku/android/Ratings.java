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
package us.blanshard.sudoku.android;

import us.blanshard.sudoku.insight.Rating;

/**
 * Methods for interpreting {@link Rating}s in the UI.
 */
public class Ratings {
  /**
   * The maximum number of stars returned by {@link #ratingStars}.
   */
  public static final int MAX_STARS = 6;

  /**
   * Returns the rating number we show to the user, which is the estimated number
   * of minutes it will take to solve the puzzle.
   */
  public static double numericalRating(double estimatedAverageSolutionSeconds) {
    // Our numerical rating is the estimated number of minutes.
    return estimatedAverageSolutionSeconds / 60;
  }

  /**
   * Turns a {@link #numericalRating} into a number of stars, which is an integer
   * between 1 and {@link #MAX_STARS} inclusive.
   */
  public static int ratingStars(double numericalRating) {
    if (numericalRating < 2.5) return 1;
    if (numericalRating < 3.5) return 2;
    if (numericalRating < 5.5) return 3;
    if (numericalRating < 10)  return 4;
    if (numericalRating < 20)  return 5;
    return 6;
  }

  /**
   * Returns the resource ID of a string that describes the difficulty of a
   * puzzle with the given number of stars.
   */
  public static int starsDescriptionResource(int stars) {
    return STAR_DESCRIPTION_RESOURCE_IDS[stars - 1];
  }

  private static int[] STAR_DESCRIPTION_RESOURCE_IDS = {
    R.string.text_rating_very_easy,
    R.string.text_rating_easy,
    R.string.text_rating_moderate,
    R.string.text_rating_hard,
    R.string.text_rating_very_hard,
    R.string.text_rating_extremely_hard,
  };
}
