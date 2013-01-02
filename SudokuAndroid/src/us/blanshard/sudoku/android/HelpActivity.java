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

import android.os.Bundle;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;

/**
 * Shows context-sensitive help.
 * @author Luke Blanshard
 */
public class HelpActivity extends ActivityBase {
  private WebView mHelpView;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // WebView reads and writes disk at create time.
    ThreadPolicy policy = StrictMode.allowThreadDiskWrites();
    try {
      mHelpView = new WebView(this);
    } finally {
      StrictMode.setThreadPolicy(policy);
    }

    setContentView(mHelpView);
    String page = getIntent().getExtras().getString(Extras.HELP_PAGE);
    loadPage(page);
  }

  private void loadPage(String page) {
    mHelpView.loadUrl("file:///android_asset/help/" + page + ".html");
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.help, menu);
    return true;
  }

  @Override public boolean onPrepareOptionsMenu(Menu menu) {
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      switch (item.getItemId()) {
        case R.id.menu_help:
          item.setVisible(false);
          break;
      }
    }
    return super.onPrepareOptionsMenu(menu);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_about:
        loadPage("about");
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override protected String getHelpPage() {
    // Shouldn't be called.
    return null;
  }

  @Override public void onBackPressed() {
    if (mHelpView.canGoBack())
      mHelpView.goBack();
    else
      finish();
  }
}
