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

import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
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
      ThreadPolicy prev = StrictMode.allowThreadDiskReads();
      try {
        addPreferencesFromResource(R.xml.preferences);
      } finally {
        StrictMode.setThreadPolicy(prev);
      }
    }
  }
}
