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

import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import us.blanshard.sudoku.android.Database.Attempt;
import us.blanshard.sudoku.android.Database.AttemptState;

import android.content.Context;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateUtils;

import java.util.Locale;

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
    if (mins < 60) return String.format(Locale.ROOT, "%d:%02d", mins, secs % 60);
    return String.format(Locale.ROOT, "%d:%02d:%02d", mins / 60, mins % 60, secs % 60);
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
   * Returns the given element's collection name, with an embedded link to the
   * list activity.
   */
  public static String collectionNameHtml(Context context, Database.Element element, boolean link) {
    String html = TextUtils.htmlEncode(element.collection.name);
    if (link)
      html = "<a href='" + Uris.LIST_URI_PREFIX + element.collection._id
          + '/' + element.puzzleId + "'>" + html + "</a>";
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
   * Returns a summary of the state of an attempt: queued, playing, abandoned,
   * or solved, along with when this happened.
   */
  public static String attemptSummaryHtml(Context context, Attempt attempt) {
    return attemptSummaryHtml(context, attempt, false);
  }
    /**
     * Returns a summary of the state of an attempt: queued, playing, abandoned,
     * or solved, along with when this happened.
     */
  public static String attemptSummaryHtml(Context context, Attempt attempt, boolean longTime) {
    int resourceId;
    switch (attempt.attemptState) {
      case UNSTARTED: resourceId = R.string.text_attempt_queued;  break;
      case STARTED:   resourceId = R.string.text_attempt_playing; break;
      case GAVE_UP:   resourceId = R.string.text_attempt_gave_up; break;
      case FINISHED:  resourceId = R.string.text_attempt_solved;  break;
      case SKIPPED:   resourceId = R.string.text_attempt_skipped; break;
      default:
        throw new AssertionError();
    }
    String elapsedTime = elapsedTime(attempt.elapsedMillis);
    if (attempt.attemptState == AttemptState.FINISHED)
      elapsedTime = "<b>" + elapsedTime + "</b>";
    CharSequence when = longTime ? relativeDateTime(context, attempt.lastTime)
        : dateTimeWithPreposition(context, attempt.lastTime);
    return context.getString(resourceId, elapsedTime, when);
  }
}
