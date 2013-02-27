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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuItem;

/**
 * @author Luke Blanshard
 */
public class SettingsActivity extends ActivityBase {

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getFragmentManager().beginTransaction()
        .replace(android.R.id.content, new SettingsFragment())
        .commit();
  }

  @Override protected String getHelpPage() {
    return "settings";
  }

  @Override public boolean onPrepareOptionsMenu(Menu menu) {
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      switch (item.getItemId()) {
        case R.id.menu_settings:
          item.setVisible(false);
          break;
      }
    }
    return super.onPrepareOptionsMenu(menu);
  }

  public static class SettingsFragment extends PreferenceFragment {
    @Override public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      getPreferenceManager().setSharedPreferencesName(Prefs.BACKED_UP_PREFS);
      ThreadPolicy prev = StrictMode.allowThreadDiskReads();
      try {
        addPreferencesFromResource(R.xml.preferences);
      } finally {
        StrictMode.setThreadPolicy(prev);
      }
    }

    @Override public void onActivityCreated(Bundle savedInstanceState) {
      super.onActivityCreated(savedInstanceState);

      setUpDeviceName();
      setUpAccounts();
    }

    private void setUpDeviceName() {
      Preference pref = findPreference(Prefs.DEVICE_NAME);
      pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
        @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
          preference.setSummary(String.valueOf(newValue));
          return true;
        }
      });
      applyChangeListener(pref);
    }

    private void setUpAccounts() {
      ListPreference pref = (ListPreference) findPreference(Prefs.USER_ID);
      AccountManager mgr = AccountManager.get(getActivity());
      Account[] accts = mgr.getAccountsByType("com.google");
      CharSequence[] values = new CharSequence[1 + accts.length];
      CharSequence[] names = new CharSequence[1 + accts.length];
      values[0] = "";
      names[0] = getString(R.string.prefs_no_user_id);
      for (int i = 0; i < accts.length; ++i) {
        values[i + 1] = names[i + 1] = accts[i].name;
      }
      pref.setEntries(names);
      pref.setEntryValues(values);
      pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
        @Override public boolean onPreferenceChange(Preference preference, Object newValue) {
          String s = newValue == null ? "" : newValue.toString();
          preference.setSummary(s.isEmpty() ? getString(R.string.prefs_no_user_id_desc) : s);
          preference.notifyDependencyChange(s.isEmpty());
          return true;
        }
      });
      applyChangeListener(pref);
    }

    private static void applyChangeListener(Preference pref) {
      pref.getOnPreferenceChangeListener().onPreferenceChange(
          pref, pref.getSharedPreferences().getAll().get(pref.getKey()));
    }
  }
}
