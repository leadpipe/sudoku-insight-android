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

import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import us.blanshard.sudoku.android.Database.Game;
import us.blanshard.sudoku.android.Database.GameState;

import android.content.Context;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.google.common.base.Strings;

/**
 * Helpful methods for converting various kinds of data to human-readable text.
 *
 * @author Luke Blanshard
 */
public class ToText {

  /**
   * Formats elapsed time as hours:minutes:seconds.
   */
  public static String elapsedTime(long millis) {
    long secs = MILLISECONDS.toSeconds(millis);
    long mins = secs / 60;
    if (mins < 60) return String.format("%d:%02d", mins, secs % 60);
    return String.format("%d:%02d:%02d", mins / 60, mins % 60, secs % 60);
  }

  /**
   * Formats the given timestamp as a relative date-time string.
   */
  public static CharSequence relativeDateTime(Context context, long timestamp) {
    return DateUtils.getRelativeDateTimeString(
        context, timestamp, MINUTE_IN_MILLIS, 2 * DAY_IN_MILLIS, FORMAT_SHOW_WEEKDAY);
  }

  /**
   * Formats the given timestamp as a date-time string with appropriate leading
   * preposition ("at" or "on").
   */
  public static CharSequence dateTimeWithPreposition(Context context, long timestamp) {
    return DateUtils.getRelativeTimeSpanString(context, timestamp, true);
  }

  /**
   * Returns the given element's collection name, combined with the element's
   * source if it has one, with an embedded link to the list activity.
   */
  public static String collectionNameHtml(Context context, Database.Element element, boolean link) {
    String html = TextUtils.htmlEncode(element.collection.name);
    if (link)
      html = "<a href='" + Uris.LIST_URI_PREFIX + element.collection._id
          + '/' + element.puzzleId + "'>" + html + "</a>";
    if (!Strings.isNullOrEmpty(element.source))
      html = context.getString(
          R.string.text_collection_with_source, html, TextUtils.htmlEncode(element.source));
    return html;
  }

  /**
   * Returns the given element's collection name, combined with the element's
   * source if it has one, with an embedded link to the list activity.
   */
  public static String collectionNameAndTimeHtml(Context context, Database.Element element) {
    String coll = collectionNameHtml(context, element, true);
    CharSequence date = relativeDateTime(context, element.createTime);
    return context.getString(R.string.text_collection_date, coll, date);
  }

  /**
   * Combines the element's collection name with the date/time the element was created.
   */
  public static String collectionNameAndTimeText(Context context, Database.Element element) {
    String html = collectionNameAndTimeHtml(context, element);
    return Html.fromHtml(html).toString();
  }

  /**
   * Returns a summary of the state of a game: queued, playing, abandoned, or
   * solved, along with when this happened.
   */
  public static String gameSummaryHtml(Context context, Game game) {
    return gameSummaryHtml(context, game, false);
  }
    /**
     * Returns a summary of the state of a game: queued, playing, abandoned, or
     * solved, along with when this happened.
     */
  public static String gameSummaryHtml(Context context, Game game, boolean longTime) {
    int resourceId;
    switch (game.gameState) {
      case UNSTARTED: resourceId = R.string.text_game_queued;  break;
      case STARTED:   resourceId = R.string.text_game_playing; break;
      case GAVE_UP:   resourceId = R.string.text_game_gave_up; break;
      case FINISHED:  resourceId = R.string.text_game_solved;  break;
      case SKIPPED:   resourceId = R.string.text_game_skipped; break;
      default:
        throw new AssertionError();
    }
    String elapsedTime = elapsedTime(game.elapsedMillis);
    if (game.gameState == GameState.FINISHED)
      elapsedTime = "<b>" + elapsedTime + "</b>";
    CharSequence when = longTime ? relativeDateTime(context, game.lastTime)
        : dateTimeWithPreposition(context, game.lastTime);
    return context.getString(resourceId, elapsedTime, when);
  }
}
