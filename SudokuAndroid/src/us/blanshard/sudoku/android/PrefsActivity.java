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

import roboguice.activity.RoboPreferenceActivity;
import roboguice.inject.InjectPreference;

import us.blanshard.sudoku.core.Generator;
import us.blanshard.sudoku.core.Symmetry;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import java.util.Set;

import javax.inject.Inject;

/**
 * Shows the preferences screen.
 *
 * @author Luke Blanshard
 */
public class PrefsActivity extends RoboPreferenceActivity {
  private static final String RANDOM_GENERATOR = "randomGenerator";
  private static final String SYMMETRY_PREFIX = "symmetry:";

  @Inject Prefs mPrefs;
  @InjectPreference(RANDOM_GENERATOR) CheckBoxPreference mRandomGenerator;
  private Set<Symmetry> mSymmetries;

  @SuppressWarnings("deprecation")
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
    mSymmetries = mPrefs.getSymmetries();
    for (Symmetry sym : mSymmetries) {
      CheckBoxPreference check = (CheckBoxPreference) findPreference(SYMMETRY_PREFIX + sym);
      check.setChecked(true);
    }
    mRandomGenerator.setChecked(mPrefs.getGenerator() == Generator.SUBTRACTIVE_RANDOM);
  }

  @SuppressWarnings("deprecation")
  @Override public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
      Preference preference) {
    boolean answer = super.onPreferenceTreeClick(preferenceScreen, preference);
    if (preference.getKey().startsWith(SYMMETRY_PREFIX)) {
      Symmetry sym = Symmetry.valueOf(preference.getKey().substring(SYMMETRY_PREFIX.length()));
      CheckBoxPreference check = (CheckBoxPreference) preference;
      if (check.isChecked()) mSymmetries.add(sym);
      else mSymmetries.remove(sym);
      mPrefs.setSymmetries(mSymmetries);
    } else if (preference == mRandomGenerator) {
      mPrefs.setGenerator(
          mRandomGenerator.isChecked() ? Generator.SUBTRACTIVE_RANDOM : Generator.SUBTRACTIVE);
    }
    return answer;
  }
}
