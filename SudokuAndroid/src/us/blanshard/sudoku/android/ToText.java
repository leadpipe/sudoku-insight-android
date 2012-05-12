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

import static android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.WEEK_IN_MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
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
   * Returns the given element's collection name, combined with the element's
   * source if it has one.
   */
  public static String collectionName(Context context, Database.Element element) {
    String coll = element.collection.name;
    if (!Strings.isNullOrEmpty(element.source))
      coll = context.getString(R.string.text_collection_with_source, coll, element.source);
    return coll;
  }

  /**
   * Formats the given timestamp as a relative date-time string.
   */
  public static CharSequence relativeDateTime(Context context, long timestamp) {
    return DateUtils.getRelativeDateTimeString(
        context, timestamp, MINUTE_IN_MILLIS, WEEK_IN_MILLIS, FORMAT_SHOW_WEEKDAY);
  }

  /**
   * Formats the given timestamp as a date-time string with appropriate leading
   * preposition ("at" or "on").
   */
  public static CharSequence dateTimeWithPreposition(Context context, long timestamp) {
    return DateUtils.getRelativeTimeSpanString(context, timestamp, true);
  }
}
