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
import static us.blanshard.sudoku.android.Json.GSON;

import us.blanshard.sudoku.messages.InstallationRpcs;
import us.blanshard.sudoku.messages.InstallationRpcs.UpdateParams;
import us.blanshard.sudoku.messages.Rpc;

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

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.common.base.Charsets;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The "started service" that interacts with our AppEngine server over the
 * network.
 *
 * @author Luke Blanshard
 */
public class NetworkService extends IntentService {

  public static void syncInstallationInfo(Context context) {
    Intent intent = makeSyncInstallationIntent(context);
    context.startService(intent);
    Log.d(TAG, "Sync installation started");
  }

  private static Intent makeSyncInstallationIntent(Context context) {
    Intent intent = new Intent(context, NetworkService.class);
    intent.putExtra(CALL, INSTALLATION);
    return intent;
  }

  private static class GiveUpException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public GiveUpException(Throwable cause) {
      super(cause);
    }
  }

  /** The auth scope that lets the web app verify it's really the given user. */
  private static final String SCOPE = "audience:server:client_id:826990774749.apps.googleusercontent.com";

  private static final String CALL = "us.blanshard.sudoku.android.Call";
  private static final int INSTALLATION = 1;

  private static final String BASE_URL = "https://sudoku-insight.appspot.com/";
//      "http://10.0.2.2:8888/";  // <-- reaches localhost
  private static final String RPC_URL = BASE_URL + "rpc";

  private static final String TAG = "NetworkService";

  private static final TypeToken<Rpc.Response<InstallationRpcs.UpdateResult>> SET_INSTALLATION_TOKEN =
      new TypeToken<Rpc.Response<InstallationRpcs.UpdateResult>>() {};

  private Prefs mPrefs;
  private final Object mConnectivityLock = new Object();
  private boolean mConnected;
  private BroadcastReceiver mConnectivityMonitor;

  private static final AtomicInteger sId = new AtomicInteger();

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
    try {
      switch (intent.getIntExtra(CALL, -1)) {
        case INSTALLATION:
          callUpdateInstallation();
          break;
        default:
          break;
      }
    } catch (GiveUpException e) {
      Log.d(TAG, "giving up", e);
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

  private void callUpdateInstallation() {
    long timeoutMs = SECONDS.toMillis(10);
    while (waitUntilConnected()) {
      UpdateParams params = makeInstallationParams();
      String json = GSON.toJson(params);
      String prev = mPrefs.getInstallData();

      if (json.equals(prev) || sendUpdateInstallation(json, params)) {
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

  private InstallationRpcs.UpdateParams makeInstallationParams() {
    InstallationRpcs.UpdateParams params = new InstallationRpcs.UpdateParams();
    params.id = Installation.id(this);
    params.shareData = mPrefs.getShareData();
    params.manufacturer = Build.MANUFACTURER;
    params.model = Build.MODEL;
    params.streamCount = mPrefs.getStreamCount();
    params.stream = mPrefs.getStream();
    Account account = mPrefs.getUserAccount();
    if (account != null) {
      params.account = new InstallationRpcs.AccountInfo();
      params.account.id = account.name;
      params.account.installationName = mPrefs.getDeviceName();
    }
    return params;
  }

  private boolean sendUpdateInstallation(String storedJson, UpdateParams params) {
    try {
      if (params.account != null) {
        params.account.authToken = GoogleAuthUtil.getTokenWithNotification(
            this, params.account.id, SCOPE, null, makeSyncInstallationIntent(this));
      }
    } catch (IOException e) {
      return false;
    } catch (GoogleAuthException e) {
      throw new GiveUpException(e);
    }

    // Clear out our notion of what's been synced with the server.
    mPrefs.setInstallDataSync("");
    Rpc.Response<InstallationRpcs.UpdateResult> res =
        sendRpc(InstallationRpcs.UPDATE_METHOD, params, SET_INSTALLATION_TOKEN);

    if (res != null && res.result != null) {
      mPrefs.setInstallDataSync(storedJson);
      if (res.result.installationName != null)
        mPrefs.setDeviceNameAsync(res.result.installationName);
      mPrefs.setStreamAsync(res.result.stream);
      mPrefs.setStreamCountAsync(res.result.streamCount);
    }
    return res != null;
  }

  private <T> Rpc.Response<T> sendRpc(String method, Object params, TypeToken<Rpc.Response<T>> token) {
    Rpc.Request req = new Rpc.Request();
    req.method = method;
    req.params = params;
    req.id = sId.incrementAndGet();

    String json = GSON.toJson(req);
    HttpURLConnection conn = null;
    Rpc.Response<T> res = null;
    try {
      URL url = new URL(RPC_URL);
      conn = (HttpURLConnection) url.openConnection();
      conn.setDoOutput(true);
      conn.setDoInput(true);
      conn.setConnectTimeout((int) SECONDS.toMillis(15));
      conn.setReadTimeout((int) SECONDS.toMillis(10));
      Writer out = new OutputStreamWriter(conn.getOutputStream(), Charsets.UTF_8);
      out.write(json);
      out.flush();
      conn.connect();

      conn.getHeaderFields();  // Waits for the response
      if (url.getHost().equals(conn.getURL().getHost())) {
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
          Reader in = new InputStreamReader(conn.getInputStream(), Charsets.UTF_8);
          res = GSON.fromJson(in, token.getType());
          if (!req.id.equals(res.id))
            Log.d(TAG, "RPC completed with wrong req ID: " + res.id + ", s/b " + req.id);
        } else {
          // Unexpected error from the server. Make a response object, so we
          // don't retry.
          res = new Rpc.Response<T>();
          res.error = Rpc.error(conn.getResponseCode(), conn.getResponseMessage(), null);
        }
        if (res.result == null) {
          Log.w(TAG, "RPC " + method + " failed: " + GSON.toJson(res.error));
        }
      } else {
        // Whoops, we were redirected unexpectedly. Possibly there's a
        // network sign-on page.
        Log.e(TAG, "redirected trying to send RPC: " + conn.getURL());
      }
    } catch (IOException e) {
      Log.e(TAG, "send RPC", e);
    } finally {
      if (conn != null) conn.disconnect();
    }
    return res;
  }
}
