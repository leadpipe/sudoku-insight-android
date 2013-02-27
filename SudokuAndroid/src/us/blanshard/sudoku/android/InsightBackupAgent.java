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

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

/**
 * Saves (most of) our preferences to the Android backup service, so on
 * reinstall we aren't starting from scratch.
 *
 * @author Luke Blanshard
 */
public class InsightBackupAgent extends BackupAgentHelper {
  @Override public void onCreate() {
    SharedPreferencesBackupHelper helper =
        new SharedPreferencesBackupHelper(this, Prefs.BACKED_UP_PREFS);
    addHelper("prefs", helper);
  }

  @Override public void onRestore(BackupDataInput data, int appVersionCode,
      ParcelFileDescriptor newState) throws IOException {
    super.onRestore(data, appVersionCode, newState);
    Prefs.instance(this).resetDeviceNameIfNeeded();
  }
}
