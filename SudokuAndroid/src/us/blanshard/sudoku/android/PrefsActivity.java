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

import us.blanshard.sudoku.gen.GenerationStrategy;
import us.blanshard.sudoku.gen.Symmetry;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import java.util.Set;

/**
 * Shows the preferences screen.
 *
 * @author Luke Blanshard
 */
public class PrefsActivity extends PreferenceActivity {
  private static final String RANDOM_GENERATOR = "randomGenerator";
  private static final String SYMMETRY_PREFIX = "symmetry:";

  private Prefs mPrefs;
  private CheckBoxPreference mRandomGenerator;
  private Set<Symmetry> mSymmetries;

  @SuppressWarnings("deprecation")
  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
    mPrefs = Prefs.instance(this);
    mRandomGenerator = (CheckBoxPreference) findPreference(RANDOM_GENERATOR);
    mSymmetries = mPrefs.getSymmetries();
    for (Symmetry sym : mSymmetries) {
      CheckBoxPreference check = (CheckBoxPreference) findPreference(SYMMETRY_PREFIX + sym);
      check.setChecked(true);
    }
    mRandomGenerator.setChecked(mPrefs.getGenerator() == GenerationStrategy.SUBTRACTIVE_RANDOM);
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
          mRandomGenerator.isChecked() ? GenerationStrategy.SUBTRACTIVE_RANDOM : GenerationStrategy.SUBTRACTIVE);
    }
    return answer;
  }
}
