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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

/**
 * Shows details of insights about the current game.
 *
 * @author Luke Blanshard
 */
public class InsightsFragment extends DialogFragment {

  public static InsightsFragment newInstance(String message) {
    InsightsFragment answer = new InsightsFragment();
    Bundle args = new Bundle();
    args.putString("message", message);
    answer.setArguments(args);
    return answer;
  }

  @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
    return new AlertDialog.Builder(getActivity())
        .setTitle(R.string.dialog_insights_title)
        .setMessage(getArguments().getString("message"))
        .setNeutralButton(R.string.button_done, new OnClickListener() {
          @Override public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
          }
        })
        .create();
  }
}
