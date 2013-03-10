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

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static us.blanshard.sudoku.android.Json.GSON;

import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.gen.Generator;
import us.blanshard.sudoku.messages.InstallationRpcs;
import us.blanshard.sudoku.messages.PuzzleRpcs;
import us.blanshard.sudoku.messages.Rpc;
import us.blanshard.sudoku.messages.Rpc.Response;

import android.accounts.Account;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

  /**
   * A callback to be notified when any puzzle's stats have been updated from
   * the central server.
   */
  public interface StatsCallback {
    void statsUpdated(long puzzleId);
  }

  public static void addStatsCallback(StatsCallback callback) {
    statsCallbacks.add(new WeakReference<StatsCallback>(callback));
  }

  public static void runStartupTimeOps(Context context) {
    pendingOps.add(new SaveInstallationOp());
    runOp(context, new SaveAllUnsavedAttemptsAndVotesOp(), "Startup time ops started");
  }

  public static void saveInstallationInfo(Context context) {
    runOp(context, new SaveInstallationOp(), "Save installation started");
  }

  public static void saveAttempt(Context context, Database.Attempt attempt) {
    runOp(context, new SaveAttemptOp(attempt), "Saving attempt " + attempt._id);
  }

  public static void saveVote(Context context, long puzzleId) {
    runOp(context, new SaveVoteOp(puzzleId), "Saving vote for " + puzzleId);
  }

  public static void updateOldStats(Context context, long cutoff) {
    runOp(context, new UpdateOldStatsOp(cutoff), "Updating old stats");
  }

  public static void updateStats(Context context, long puzzleId) {
    runOp(context, new UpdateStatsOp(puzzleId), "Updating stats for " + puzzleId);
  }

  private static void runOp(Context context, Op op, String desc) {
    pendingOps.add(op);
    context.startService(new Intent(context, NetworkService.class));
    Log.d(TAG, desc);
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
    TypeToken<T> getResultTypeToken();

    /**
     * Returns the RPC method name.
     */
    String getMethod();

    /**
     * Returns a cost for this RPC, approximating the relative time required
     * to service it.
     */
    int getCost();

    /**
     * Processes the given response from the server. Returns true if the RPC is
     * complete (whether successful or not), false if it should be retried.
     */
    boolean process(Rpc.Response<T> response);
  }

  private static final Queue<Op> pendingOps = Queues.newConcurrentLinkedQueue();
  private static final Collection<WeakReference<StatsCallback>> statsCallbacks =
      Queues.newConcurrentLinkedQueue();

  /** The auth scope that lets the web app verify it's really the given user. */
  private static final String SCOPE = "audience:server:client_id:826990774749.apps.googleusercontent.com";

  private static final String ADD_OP = "us.blanshard.sudoku.android.AddOp";
  private static final int SAVE_INSTALLATION = 1;

  private static final long DEFAULT_RETRY_TIME_MS = SECONDS.toMillis(10);
  private static final long MAX_RETRY_TIME_MS = HOURS.toMillis(1);

  private static final String BASE_URL = "https://sudoku-insight.appspot.com/";
