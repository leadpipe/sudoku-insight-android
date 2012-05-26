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

import us.blanshard.sudoku.core.Generator;
import us.blanshard.sudoku.core.Symmetry;

import android.content.SharedPreferences;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;

import com.google.common.base.Enums;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/**
 * Wrapper around shared preferences providing a more convenient API.
 *
 * @author Luke Blanshard
 */
public class Prefs {
  private static final char SEP = ':';
  private static final String GAME_ID = "gameId";
  private static final String GENERATOR = "generator";
  private static final String SHOW_INSIGHTS = "showInsights";
  private static final String SYMMETRIES = "symmetries";

  @Inject SharedPreferences mPrefs;

  public boolean hasCurrentGameId() {
    return mPrefs.contains(GAME_ID);
  }

  public long getCurrentGameId() {
    return mPrefs.getLong(GAME_ID, -1);
  }

  public void setCurrentGameIdAsync(long gameId) {
    SharedPreferences.Editor prefs = mPrefs.edit();
    prefs.putLong(GAME_ID, gameId);
    prefs.apply();
  }

  public void removeCurrentGameIdAsync() {
    SharedPreferences.Editor prefs = mPrefs.edit();
    prefs.remove(GAME_ID);
    prefs.apply();
  }

  public Generator getGenerator() {
    return mPrefs.contains(GENERATOR)
        ? Generator.valueOf(mPrefs.getString(GENERATOR, null)) : Generator.SUBTRACTIVE;
  }

  public void setGenerator(Generator generator) {
    ThreadPolicy policy = StrictMode.allowThreadDiskWrites();
    try {
      SharedPreferences.Editor prefs = mPrefs.edit();
      prefs.putString(GENERATOR, generator.toString());
      prefs.commit();
    } finally {
      StrictMode.setThreadPolicy(policy);
    }
  }

  public boolean getShowInsights() {
    return mPrefs.getBoolean(SHOW_INSIGHTS, true);
  }

  public void setShowInsightsAsync(boolean showInsights) {
    SharedPreferences.Editor prefs = mPrefs.edit();
    prefs.putBoolean(SHOW_INSIGHTS, showInsights);
    prefs.apply();
  }

  public Set<Symmetry> getSymmetries() {
    if (mPrefs.contains(SYMMETRIES)) {
      return Sets.newEnumSet(Iterables.transform(
          Splitter.on(SEP).split(mPrefs.getString(SYMMETRIES, null)),
          Enums.valueOfFunction(Symmetry.class)), Symmetry.class);
    }
    return EnumSet.of(Symmetry.CLASSIC, Symmetry.BLOCKWISE);
  }

  public Symmetry chooseSymmetry(Random random) {
    Set<Symmetry> set = getSymmetries();
    int i = 0, target = random.nextInt(set.size());
    for (Symmetry sym : set) {
      if (i++ == target) return sym;
    }
    throw new IllegalStateException("No symmetries found? " + set);
  }

  public void setSymmetries(Set<Symmetry> symmetries) {
    ThreadPolicy policy = StrictMode.allowThreadDiskWrites();
    try {
      SharedPreferences.Editor prefs = mPrefs.edit();
      if (symmetries.isEmpty()) prefs.remove(SYMMETRIES);
      else prefs.putString(SYMMETRIES, Joiner.on(SEP).join(symmetries));
      prefs.commit();
    } finally {
      StrictMode.setThreadPolicy(policy);
    }
  }
}
