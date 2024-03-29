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

import static us.blanshard.sudoku.android.Extras.ATTEMPT_ID;
import static us.blanshard.sudoku.gen.Generator.NUM_STREAMS;
import static us.blanshard.sudoku.messages.InstallationRpcs.monthNumber;

import us.blanshard.sudoku.insight.Evaluator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.math.LongMath;

import java.util.Calendar;
import java.util.Locale;

/**
 * Wrapper around shared preferences providing a more convenient API.
 *
 * @author Luke Blanshard
 */
public class Prefs {
  public static final String BACKED_UP_PREFS = "prefs";
  public static final String LOCAL_PREFS = "localPrefs";

  public static final String DEVICE_NAME = "deviceName";
  public static final String PROPER_ONLY = "properOnly";
  public static final String SHARE_DATA = "shareData";
  public static final String STREAM = "stream";
  public static final String USER_ID = "googleUserId";

  /** The largest number of solutions this app will tolerate. */
  public static final int MAX_SOLUTIONS = 10;

  private static final String COLLECTION = "collection";
  private static final String COUNTER = "counter";
  private static final String DEFAULT_DEVICE_NAME = "defaultDeviceName";
  private static final String INSTALL_DATA = "installData";
  private static final String MONTH = "month";
  private static final String RATING_VERSION = "ratingVersion";
  private static final String SEEN_NOTICE = "seenNotice";
  private static final String SORT = "sort";
  private static final String STREAM_COUNT = "streamCount";

  private final Context mAppContext;
  private final BackupManager mBackupManager;
  private final SharedPreferences mLocalPrefs;
  private final SharedPreferences mPrefs;
  private final String mInstallationId;
  // These will be gc'ed if not retained here:
  private final OnSharedPreferenceChangeListener mPrefsListener;
  private final OnSharedPreferenceChangeListener mLocalPrefsListener;
  private static Prefs sInstance;

