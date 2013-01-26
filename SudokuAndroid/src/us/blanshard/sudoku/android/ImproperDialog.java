/*
Copyright 2013 Google Inc.

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

import us.blanshard.sudoku.gen.Generator;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;

import com.google.gson.JsonObject;

/**
 * A dialog explaining that a given puzzle is improper, and letting the user
 * back out, try the puzzle, or try the puzzle and change the "proper only"
 * setting to off.
 *
 * @author Luke Blanshard
 */
public abstract class ImproperDialog extends DialogFragment implements OnClickListener {

  /**
   * Tells whether this dialog should be shown, given the user's preferences and
   * the given puzzle's properties.
   */
  public static boolean isNeeded(Prefs prefs, JsonObject puzzleProperties) {
    return prefs.getProperOnly()
        && puzzleProperties.has(Generator.NUM_SOLUTIONS_KEY)
        && puzzleProperties.get(Generator.NUM_SOLUTIONS_KEY).getAsInt() > 1;
  }

  public void show(FragmentManager fm) {
    super.show(fm, "improperDialog");
  }

  @Override public final Dialog onCreateDialog(Bundle savedInstanceState) {
    return new AlertDialog.Builder(getActivity())
      .setTitle(R.string.dialog_improper_title)
      .setMessage(R.string.dialog_improper_message)
      .setPositiveButton(R.string.button_always, this)
      .setNeutralButton(R.string.button_once, this)
      .setNegativeButton(android.R.string.cancel, this)
      .create();
  }

  @Override public final void onClick(DialogInterface dialog, int which) {
    dialog.dismiss();
    switch (which) {
      case Dialog.BUTTON_NEGATIVE:
        this.canceled();
        break;
      case Dialog.BUTTON_POSITIVE:
        Prefs.instance(getActivity()).setProperOnlyAsync(false);
        // ...and fall through to:
      case Dialog.BUTTON_NEUTRAL:
        this.okayed();
        break;
    }
  }

  @Override public final void onCancel(DialogInterface dialog) {
    this.canceled();
  }

  /**
   * Called after the user has canceled the dialog, and the dialog has been
   * dismissed. Default implementation does nothing.
   */
  protected void canceled() {}

  /**
   * Called after the user has okayed the playing of the improper puzzle,
   * whether or not the proper-only preference has been updated.
   */
  protected abstract void okayed();
}
