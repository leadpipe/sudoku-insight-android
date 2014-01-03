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

import us.blanshard.sudoku.android.Database.Puzzle;
import us.blanshard.sudoku.android.Database.RatingInfo;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.gen.Generator;
import us.blanshard.sudoku.insight.Evaluator;
import us.blanshard.sudoku.insight.Rating;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * The "started service" that rates puzzles in the background.
 */
public class RatingService extends Service {

  /**
   * A callback to be notified when any puzzle's rating has been updated or
   * completed.
   */
  public interface RatingCallback {
    // Only called during #ratePuzzle's puzzle rating.
    void ratingScoreUpdated(long puzzleId, double minScore);

    // Called for all puzzles rated by ID.
    void ratingComplete(long puzzleId, Rating rating);
  }

  /** Starts the rating service. */
  public static void start(Context context) {
    Log.d(TAG, "Service started");
    context.startService(new Intent(context, RatingService.class));
  }

  /** Adds a callback that will be notified whenever a puzzle has been rated or
      its score updated. */
  public static void addCallback(RatingCallback callback) {
    Log.d(TAG, "Adding callback " + callback);
    sCallbacks.add(callback);
  }

  /** Tells the service to rate the puzzle with the given ID. */
  public static void ratePuzzle(Context context, long puzzleId) {
    Log.d(TAG, "Asked to rate puzzle " + puzzleId);
    Intent intent = new Intent(context, RatingService.class);
    intent.putExtra(Extras.PUZZLE_ID, puzzleId);
    context.startService(intent);
  }

  /** Looks up the {@link Generator} properties of the puzzle with the given
      name, if they are known to the rating service. */
  @Nullable public static JsonObject lookUpProperties(String name) {
    return sPuzzleProperties.get(name);
  }

  private final class ServiceHandler extends Handler {
    public ServiceHandler(Looper looper) {
      super(looper);
    }

    @Override public void handleMessage(Message msg) {
      ratePuzzles();

      // Stop the service using the startId, so that we don't stop the service
      // in the middle of handling another job.
      stopSelfResult(msg.arg1);
    }
  }

  private static final WeakCallbackCollection<RatingCallback> sCallbacks =
      WeakCallbackCollection.create();
  private static final Map<String, JsonObject> sPuzzleProperties =
      Maps.newConcurrentMap();

  private static final String TAG = "RatingService";
  private static final int NUM_PRECOMPUTES = 15;  // how many ratings to calculate ahead

  private Prefs mPrefs;
  private Database mDb;
  private HandlerThread mThread;
  private ServiceHandler mServiceHandler;

  private final Map<String, Rating> mRatings = Maps.newHashMap();

  private final Object mLock = new Object();
  private long mNewPuzzleId = -1;  // Set by UI thread, cleared by rating thread
  private long mCurrentPuzzleId = -1;  // Written by rating thread

  public RatingService() {
    super();
  }

  @Override public void onCreate() {
    super.onCreate();

    mPrefs = Prefs.instance(this);
    mDb = Database.instance(this);
    mThread = new HandlerThread("RatingService", Process.THREAD_PRIORITY_BACKGROUND);
    mThread.start();
    mServiceHandler = new ServiceHandler(mThread.getLooper());
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent.hasExtra(Extras.PUZZLE_ID)) {
      synchronized (mLock) {
        long puzzleId = intent.getExtras().getLong(Extras.PUZZLE_ID);
        if (mCurrentPuzzleId != puzzleId) {
          mNewPuzzleId = puzzleId;
          mThread.interrupt();
          Log.d(TAG, "Interrupted rating thread to do " + mNewPuzzleId + " instead of " + mCurrentPuzzleId);
        }
      }
    }

    Message msg = mServiceHandler.obtainMessage();
    msg.arg1 = startId;
    mServiceHandler.sendMessage(msg);

