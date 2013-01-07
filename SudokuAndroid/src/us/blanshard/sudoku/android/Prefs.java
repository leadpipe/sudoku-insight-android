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

import static us.blanshard.sudoku.android.Extras.ATTEMPT_ID;
import static us.blanshard.sudoku.gen.Generator.NUM_STREAMS;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.math.LongMath;

import java.util.Calendar;

/**
 * Wrapper around shared preferences providing a more convenient API.
 *
 * @author Luke Blanshard
 */
public class Prefs {
  public static final String DEVICE_NAME = "deviceName";
  public static final String PROPER_ONLY = "properOnly";
  public static final String USER_ID = "googleUserId";

  /** The largest number of solutions this app will tolerate. */
  public static final int MAX_SOLUTIONS = 10;

  private static final String COUNTER = "counter";
  private static final String MONTH = "month";
  private static final String SORT = "sort";
  private static final String STREAM = "stream";

  private final Context mAppContext;
  private final SharedPreferences mPrefs;
  private final String mInstallationId;
  private static Prefs sInstance;

  private Prefs(Context context) {
    mAppContext = context.getApplicationContext();
    mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    mInstallationId = Installation.id(context);
    if (!mPrefs.contains(PROPER_ONLY) || !mPrefs.contains(DEVICE_NAME)) {
      SharedPreferences.Editor prefs = mPrefs.edit();
      prefs.putBoolean(PROPER_ONLY, true);
      prefs.putString(DEVICE_NAME, Build.MODEL);
      prefs.apply();
    }
  }

  public static synchronized Prefs instance(Context context) {
    if (sInstance == null) sInstance = new Prefs(context);
    return sInstance;
  }

  public boolean hasCurrentAttemptId() {
    return mPrefs.contains(ATTEMPT_ID);
  }

  public long getCurrentAttemptId() {
    return mPrefs.getLong(ATTEMPT_ID, -1);
  }

  public void setCurrentAttemptIdAsync(long attemptId) {
    SharedPreferences.Editor prefs = mPrefs.edit();
    prefs.putLong(ATTEMPT_ID, attemptId);
    prefs.apply();
  }

  public void removeCurrentAttemptIdAsync() {
    SharedPreferences.Editor prefs = mPrefs.edit();
    prefs.remove(ATTEMPT_ID);
    prefs.apply();
  }

  public int getSort() {
    return mPrefs.getInt(SORT, -1);
  }

  public void setSortAsync(int sort) {
    SharedPreferences.Editor prefs = mPrefs.edit();
    prefs.putInt(SORT, sort);
    prefs.apply();
  }

  public boolean hasUserId() {
    return !Strings.isNullOrEmpty(getUserId());
  }

  public String getUserId() {
    return mPrefs.getString(USER_ID, "");
  }

  /** Returns null if one isn't set up or the one that is isn't available anymore. */
  public Account getUserAccount() {
    String id = getUserId();
    if (Strings.isNullOrEmpty(id)) return null;
    for (Account acct : AccountManager.get(mAppContext).getAccountsByType("com.google"))
      if (id.equals(acct.name)) return acct;
    return null;
  }

  public boolean getProperOnly() {
    return mPrefs.getBoolean(PROPER_ONLY, true);
  }

  /**
   * Returns the largest number of solutions permitted to any puzzle we capture.
   */
  public int getMaxSolutions() {
    return getProperOnly() ? 1 : MAX_SOLUTIONS;
  }

  public String getDeviceName() {
    return mPrefs.getString(DEVICE_NAME, Build.MODEL);
  }

  public int getStream() {
    int stream = mPrefs.getInt(STREAM, 0);
    if (stream == 0) {
      HashCode code = Hashing.murmur3_128().hashString(mInstallationId, Charsets.UTF_8);
      stream = 1 + LongMath.mod(code.asLong(), NUM_STREAMS);
      setStreamAsync(stream);
    }
    return stream;
  }

  public void setStreamAsync(int stream) {
    SharedPreferences.Editor prefs = mPrefs.edit();
    prefs.putInt(STREAM, stream);
    prefs.apply();
  }

  public int getNextCounterSync(Calendar cal) {
    int month = cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH) + 1;
    int currentMonth = mPrefs.getInt(MONTH, 0);
    int counter = mPrefs.getInt(COUNTER, 0);
    SharedPreferences.Editor prefs = mPrefs.edit();
    if (month == currentMonth) {
      ++counter;
    } else {
      counter = 1;
      prefs.putInt(MONTH, month);
    }
    prefs.putInt(COUNTER, counter);
    prefs.commit();
    return counter;
  }
}
