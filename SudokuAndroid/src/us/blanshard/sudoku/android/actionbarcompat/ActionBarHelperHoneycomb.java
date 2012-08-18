/*
 * Copyright 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package us.blanshard.sudoku.android.actionbarcompat;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.widget.SpinnerAdapter;

/**
 * An extension of {@link ActionBarHelper} that provides Android 3.0-specific functionality for
 * Honeycomb tablets. It thus requires API level 11.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ActionBarHelperHoneycomb extends ActionBarHelper {

    protected ActionBarHelperHoneycomb(Activity activity) {
        super(activity);
    }

    /**
     * Returns a {@link Context} suitable for inflating layouts for the action bar. The
     * implementation for this method in {@link ActionBarHelperICS} asks the action bar for a
     * themed context.
     */
    protected Context getActionBarThemedContext() {
        return mActivity;
    }

    @Override public void setNavigationMode(int mode) {
        mActivity.getActionBar().setNavigationMode(mode);
    }

    @Override public void setListNavigationCallbacks(SpinnerAdapter adapter,
            final OnNavigationListener callback) {
        mActivity.getActionBar().setListNavigationCallbacks(adapter, new ActionBar.OnNavigationListener() {
          @Override public boolean onNavigationItemSelected(int itemPosition, long itemId) {
              return callback.onNavigationItemSelected(itemPosition, itemId);
          }
        });
    }

    @Override public void setSelectedNavigationItem(int position) {
        mActivity.getActionBar().setSelectedNavigationItem(position);
    }

    @Override public int getSelectedNavigationIndex() {
        return mActivity.getActionBar().getSelectedNavigationIndex();
    }

    @Override public int getNavigationItemCount() {
        return mActivity.getActionBar().getNavigationItemCount();
    }

    @Override public void onAttachFragment(Fragment fragment) {
        // Just override to ignore the fragment -- not needed in honeycomb and later
    }

    @Override public void setDisplayHomeAsUpEnabled(boolean displayHomeAsUp) {
        mActivity.getActionBar().setDisplayHomeAsUpEnabled(displayHomeAsUp);
    }

    /**{@inheritDoc}*/
    @Override
    public void invalidateOptionsMenu() {
        mActivity.invalidateOptionsMenu();
    }
}