    return START_STICKY;
  }

  @Override public IBinder onBind(Intent intent) {
    // We aren't that kind of service.
    return null;
  }

  private abstract static class RatingJob {
    Rating rating;

    // Rates the puzzle, saves the result in the appropriate place in the
    // database.
    abstract void rate();

    boolean wasInterrupted() {
      return rating == null || !rating.evalComplete;
    }
  }

  private class NameRatingJob extends RatingJob {
    private final String name;

    NameRatingJob(String name) {
      this.name = name;
    }

    @Override
    void rate() {
      rating = rateNamedPuzzle(name);
    }
  }

  private class IdRatingJob extends RatingJob {
    private long puzzleId;

    IdRatingJob(long puzzleId) {
      this.puzzleId = puzzleId;
    }

    @Override
    void rate() {
      rating = ratePuzzle(puzzleId);
    }
  }

  private void ratePuzzles() {
    List<RatingJob> jobs = getRatingJobs();
    for (RatingJob job : jobs) {
      job.rate();
      if (job.wasInterrupted())
        return;  // Early return: we'll start over with the puzzle we were just asked for.
    }

    // Here, we made it all the way through the list without being interrupted.
    // Note this, so we can reduce our workload next time.
    mPrefs.setRatingVersionAsync(Evaluator.CURRENT_VERSION);
  }

  private List<RatingJob> getRatingJobs() {
    List<RatingJob> jobs = Lists.newArrayList();
    addNewPuzzleIdJob(jobs);
    addUpcomingPuzzleJobs(jobs);
    addOldPuzzleJobs(jobs);
    return jobs;
  }

  // If we've been asked to rate a new puzzle, add a job for it.
  private void addNewPuzzleIdJob(List<RatingJob> jobs) {
    synchronized (mLock) {
      Thread.interrupted();  // Clear any interruption
      if (mNewPuzzleId != -1) {
        Log.d(TAG, "Found new puzzle ID to rate: " + mNewPuzzleId);
        mCurrentPuzzleId = mNewPuzzleId;
        jobs.add(new IdRatingJob(mNewPuzzleId));
      } else {
        Log.d(TAG, "No new puzzle ID to rate");
      }
    }
  }

  // For puzzles that will soon be generated, make jobs that will rate them
  // early.  Also, trim the database table if it has ratings that are no longer
  // in our future.  And fill a couple of caches with relevant information.
  private void addUpcomingPuzzleJobs(List<RatingJob> jobs) {
    Set<String> names = Sets.newLinkedHashSet();
    int stream = mPrefs.getStream();
    Calendar cal = Calendar.getInstance();
    int counter = Math.max(1, mPrefs.getNextCounter(cal) - 1);
    for (int i = 0; i < NUM_PRECOMPUTES; ++i) {
      // We precompute a set number of puzzles for the current month...
      names.add(Generator.makePuzzleName(stream, cal, counter + i));
    }
    cal.add(Calendar.MONTH, 1);
    for (int i = 0; i < NUM_PRECOMPUTES; ++i) {
      // ...and also for the next month.
      names.add(Generator.makePuzzleName(stream, cal, 1 + i));
    }

    mRatings.clear();
    List<RatingInfo> toDelete = Lists.newArrayList();
    for (RatingInfo info : mDb.getRatingInfo()) {
      if (!isValidRating(info.rating))
        continue;
      String name = info.properties.get(Generator.NAME_KEY).getAsString();
      if (names.contains(name)) {
        // This one is still in our future, hang on to it.
        sPuzzleProperties.put(name, info.properties);
        mRatings.put(name, info.rating);
        names.remove(name);
      } else {
        // This one has become obsolete.
        sPuzzleProperties.remove(name);
        toDelete.add(info);
      }
    }

    if (!toDelete.isEmpty())
      mDb.deleteRatingInfo(toDelete);

    // Anything left in names is the upcoming puzzles we need to generate and
    // rate.
    for (String name : names)
      jobs.add(new NameRatingJob(name));
  }

  // For puzzles in the database, ensure they are all rated (by the current
  // version of the algorithm).
  private void addOldPuzzleJobs(List<RatingJob> jobs) {
    boolean dbUpToDate = mPrefs.getRatingVersion() == Evaluator.CURRENT_VERSION;
    for (long id : mDb.getPuzzlesNeedingRating(!dbUpToDate))
      jobs.add(new IdRatingJob(id));
  }

  private boolean isValidRating(Rating rating) {
    return rating != null
        && rating.evalComplete
        && rating.algorithmVersion == Evaluator.CURRENT_VERSION;
  }

  private Rating rateNamedPuzzle(String name) {
    Log.d(TAG, "Rating puzzle " + name);
    JsonObject props = Generator.regeneratePuzzle(name);
    sPuzzleProperties.put(name, props);
    Grid clues = Grid.fromString(props.get(Generator.PUZZLE_KEY).getAsString());
    Rating rating = Evaluator.evaluate(clues, null);
    if (rating.evalComplete) {
      mDb.addRatingInfo(rating, props);
      mRatings.put(name, rating);
    }
    return rating;
  }

  private Rating ratePuzzle(final long puzzleId) {
    Log.d(TAG, "Rating puzzle " + puzzleId);
    Puzzle puzzle = mDb.getFullPuzzle(puzzleId);
    Rating rating = puzzle.rating;
    if (isValidRating(rating)) {
      Log.d(TAG, "...already rated");
    } else {
      JsonObject props = new JsonParser().parse(puzzle.properties).getAsJsonObject();
      String name = props.has(Generator.NAME_KEY) ? props.get(Generator.NAME_KEY).getAsString() : null;
      rating = mRatings.get(name);
      if (isValidRating(rating)) {
        Log.d(TAG, "...already rated as name " + name);
      } else {
        Evaluator.Callback callback = null;
        synchronized (mLock) {
          mCurrentPuzzleId = puzzleId;
          if (mNewPuzzleId == puzzleId)
            callback = new Evaluator.Callback() {
              @Override public void updateEstimate(double minScore) {
                for (RatingCallback rc : sCallbacks)
                  rc.ratingScoreUpdated(puzzleId, minScore);
              }
              @Override public void disproofsRequired() {}
            };
        }
        rating = Evaluator.evaluate(puzzle.clues, callback);
      }
      if (rating.evalComplete) {
        mDb.setPuzzleRating(puzzleId, rating);
        if (name != null) {
          mDb.addRatingInfo(rating, props);
          mRatings.put(name, rating);
        }
      }
    }
    synchronized (mLock) {
      mCurrentPuzzleId = -1;
      if (mNewPuzzleId == puzzleId)
        mNewPuzzleId = -1;
    }
    if (rating.evalComplete)
      for (RatingCallback rc : sCallbacks) {
        rc.ratingComplete(puzzleId, rating);
        Log.d(TAG, "Rating complete sent to " + rc);
      }
    return rating;
  }
}