  private Prefs(Context context) {
    mAppContext = context.getApplicationContext();
    mBackupManager = new BackupManager(context);
    mPrefs = context.getSharedPreferences(BACKED_UP_PREFS, 0);
    mLocalPrefs = context.getSharedPreferences(LOCAL_PREFS, 0);
    mInstallationId = Installation.id(context);
    mPrefsListener = new OnSharedPreferenceChangeListener() {
      @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mBackupManager.dataChanged();
        NetworkService.saveInstallationInfo(mAppContext);
      }
    };
    mPrefs.registerOnSharedPreferenceChangeListener(mPrefsListener);
    mLocalPrefsListener = new OnSharedPreferenceChangeListener() {
      @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        NetworkService.saveInstallationInfo(mAppContext);
      }
    };
    mLocalPrefs.registerOnSharedPreferenceChangeListener(mLocalPrefsListener);
    if (!mPrefs.contains(PROPER_ONLY) || !mPrefs.contains(DEVICE_NAME)) {
      SharedPreferences.Editor prefs = mPrefs.edit();
      String defaultDeviceName = defaultDeviceName();
      prefs.putString(DEVICE_NAME, defaultDeviceName);
      prefs.putString(DEFAULT_DEVICE_NAME, defaultDeviceName);
      prefs.putBoolean(PROPER_ONLY, true);
      prefs.putBoolean(SHARE_DATA, false);
      prefs.putString(USER_ID, "");
      prefs.apply();
    }
    // Kick off all required save ops at startup time.
    NetworkService.runStartupTimeOps(mAppContext);

    // While we're here, start up the rating service as well.
    RatingService.start(mAppContext);
  }

  public static synchronized Prefs instance(Context context) {
    if (sInstance == null) sInstance = new Prefs(context);
    return sInstance;
  }

  public static String defaultDeviceName() {
    String answer = Build.MODEL;
    if (!answer.toLowerCase(Locale.US).startsWith(Build.MANUFACTURER.toLowerCase(Locale.US)))
      answer = Build.MANUFACTURER + " " + answer;
    return answer;
  }

  public Context getContext() {
    return mAppContext;
  }

  public boolean hasCurrentAttemptId() {
    return mLocalPrefs.contains(ATTEMPT_ID);
  }

  public long getCurrentAttemptId() {
    return mLocalPrefs.getLong(ATTEMPT_ID, -1);
  }

  public void setCurrentAttemptIdAsync(long attemptId) {
    SharedPreferences.Editor prefs = mLocalPrefs.edit();
    prefs.putLong(ATTEMPT_ID, attemptId);
    prefs.apply();
  }

  public void removeCurrentAttemptIdAsync() {
    SharedPreferences.Editor prefs = mLocalPrefs.edit();
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

  public boolean getShareData() {
    return mPrefs.getBoolean(SHARE_DATA, false);
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

  public void setProperOnlyAsync(boolean flag) {
    SharedPreferences.Editor prefs = mPrefs.edit();
    prefs.putBoolean(PROPER_ONLY, flag);
    prefs.apply();
  }

  public String getDeviceName() {
    return mPrefs.getString(DEVICE_NAME, null);
  }

  public void setDeviceNameAsync(String name) {
    SharedPreferences.Editor prefs = mPrefs.edit();
    prefs.putString(DEVICE_NAME, name);
    prefs.apply();
  }

  public void resetDeviceNameIfNeeded() {
    String defaultDeviceName = defaultDeviceName();
    // If we're restored to a different device, reset the device name. If it's
    // to the same device, leave the device name alone.
    if (!defaultDeviceName.equals(mPrefs.getString(DEFAULT_DEVICE_NAME, null))) {
      SharedPreferences.Editor prefs = mPrefs.edit();
      prefs.putString(DEVICE_NAME, defaultDeviceName);
      prefs.putString(DEFAULT_DEVICE_NAME, defaultDeviceName);
      prefs.apply();
    }
  }

  public int getStream() {
    int stream = mLocalPrefs.getInt(STREAM, 0);
    if (stream == 0) {
      HashCode code = Hashing.murmur3_128().hashString(mInstallationId, Charsets.UTF_8);
      stream = 1 + LongMath.mod(code.asLong(), getStreamCount());
      setStreamAsync(stream);
    }
    return stream;
  }

  public int getStreamCount() {
    return mLocalPrefs.getInt(STREAM_COUNT, NUM_STREAMS);
  }

  public void setStreamAsync(int stream) {
    SharedPreferences.Editor prefs = mLocalPrefs.edit();
    prefs.putInt(STREAM, stream);
    prefs.apply();
  }

  public void setStreamCountAsync(int streamCount) {
    if (streamCount == getStreamCount()) return;
    SharedPreferences.Editor prefs = mLocalPrefs.edit();
    prefs.putInt(STREAM_COUNT, streamCount);
    prefs.apply();
  }

  public int getNextCounter(Calendar cal) {
    int month = monthNumber(cal);
    int currentMonth = mPrefs.getInt(MONTH, 0);
    int counter = mPrefs.getInt(COUNTER, 0);
    if (month == currentMonth) {
      ++counter;
    } else {
      counter = 1;
    }
    return counter;
  }

  public int getMonthNumber() {
    return mPrefs.getInt(MONTH, 0);
  }

  public void setNextCounterSync(Calendar cal, int counter) {
    int month = monthNumber(cal);
    SharedPreferences.Editor prefs = mPrefs.edit();
    prefs.putInt(MONTH, month);
    prefs.putInt(COUNTER, counter);
    prefs.commit();
  }

  /**
   * The install data is the data that describes this installation that has been
   * synced to the central service.
   */
  public String getInstallData() {
    return mLocalPrefs.getString(INSTALL_DATA, "");
  }

  public void setInstallDataSync(String data) {
    SharedPreferences.Editor prefs = mLocalPrefs.edit();
    prefs.putString(INSTALL_DATA, data);
    prefs.commit();
  }

  public boolean hasUserEverSeenNotice() {
    return mPrefs.getBoolean(SEEN_NOTICE, false);
  }

  public boolean hasUserSeenNoticeHere() {
    return mLocalPrefs.getBoolean(SEEN_NOTICE, false);
  }

  public void setUserHasSeenNoticeAsync() {
    SharedPreferences.Editor prefs = mLocalPrefs.edit();
    prefs.putBoolean(SEEN_NOTICE, true);
    prefs.apply();
    prefs = mPrefs.edit();
    prefs.putBoolean(SEEN_NOTICE, true);
    prefs.apply();
  }

  /**
   * The {@link Evaluator} algorithm version that all rated puzzles in the
   * database have; defaults to one less than the current version number, to
   * signal that the database is out of sync with the code.
   */
  public int getRatingVersion() {
    return mLocalPrefs.getInt(RATING_VERSION, Evaluator.CURRENT_VERSION - 1);
  }

  public void setRatingVersionAsync(int version) {
    SharedPreferences.Editor prefs = mLocalPrefs.edit();
    prefs.putInt(RATING_VERSION, version);
    prefs.apply();
  }

  /**
   * The database collection ID of the collection currently being played.  Defaults
   * to the built-in collection of generated puzzles.
   */
  public long getCurrentCollection() {
    return mLocalPrefs.getLong(COLLECTION, Database.GENERATED_COLLECTION_ID);
  }

  public void setCurrentCollectionAsync(long collectionId) {
    SharedPreferences.Editor prefs = mLocalPrefs.edit();
    prefs.putLong(COLLECTION, collectionId);
    prefs.apply();
  }
}