//      "http://10.0.2.2:8888/";  // <-- reaches localhost
  private static final String RPC_URL = BASE_URL + "rpc";

  private static final String TAG = "NetworkService";

  private static final TypeToken<InstallationRpcs.UpdateResult> SET_INSTALLATION_TOKEN =
      new TypeToken<InstallationRpcs.UpdateResult>() {};
  private static final TypeToken<PuzzleRpcs.AttemptResult> SAVE_ATTEMPT_TOKEN =
      new TypeToken<PuzzleRpcs.AttemptResult>() {};
  private static final TypeToken<PuzzleRpcs.VoteResult> SAVE_VOTE_TOKEN =
      new TypeToken<PuzzleRpcs.VoteResult>() {};
  private static final TypeToken<PuzzleRpcs.PuzzleResult> PUZZLE_STATS_TOKEN =
      new TypeToken<PuzzleRpcs.PuzzleResult>() {};
  private static final TypeToken<List<Rpc.Response<Object>>> BATCH_RESULT_TOKEN =
      new TypeToken<List<Rpc.Response<Object>>>() {};
  private static final int WRITE_COST = 8;
  private static final int SAVE_ATTEMPT_COST = 20;
  private static final int READ_COST = 1;
  private static final int MAX_COST = 200;

  private Prefs mPrefs;
  private Database mDb;
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
    mDb = Database.instance(this);

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

  private static class RpcCall<T> {
    RpcOp<T> op;
    Rpc.Request request;
    Rpc.Response<T> response;

    RpcCall(RpcOp<T> op, Object params) {
      this.op = op;
      this.request = new Rpc.Request();
      this.request.method = op.getMethod();
      this.request.params = params;
      this.request.id = sId.incrementAndGet();
    }

    static <T> RpcCall<T> create(RpcOp<T> op, Object params) {
      return new RpcCall<T>(op, params);
    }

    @SuppressWarnings("unchecked")
    void setResponse(Response<?> response) {
      this.response = (Response<T>) response;
    }

    boolean processResponse() {
      return response != null && op.process(response);
    }
  }

  private void processOps() {
    long timeoutMs = DEFAULT_RETRY_TIME_MS;
    Set<RpcOp<?>> pendingRpcOps = Sets.newLinkedHashSet();

    while (!pendingOps.isEmpty() || !pendingRpcOps.isEmpty()) {
      if (!waitUntilConnected())
        break;

      for (Op op; (op = pendingOps.poll()) != null; )
        op.addRpcs(this, pendingRpcOps);

      List<Rpc.Request> batch = Lists.newArrayList();
      Map<Integer, RpcCall<?>> calls = Maps.newLinkedHashMap();
      int cost = 0;

      for (Iterator<RpcOp<?>> it = pendingRpcOps.iterator(); it.hasNext(); ) {
        RpcOp<?> rpcOp = it.next();
        if (cost + rpcOp.getCost() > MAX_COST)
          break;
        it.remove();

        Object params = rpcOp.asRequestParams();
        if (params == null)
          continue;

        cost += rpcOp.getCost();
        RpcCall<?> call = RpcCall.create(rpcOp, params);
        batch.add(call.request);
        calls.put(call.request.id, call);
      }

      if (batch.isEmpty())
        break;

      Log.d(TAG, "Sending " + batch.size() + " RPCs with cost " + cost);

      if (processBatch(batch, calls, pendingRpcOps)) {
        timeoutMs = DEFAULT_RETRY_TIME_MS;
        continue;
      }

      // The RPCs that can be retried have been put back into the pending set.
      // Sleep awhile and try again.
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

  /**
   * Sends a batch RPC, processes their results. Returns true if all the calls
   * are complete, false if some of them should be retried. In the false case,
   * also puts the retriable ones back into the {@code pendingRpcOps} set.
   *
   * @param batch the requests to send
   * @param calls mapping from request ID to call
   * @param pendingRpcOps the set of RPC ops still left to send
   * @return true if none need retrying
   */
  private boolean processBatch(List<Rpc.Request> batch, Map<Integer, RpcCall<?>> calls,
      Set<RpcOp<?>> pendingRpcOps) {
    sendBatch(batch, calls);
    boolean answer = true;
    for (RpcCall<?> call : calls.values()) {
      if (call.processResponse()) {
        if (call.response.error == null)
          Log.d(TAG, "Processed RPC " + call.request.method);
        else
          Log.i(TAG, "Processed failed RPC " + call.request.method + ", error "
              + GSON.toJson(call.response.error));
      } else {
        Log.w(TAG, "Unable to process RPC " + call.request.method + ", will retry");
        pendingRpcOps.add(call.op);
        answer = false;
      }
    }
    return answer;
  }

  private void sendBatch(List<Rpc.Request> batch, final Map<Integer, RpcCall<?>> calls) {
    String json = GSON.toJson(batch);
    Rpc.Response<Object> singleReponse = null;
    HttpURLConnection conn = null;
    try {
      URL url = new URL(RPC_URL);
      conn = (HttpURLConnection) url.openConnection();
      conn.setDoOutput(true);
      conn.setDoInput(true);
      conn.setConnectTimeout((int) SECONDS.toMillis(15));
      conn.setReadTimeout((int) SECONDS.toMillis(60));  // App engine request timeout
      Writer out = new OutputStreamWriter(conn.getOutputStream(), Charsets.UTF_8);
      out.write(json);
      out.flush();
      conn.connect();

      conn.getHeaderFields();  // Waits for the response
      if (url.getHost().equals(conn.getURL().getHost())) {
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
          Reader reader = new InputStreamReader(conn.getInputStream(), Charsets.UTF_8);
          JsonReader in = new JsonReader(reader);
          if (in.peek() == JsonToken.BEGIN_ARRAY) {
            Json.setIdToType(new Function<Integer, Type>() {
              @Override public Type apply(@Nullable Integer input) {
                return calls.get(input).op.getResultTypeToken().getType();
              }
            });
            try {
              List<Rpc.Response<?>> responses = GSON.fromJson(in, BATCH_RESULT_TOKEN.getType());
              Log.d(TAG, "#responses: " + responses.size());
              for (Rpc.Response<?> res : responses) {
                RpcCall<?> call = calls.get(res.id);
                if (call == null) {
                  Log.w(TAG, "Mismatched ID in batch response: " + res.id);
                } else {
                  call.setResponse(res);
                }
              }
            } finally {
              Json.setIdToType(null);
            }
          } else {
            // Probably just a single error, something bad happened.
            singleReponse = GSON.fromJson(in, Rpc.Response.class);
            if (singleReponse.error == null)
              singleReponse.error = Rpc.error(0, "unknown", null);
            Log.d(TAG, "RPC batch returned single response: " + GSON.toJson(singleReponse));
          }
        } else {
          // Unexpected error from the server. Make a response object, so we
          // don't retry.
          singleReponse = new Rpc.Response<Object>();
          singleReponse.error = Rpc.error(conn.getResponseCode(), conn.getResponseMessage(), null);
        }
        if (singleReponse != null) {
          for (RpcCall<?> call : calls.values())
            call.setResponse(singleReponse);
        }
      } else {
        // Whoops, we were redirected unexpectedly. Possibly there's a
        // network sign-on page.
        Log.e(TAG, "redirected trying to send RPC: " + conn.getURL());
      }
    } catch (Exception e) {
      Log.e(TAG, "send RPC batch", e);
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private static boolean shouldRetry(Rpc.Error error) {
    return error != null && error.code == Rpc.RETRIABLE_ERROR;
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

  /**
   * The RPC op for saving the installation's info. All instances compare equal,
   * to fold multiple requests to save into a single RPC. In addition, this
   * class verifies that there is some difference between what is currently
   * saved and the new data, before actually sending an RPC.
   */
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
        // Delink the account from the installation. This isn't great, but it's
        // preferable to not being able to save the installation at all.
        params.account = null;
        savedJson = GSON.toJson(params);
        if (savedJson.equals(prev))
          return null;
      }

      // Clear out our notion of what's been synced with the server.
      mPrefs.setInstallDataSync("");
      return params;
    }

    @Override public TypeToken<InstallationRpcs.UpdateResult> getResultTypeToken() {
      return SET_INSTALLATION_TOKEN;
    }

    @Override public String getMethod() {
      return InstallationRpcs.UPDATE_METHOD;
    }

    @Override public int getCost() {
      return WRITE_COST;
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
      return !shouldRetry(res.error);
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
      params.androidSdk = Build.VERSION.SDK_INT;
      params.androidAppVersion = getAppVersion();
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

    private int getAppVersion() {
      PackageManager pm = NetworkService.this.getPackageManager();
      try {
        PackageInfo info = pm.getPackageInfo("us.blanshard.sudoku.android", 0);
        return info.versionCode;
      } catch (NameNotFoundException e) {
        Log.e(TAG, "our package not found", e);
        return 0;
      }
    }
  }

  /**
   * An Op for the startup-time task of bringing all attempts and votes up to
   * date in the server.
   */
  private static class SaveAllUnsavedAttemptsAndVotesOp implements Op {
    @Override public void addRpcs(NetworkService svc, Set<RpcOp<?>> rpcs) {
      for (Database.Attempt attempt : svc.mDb.getUnsavedAttempts())
        rpcs.add(svc.new SaveAttemptRpcOp(attempt));
      for (Database.Puzzle puzzle : svc.mDb.getPuzzlesWithUnsavedVotes())
        rpcs.add(svc.new SaveVoteRpcOp(puzzle));
    }
  }

  /**
   * An Op for saving a particular attempt.
   */
  private static class SaveAttemptOp implements Op {
    private final Database.Attempt attempt;

    public SaveAttemptOp(Database.Attempt attempt) {
      this.attempt = attempt;
    }

    @Override public void addRpcs(NetworkService svc, Set<RpcOp<?>> rpcs) {
      rpcs.add(svc.new SaveAttemptRpcOp(attempt));
    }
  }

  /**
   * An Op for saving a particular vote.
   */
  private static class SaveVoteOp implements Op {
    private final long puzzleId;

    public SaveVoteOp(long puzzleId) {
      this.puzzleId = puzzleId;
    }

    @Override public void addRpcs(NetworkService svc, Set<RpcOp<?>> rpcs) {
      rpcs.add(svc.new SaveVoteRpcOp(svc.mDb.getFullPuzzle(puzzleId)));
    }
  }

  /**
   * The RPC op that saves an attempt.
   */
  private class SaveAttemptRpcOp implements RpcOp<PuzzleRpcs.AttemptResult> {
    private final Database.Attempt attempt;

    public SaveAttemptRpcOp(Database.Attempt attempt) {
      this.attempt = attempt;
    }

    @Override public PuzzleRpcs.AttemptParams asRequestParams() {
      if (!attempt.attemptState.isComplete()) {
        Log.e(TAG, "Trying to save an incomplete attempt " + attempt._id
            + ": " + attempt.attemptState);
        return null;
      }
      PuzzleRpcs.AttemptParams params = new PuzzleRpcs.AttemptParams();
      params.installationId = Installation.id(getApplicationContext());
      params.attemptId = attempt._id;
      params.puzzle = attempt.clues.toFlatString();
      params.puzzleId = attempt.puzzleId;
      JsonObject props = new JsonParser().parse(attempt.properties).getAsJsonObject();
      if (props.has(Generator.NAME_KEY))
        params.name = props.get(Generator.NAME_KEY).getAsString();
      if (props.has(Generator.SOURCE_KEY))
        params.source = props.get(Generator.SOURCE_KEY).getAsString();
      params.history = GameJson.toHistory(Json.GSON, attempt.history);
      params.elapsedMs = attempt.elapsedMillis;
      params.stopTime = attempt.lastTime;
      return params;
    }

    @Override public TypeToken<PuzzleRpcs.AttemptResult> getResultTypeToken() {
      return SAVE_ATTEMPT_TOKEN;
    }

    @Override public String getMethod() {
      return PuzzleRpcs.ATTEMPT_UPDATE_METHOD;
    }

    @Override public int getCost() {
      return SAVE_ATTEMPT_COST;
    }

    @Override public boolean process(Rpc.Response<PuzzleRpcs.AttemptResult> res) {
      if (res.result != null) {
        mDb.markAttemptSaved(attempt._id, attempt.lastTime);
      }
      return !shouldRetry(res.error);
    }

    @Override public boolean equals(Object o) {
      if (o instanceof SaveAttemptRpcOp) {
        SaveAttemptRpcOp that = (SaveAttemptRpcOp) o;
        return this.attempt._id == that.attempt._id;
      }
      return false;
    }

    @Override public int hashCode() {
      return (int) attempt._id * 327852198 + 1283764;
    }
  }

  /**
   * The RPC op that saves a vote.
   */
  private class SaveVoteRpcOp implements RpcOp<PuzzleRpcs.VoteResult> {
    private final Database.Puzzle puzzle;

    public SaveVoteRpcOp(Database.Puzzle puzzle) {
      this.puzzle = puzzle;
    }

    @Override public PuzzleRpcs.VoteParams asRequestParams() {
      PuzzleRpcs.VoteParams params = new PuzzleRpcs.VoteParams();
      params.installationId = Installation.id(getApplicationContext());
      params.puzzle = puzzle.clues.toFlatString();
      params.vote = puzzle.vote;
      return params;
    }

    @Override public TypeToken<PuzzleRpcs.VoteResult> getResultTypeToken() {
      return SAVE_VOTE_TOKEN;
    }

    @Override public String getMethod() {
      return PuzzleRpcs.VOTE_UPDATE_METHOD;
    }

    @Override public int getCost() {
      return WRITE_COST;
    }

    @Override public boolean process(Rpc.Response<PuzzleRpcs.VoteResult> res) {
      if (res.result != null) {
        mDb.markVoteSaved(puzzle._id, puzzle.vote);
      }
      return !shouldRetry(res.error);
    }

    @Override public boolean equals(Object o) {
      if (o instanceof SaveVoteRpcOp) {
        SaveVoteRpcOp that = (SaveVoteRpcOp) o;
        return this.puzzle._id == that.puzzle._id;
      }
      return false;
    }

    @Override public int hashCode() {
      return (int) puzzle._id * 872634587 + 2384675;
    }
  }

  /**
   * An Op for updating all the puzzle stats last updated before a particular
   * point in time.
   */
  private static class UpdateOldStatsOp implements Op {
    private final long cutoff;

    public UpdateOldStatsOp(long cutoff) {
      this.cutoff = cutoff;
    }

    @Override public void addRpcs(NetworkService svc, Set<RpcOp<?>> rpcs) {
      for (Database.Puzzle puzzle : svc.mDb.getPuzzlesWithOldStats(cutoff))
        rpcs.add(svc.new UpdateStatsRpcOp(puzzle));
    }
  }

  /**
   * An Op for updating the stats for a particular puzzle.
   */
  private static class UpdateStatsOp implements Op {
    private final long puzzleId;

    public UpdateStatsOp(long puzzleId) {
      this.puzzleId = puzzleId;
    }

    @Override public void addRpcs(NetworkService svc, Set<RpcOp<?>> rpcs) {
      rpcs.add(svc.new UpdateStatsRpcOp(svc.mDb.getFullPuzzle(puzzleId)));
    }
  }

  /**
   * The RPC op that fetches the stats about a puzzle and saves them to the
   * database.
   */
  private class UpdateStatsRpcOp implements RpcOp<PuzzleRpcs.PuzzleResult> {
    private final Database.Puzzle puzzle;

    public UpdateStatsRpcOp(Database.Puzzle puzzle) {
      this.puzzle = puzzle;
    }

    @Override public PuzzleRpcs.PuzzleParams asRequestParams() {
      PuzzleRpcs.PuzzleParams params = new PuzzleRpcs.PuzzleParams();
      params.puzzle = puzzle.clues.toFlatString();
      if (puzzle.stats != null) {
        PuzzleRpcs.PuzzleResult stats = GSON.fromJson(puzzle.stats, PuzzleRpcs.PuzzleResult.class);
        params.previousStatsTimestamp = stats.statsTimestamp;
      }
      return params;
    }

    @Override public TypeToken<PuzzleRpcs.PuzzleResult> getResultTypeToken() {
      return PUZZLE_STATS_TOKEN;
    }

    @Override public String getMethod() {
      return PuzzleRpcs.PUZZLE_GET_METHOD;
    }

    @Override public int getCost() {
      return READ_COST;
    }

    @Override public boolean process(Rpc.Response<PuzzleRpcs.PuzzleResult> res) {
      if (res.result != null) {
        mDb.setPuzzleStats(puzzle._id, GSON.toJson(res.result));
        Iterator<WeakReference<StatsCallback>> it = statsCallbacks.iterator();
        while (it.hasNext()) {
          StatsCallback callback = it.next().get();
          if (callback == null)
            it.remove();
          else
            callback.statsUpdated(puzzle._id);
        }
      } else if (res.error.code == Rpc.OBJECT_UNCHANGED) {
        // Just update the timestamp.
        mDb.setPuzzleStats(puzzle._id, puzzle.stats);
      }
      return !shouldRetry(res.error);
    }

    @Override public boolean equals(Object o) {
      if (o instanceof UpdateStatsRpcOp) {
        UpdateStatsRpcOp that = (UpdateStatsRpcOp) o;
        return this.puzzle._id == that.puzzle._id;
      }
      return false;
    }

    @Override public int hashCode() {
      return (int) puzzle._id * 384763457 + 348567121;
    }
  }
}
