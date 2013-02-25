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

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.common.base.Charsets;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

/**
 * The "started service" that interacts with our AppEngine server over the
 * network.
 *
 * @author Luke Blanshard
 */
public class NetworkService extends IntentService {

  public static void syncInstallationInfo(Context context) {
    pendingOps.add(new SaveInstallationOp());
    context.startService(new Intent(context, NetworkService.class));
    Log.d(TAG, "Sync installation started");
  }

  /**
   * A high-level operation enqueued by the user-facing app for the network
   * service to handle at the appropriate time.
   */
  private interface Op {
    /**
     * Calculates the RPCs required to perform this operation, adds them to the
     * given set.  There may be nothing to do.
     */
    void addRpcs(NetworkService svc, Set<RpcOp<?>> rpcs);
  }

  /**
   * An operation corresponding to a single RPC to send and process.
   */
  private interface RpcOp<T> {
    /**
     * Converts this object to the RPC request parameters it corresponds to, if
     * it is still needed.
     */
    @Nullable Object asRequestParams();

    /**
     * Exposes an object to guide the JSON parsing of the RPC response.
     */
    TypeToken<Rpc.Response<T>> getResponseTypeToken();

    /**
     * Returns the RPC method name.
     */
    String getMethod();

    /**
     * Processes the given response from the server. Returns true if the RPC is
     * complete (whether successful or not), false if it should be retried.
     */
    boolean process(Rpc.Response<T> response);
  }

  private static final Queue<Op> pendingOps = Queues.newConcurrentLinkedQueue();

  /** The auth scope that lets the web app verify it's really the given user. */
  private static final String SCOPE = "audience:server:client_id:826990774749.apps.googleusercontent.com";

  private static final String ADD_OP = "us.blanshard.sudoku.android.AddOp";
  private static final int SAVE_INSTALLATION = 1;

  private static final long DEFAULT_RETRY_TIME_MS = SECONDS.toMillis(10);
  private static final long MAX_RETRY_TIME_MS = HOURS.toMillis(1);

  private static final String BASE_URL = //"https://sudoku-insight.appspot.com/";
      "http://10.0.2.2:8888/";  // <-- reaches localhost
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
    switch (intent.getIntExtra(ADD_OP, -1)) {
      case SAVE_INSTALLATION:
        pendingOps.add(new SaveInstallationOp());
        break;
      default:
        break;
    }
    try {
      processOps();
    } catch (Throwable t) {
      Log.d(TAG, "uncaught processing network ops", t);
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

  private void processOps() {
    long timeoutMs = DEFAULT_RETRY_TIME_MS;
    Set<RpcOp<?>> rpcOps = Sets.newLinkedHashSet();

    while (!pendingOps.isEmpty() || !rpcOps.isEmpty()) {
      if (!waitUntilConnected())
        break;

      for (Op op; (op = pendingOps.poll()) != null; )
        op.addRpcs(this, rpcOps);

      if (rpcOps.isEmpty())
        break;

      RpcOp<?> rpcOp = rpcOps.iterator().next();
      rpcOps.remove(rpcOp);

      Object params = rpcOp.asRequestParams();
      if (params == null)
        continue;

      if (processRpc(rpcOp, params)) {
        timeoutMs = DEFAULT_RETRY_TIME_MS;
        continue;
      }

      try {
        Thread.sleep(timeoutMs);
        timeoutMs = Math.min(MAX_RETRY_TIME_MS, timeoutMs * 3);
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

  private <T> boolean processRpc(RpcOp<T> rpcOp, Object params) {
    Rpc.Response<T> response = sendRpc(rpcOp.getMethod(), params, rpcOp.getResponseTypeToken());
    if (response == null)
      return false;
    return rpcOp.process(response);
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
      conn.setReadTimeout((int) DEFAULT_RETRY_TIME_MS);
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
    } catch (Exception e) {
      Log.e(TAG, "send RPC", e);
    } finally {
      if (conn != null) conn.disconnect();
    }
    return res;
  }

  // Op and RpcOp classes

  /**
   * The high-level operation for saving this installation's info to the server.
   */
  private static class SaveInstallationOp implements Op {

    @Override public void addRpcs(NetworkService svc, Set<RpcOp<?>> rpcs) {
      // We always add the RpcOp, and figure out whether it needs sending only
      // when about to send it.
      rpcs.add(svc.new SaveInstallationRpcOp());
    }
  }

  private class SaveInstallationRpcOp implements RpcOp<InstallationRpcs.UpdateResult> {
    private String savedJson;

    @Override public InstallationRpcs.UpdateParams asRequestParams() {
      InstallationRpcs.UpdateParams params = makeInstallationParams();
      savedJson = GSON.toJson(params);
      String prev = mPrefs.getInstallData();
      if (savedJson.equals(prev))
        return null;

      try {
        if (params.account != null) {
          Intent intent = new Intent(NetworkService.this, NetworkService.class);
          intent.putExtra(ADD_OP, SAVE_INSTALLATION);
          params.account.authToken = GoogleAuthUtil.getTokenWithNotification(
              NetworkService.this, params.account.id, SCOPE, null, intent);
        }
      } catch (Exception e) {
        Log.e(TAG, "problem getting auth token", e);
        return null;
      }

      // Clear out our notion of what's been synced with the server.
      mPrefs.setInstallDataSync("");
      return params;
    }

    @Override public TypeToken<Rpc.Response<InstallationRpcs.UpdateResult>> getResponseTypeToken() {
      return SET_INSTALLATION_TOKEN;
    }

    @Override public String getMethod() {
      return InstallationRpcs.UPDATE_METHOD;
    }

    @Override public boolean process(Rpc.Response<InstallationRpcs.UpdateResult> res) {
      if (res.result != null) {
        mPrefs.setInstallDataSync(savedJson);
        if (res.result.installationName != null)
          mPrefs.setDeviceNameAsync(res.result.installationName);
        mPrefs.setStreamAsync(res.result.stream);
        // Setting the stream count has to come after the stream itself:
        mPrefs.setStreamCountAsync(res.result.streamCount);
      }
      return true;
    }

    @Override public boolean equals(Object o) {
      return o instanceof SaveInstallationRpcOp;
    }

    @Override public int hashCode() {
      return SaveInstallationRpcOp.class.hashCode();
    }

    private InstallationRpcs.UpdateParams makeInstallationParams() {
      InstallationRpcs.UpdateParams params = new InstallationRpcs.UpdateParams();
      params.id = Installation.id(NetworkService.this);
      params.shareData = mPrefs.getShareData();
      params.manufacturer = Build.MANUFACTURER;
      params.model = Build.MODEL;
      params.streamCount = mPrefs.getStreamCount();
      params.stream = mPrefs.getStream();
      params.monthNumber = mPrefs.getMonthNumber();
      Account account = mPrefs.getUserAccount();
      if (account != null) {
        params.account = new InstallationRpcs.AccountInfo();
        params.account.id = account.name;
        params.account.installationName = mPrefs.getDeviceName();
      }
      return params;
    }
  }
}
