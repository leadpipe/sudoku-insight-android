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

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static us.blanshard.sudoku.game.GameJson.GSON;

import us.blanshard.sudoku.messages.InstallationInfo;

import android.accounts.Account;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import com.google.common.base.Charsets;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The "started service" that interacts with our AppEngine server over the
 * network.
 *
 * @author Luke Blanshard
 */
public class NetworkService extends IntentService {

  public static void syncInstallationInfo(Context context) {
    Intent intent = new Intent(context, NetworkService.class);
    intent.putExtra(CALL, INSTALLATION);
    context.startService(intent);
    Log.d(TAG, "Sync installation started");
  }

  private static final String CALL = "us.blanshard.sudoku.android.Call";
  private static final int INSTALLATION = 1;

  private static final String BASE_URL = "https://sudoku-insight.appspot.com/";
  // "http://10.0.2.2:8888/";  // <-- reaches localhost
  private static final String SET_INSTALL_URL = BASE_URL + "installation";

  private static final String TAG = "NetworkService";

  private Prefs mPrefs;
  private final Object mConnectivityLock = new Object();
  private boolean mConnected;
  private BroadcastReceiver mConnectivityMonitor;

  public NetworkService() {
    super(TAG);
  }

  @Override public void onCreate() {
    super.onCreate();

    mPrefs = Prefs.instance(this);

    // Set up our connectivity monitoring.
    IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    mConnectivityMonitor = new BroadcastReceiver() {
      @Override public void onReceive(Context context, Intent intent) {
        updateConnectivity();
      }
    };
    registerReceiver(mConnectivityMonitor, filter);

    // And establish the base state.
    updateConnectivity();
  }

  @Override public void onDestroy() {
    super.onDestroy();
    unregisterReceiver(mConnectivityMonitor);
  }

  @Override protected void onHandleIntent(Intent intent) {
    switch (intent.getIntExtra(CALL, -1)) {
      case INSTALLATION:
        callSetInstallation();
        break;
      default:
        break;
    }
  }

  private void updateConnectivity() {
    ConnectivityManager connMgr =
        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
    synchronized (mConnectivityLock) {
      mConnected = networkInfo != null && networkInfo.isConnected();
      if (mConnected) mConnectivityLock.notifyAll();
    }
  }

  private void callSetInstallation() {
    long timeoutMs = SECONDS.toMillis(10);
    while (waitUntilConnected()) {
      String json = GSON.toJson(makeInstallationRequest());
      String prev = mPrefs.getInstallData();

      if (json.equals(prev) || sendSetInstallation(json)) {
        break;
      }

      try {
        Thread.sleep(timeoutMs);
        timeoutMs = Math.min(HOURS.toMillis(1), timeoutMs * 3);
      } catch (InterruptedException e) {
        // Just stop if we're interrupted.
        break;
      }
    }
  }

  private boolean waitUntilConnected() {
    synchronized (mConnectivityLock) {
      while (!mConnected)
        try {
          mConnectivityLock.wait();
        } catch (InterruptedException e) {
          return false;
        }
    }
    return true;
  }

  private InstallationInfo.UpdateRequest makeInstallationRequest() {
    InstallationInfo.UpdateRequest req = new InstallationInfo.UpdateRequest();
    req.id = Installation.id(this);
    Account account = mPrefs.getUserAccount();
    if (account != null) {
      req.accountId = account.name;
      req.name = mPrefs.getDeviceName();
    }
    req.shareData = mPrefs.getShareData();
    req.manufacturer = Build.MANUFACTURER;
    req.model = Build.MODEL;
    req.streamCount = mPrefs.getStreamCount();
    req.stream = mPrefs.getStream();
    return req;
  }

  private boolean sendSetInstallation(String json) {
    boolean done = false;
    HttpURLConnection conn = null;
    try {
      URL url = new URL(SET_INSTALL_URL);
      conn = (HttpURLConnection) url.openConnection();
      conn.setDoOutput(true);
      conn.setDoInput(true);
      conn.setConnectTimeout((int) SECONDS.toMillis(15));
      conn.setReadTimeout((int) SECONDS.toMillis(10));
      Writer out = new OutputStreamWriter(conn.getOutputStream(), Charsets.UTF_8);
      out.write(json);
      out.flush();
      conn.connect();

      // Clear out our notion of what's been synced with the server.  It
      // could be old or new at this point.
      mPrefs.setInstallDataSync("");

      conn.getHeaderFields();  // Waits for the response
      if (url.getHost().equals(conn.getURL().getHost())) {
        // We got through, so we won't try again, even if it failed.
        done = true;
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
          mPrefs.setInstallDataSync(json);
          Reader in = new InputStreamReader(conn.getInputStream(), Charsets.UTF_8);
          InstallationInfo.UpdateResponse resp = GSON.fromJson(in, InstallationInfo.UpdateResponse.class);
          if (resp.name != null) mPrefs.setDeviceNameAsync(resp.name);
          mPrefs.setStreamAsync(resp.stream);
          mPrefs.setStreamCountAsync(resp.streamCount);
          Log.d(TAG, "SetInstallation completed");
        } else {
          // Unexpected error from the server.
          Log.e(TAG, "SetInstallation returned code " + conn.getResponseCode());
        }
      } else {
        // Whoops, we were redirected unexpectedly. Possibly there's a
        // network sign-on page.
        Log.e(TAG, "redirected trying to set installation info: " + conn.getURL());
      }
    } catch (IOException e) {
      Log.e(TAG, "set installation", e);
    } finally {
      if (conn != null) conn.disconnect();
    }
    return done;
  }
}
