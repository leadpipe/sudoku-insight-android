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

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.SpinnerAdapter;

/**
 * An abstract class that handles some common action bar-related functionality in the app. This
 * class provides functionality useful for both phones and tablets, and does not require any Android
 * 3.0-specific features, although it uses them if available.
 *
 * Two implementations of this class are {@link ActionBarHelperBase} for a pre-Honeycomb version of
 * the action bar, and {@link ActionBarHelperHoneycomb}, which uses the built-in ActionBar features
 * in Android 3.0 and later.
 */
public abstract class ActionBarHelper {
    protected final Activity mActivity;

    /**
     * Factory method for creating {@link ActionBarHelper} objects for a
     * given activity. Depending on which device the app is running, either a basic helper or
     * Honeycomb-specific helper will be returned.
     */
    public static ActionBarHelper createInstance(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return new ActionBarHelperICS(activity);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return new ActionBarHelperHoneycomb(activity);
        } else {
            return new ActionBarHelperBase(activity);
        }
    }

    /**
     * Listener interface for ActionBar navigation events.
     */
    public interface OnNavigationListener {
        /**
         * This method is called whenever a navigation item in your action bar
         * is selected.
         *
         * @param itemPosition Position of the item clicked.
         * @param itemId ID of the item clicked.
         * @return True if the event was handled, false otherwise.
         */
        public boolean onNavigationItemSelected(int itemPosition, long itemId);
    }

    /**
     * Set the current navigation mode, using the navigation mode constants from ActionBar.
     */
    public abstract void setNavigationMode(int mode);

    /**
     * Set the adapter and navigation callback for list navigation mode.
     *
     * The supplied adapter will provide views for the expanded list as well as
     * the currently selected item. (These may be displayed differently.)
     *
     * The supplied OnNavigationListener will alert the application when the user
     * changes the current list selection.
     *
     * @param adapter An adapter that will provide views both to display
     *                the current navigation selection and populate views
     *                within the dropdown navigation menu.
     * @param callback An OnNavigationListener that will receive events when the user
     *                 selects a navigation item.
     */
    public abstract void setListNavigationCallbacks(SpinnerAdapter adapter,
            OnNavigationListener callback);

    /**
     * Set the selected navigation item in list or tabbed navigation modes.
     *
     * @param position Position of the item to select.
     */
    public abstract void setSelectedNavigationItem(int position);

    /**
     * Get the position of the selected navigation item in list or tabbed navigation modes.
     *
     * @return Position of the selected item.
     */
    public abstract int getSelectedNavigationIndex();

    /**
     * Get the number of navigation items present in the current navigation mode.
     *
     * @return Number of navigation items.
     */
    public abstract int getNavigationItemCount();

    /**
     * Action bar helper code to be run in {@link Activity#onCreate(android.os.Bundle)}.
     */
    public void setDisplayHomeAsUpEnabled(boolean displayHomeAsUp) {
    }

    /**
     * Action bar helper code to be run in {@link Activity#onCreate(android.os.Bundle)}.
     */
    public void onCreate(Bundle savedInstanceState) {
    }

    /**
     * Action bar helper code to be run in {@link Activity#onPostCreate(android.os.Bundle)}.
     */
    public void onPostCreate(Bundle savedInstanceState) {
    }

    /**
     * Action bar helper code to be run in {@link Activity#onCreateOptionsMenu(android.view.Menu)}.
     *
     * NOTE: Setting the visibility of menu items in <em>menu</em> is not currently supported.
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    /**
     * Returns a {@link MenuInflater} for use when inflating menus. The implementation of this
     * method in {@link ActionBarHelperBase} returns a wrapped menu inflater that can read
     * action bar metadata from a menu resource pre-Honeycomb.
     */
    public MenuInflater getMenuInflater(MenuInflater superMenuInflater) {
        return superMenuInflater;
    }

    /**
     * Invalidates the options menu, specifically the action bar.
     */
    public void invalidateOptionsMenu() {
    }

    /**
     * Action bar helper code to be run in {@link FragmentActivity#onAttachFragment}.
     */
    public void onAttachFragment(Fragment fragment) {
    }


    protected ActionBarHelper(Activity activity) {
      mActivity = activity;
    }

    /**
     * Action bar helper code to be run in {@link Activity#onTitleChanged(CharSequence, int)}.
     */
    protected void onTitleChanged(CharSequence title, int color) {
    }
}
