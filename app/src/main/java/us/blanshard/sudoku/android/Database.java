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

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.gen.Generator;
import us.blanshard.sudoku.insight.Evaluator;
import us.blanshard.sudoku.insight.Rating;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Interface to the SQLite database backing the Insight app.  The methods of this
 * class should generally not be called from the UI thread.
 *
 * @author Luke Blanshard
 */
public class Database {

  private static final String ATTEMPT_SELECT_AND_FROM_CLAUSE =
      "SELECT a.*, clues, properties, rating FROM [Attempt] a JOIN [Puzzle] p ON a.puzzleId = p._id ";

  public static final int ALL_PSEUDO_COLLECTION_ID = 0;
  public static final int GENERATED_COLLECTION_ID = 1;
  public static final int CAPTURED_COLLECTION_ID = 2;
  public static final int SYNCED_COLLECTION_ID = 3;
  public static final int RECOMMENDED_COLLECTION_ID = 4;

  public static final int THIS_INSTALLATION_ID = 1;

  private final OpenHelper mOpenHelper;
  private static Database sInstance;

  private Database(Context context) {
    this.mOpenHelper = new OpenHelper(context.getApplicationContext());
  }

  public static synchronized Database instance(Context context) {
    if (sInstance == null) sInstance = new Database(context);
    return sInstance;
  }

  public enum AttemptState {
    UNSTARTED(0),
    STARTED(1),
    GAVE_UP(2),
    FINISHED(3),
    SKIPPED(4),
    ;

    private final int number;
    private static final AttemptState[] numbersToValues;

    static {
      AttemptState[] values = values();
      numbersToValues = new AttemptState[values.length];
      for (AttemptState s : values)
        numbersToValues[s.number] = s;
    }

    AttemptState(int number) {
      this.number = number;
    }

    public int getNumber() {
      return number;
    }

    public boolean isInPlay() {
      return this.number < GAVE_UP.number;
    }

    public boolean isComplete() {
      return this == GAVE_UP || this == FINISHED;
    }

    public static AttemptState fromNumber(int number) {
      return numbersToValues[number];
    }
  }

  public static class Attempt implements Cloneable {
    public long _id;
    public long puzzleId;
    public long installationId;
    public String history;
    public long elapsedMillis;
    public int numMoves;
    public int numTrails;
    public String uiState;
    public long startTime;
    public long lastTime;
    public AttemptState attemptState;
    public long replayTime;
    public boolean saved;

    // Optional other stuff
    public Grid clues;
    public String properties;
    public Rating rating;
    public List<Element> elements;

    @Override public Attempt clone() {
      try {
        return (Attempt) super.clone();
      } catch (CloneNotSupportedException e) {
        throw new Error(e);
      }
    }
  }

  public static class CollectionInfo {
    public long _id;
    public String name;
    public String source;
    public long createTime;
  }

  public static class Element {
    public long _id;
    public long puzzleId;
    public long createTime;
    public CollectionInfo collection;
  }

  public static class Puzzle {
    public long _id;
    public Grid clues;
    public String properties;
    public String source;
    public int vote;
    public boolean voteSaved;
    public String stats;
    public long statsTime;
    public Rating rating;
    public List<Attempt> attempts;
    public List<Element> elements;
  }

  public static class RatingInfo {
    public long _id;
    public Rating rating;
    public JsonObject properties;
  }

  /**
   * Looks up the puzzle with the given clues, returns its ID or null if it
   * isn't already in the database.
   */
  public Long lookUpPuzzleId(Grid clues) throws SQLException {
    return getPuzzleId(mOpenHelper.getReadableDatabase(), clues.toFlatString());
  }

  /**
   * Adds the puzzle described by the given {@link Generator} properties to the
   * database, and also to the generated-puzzles collection.  Returns the
   * puzzle's ID.  Modifies the given properties object.
   */
  public long addGeneratedPuzzle(JsonObject properties) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      long puzzleId = addOrUpdatePuzzle(properties, null);

      if (getElementId(db, puzzleId, GENERATED_COLLECTION_ID) == null) {
        ContentValues values = new ContentValues();
        values.put("puzzleId", puzzleId);
        values.put("collectionId", GENERATED_COLLECTION_ID);
        values.put("createTime", System.currentTimeMillis());
        db.insertOrThrow("Element", null, values);
      }
      db.setTransactionSuccessful();
      return puzzleId;
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Adds the puzzle with the given properties to the database, and also to the
   * captured-puzzles collection.  Returns the puzzle's database ID.  Modifies
   * the given properties object to remove its string ID and add in any
   * properties already present in the database for the puzzle.
   */
  public long addCapturedPuzzle(JsonObject properties, String source) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      long puzzleId = addOrUpdatePuzzle(properties, source);

      if (getElementId(db, puzzleId, CAPTURED_COLLECTION_ID) == null) {
        ContentValues values = new ContentValues();
        values.put("puzzleId", puzzleId);
        values.put("collectionId", CAPTURED_COLLECTION_ID);
        values.put("createTime", System.currentTimeMillis());
        db.insertOrThrow("Element", null, values);
      }
      db.setTransactionSuccessful();
      return puzzleId;
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Adds the puzzle with the given properties and source to the database, or
   * updates it by merging the properties and source with what's already there.
   * If not already present creates an attempt row for it as well.  Modifies the
   * given JsonObject to remove the clues and add any properties already in the
   * database that aren't in the given JsonObject.  Returns the puzzle's ID.
   */
  private long addOrUpdatePuzzle(JsonObject properties, String source) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      String clues = properties.remove(Generator.PUZZLE_KEY).getAsString();
      Long puzzleId = getPuzzleId(db, clues);
      if (puzzleId != null) {
        Puzzle puzzle = getFullPuzzle(puzzleId);
        JsonObject existing = new JsonParser().parse(puzzle.properties).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : existing.entrySet())
          if (!properties.has(e.getKey()))
            properties.add(e.getKey(), e.getValue());
        if (source == null)
          source = puzzle.source;
      }

      ContentValues values = new ContentValues();
      if (puzzleId == null)
        values.put("clues", clues);
      values.put("properties", properties.toString());
      if (source != null)
        values.put("source", source);

      if (puzzleId == null) {
        puzzleId = db.insertOrThrow("Puzzle", null, values);
        putUnstartedAttempt(db, puzzleId);
      } else {
        db.update("Puzzle", values, "[_id] = ?", new String[]{ puzzleId.toString() });
      }

      db.setTransactionSuccessful();
      return puzzleId;
    } finally {
      db.endTransaction();
    }
  }

  private static long putUnstartedAttempt(SQLiteDatabase db, long puzzleId) throws SQLException {
    ContentValues values = new ContentValues();
    values.put("puzzleId", puzzleId);
    values.put("installationId", THIS_INSTALLATION_ID);
    values.put("attemptState", AttemptState.UNSTARTED.getNumber());
    values.put("lastTime", System.currentTimeMillis());
    return db.insertOrThrow("Attempt", null, values);
  }

  /**
   * Adds a new collection to the database, from the given source and with the
   * given name, and consisting of the given puzzles, expressed as Generator
   * properties objects.  Returns the ID of the new collection.
   */
  public long addCollection(String name, String source, List<JsonObject> puzzles) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      long createTime = System.currentTimeMillis();
      values.put("name", name);
      values.put("source", source);
      values.put("createTime", createTime);
      long collectionId = db.insertOrThrow("Collection", null, values);

      for (JsonObject puzzle : puzzles) {
        long puzzleId = addOrUpdatePuzzle(puzzle, source);
        values.clear();
        values.put("puzzleId", puzzleId);
        values.put("collectionId", collectionId);
        values.put("createTime", createTime);
        db.insertOrThrow("Element", null, values);
      }
      db.setTransactionSuccessful();
      return collectionId;
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Sets the vote for a puzzle.  Allows any int value.
   */
  public void vote(long puzzleId, int vote) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("vote", vote);
      values.put("voteSaved", 0);
      db.update("Puzzle", values, "[_id] = ?", new String[]{ Long.toString(puzzleId) });
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Marks a puzzle's vote as saved.
   */
  public void markVoteSaved(long puzzleId, int vote) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("voteSaved", 1);
      db.update("Puzzle", values, "[_id] = ? AND [vote] = ?",
          new String[]{ Long.toString(puzzleId), Integer.toString(vote) });
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Sets a puzzle's stats string, marking it with the current time.
   */
  public void setPuzzleStats(long puzzleId, String stats) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("stats", stats);
      values.put("statsTime", System.currentTimeMillis());
      db.update("Puzzle", values, "[_id] = ?", new String[]{ Long.toString(puzzleId) });
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Returns the IDs of puzzles either missing ratings entirely or whose ratings
   * are obsoleted by a new version of {@link Evaluator}'s algorithm.
   */
  public List<Long> getPuzzlesNeedingRating(boolean checkForOldVersion) {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String query = checkForOldVersion
        ? "SELECT [_id], [rating] FROM [Puzzle]"
        : "SELECT [_id] FROM [Puzzle] WHERE [rating] IS NULL";
    List<Long> answer = Lists.newArrayList();
    try (Cursor cursor = db.rawQuery(query, null)) {
      while (cursor.moveToNext()) {
        Long id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
        if (checkForOldVersion) {
          String ratingString = cursor.getString(cursor.getColumnIndexOrThrow("rating"));
          if (ratingString != null) {
            Rating rating = Rating.deserialize(ratingString);
            if (rating.algorithmVersion == Evaluator.CURRENT_VERSION)
              continue;  // Skip this guy, he's good.
          }
        }
        answer.add(id);
      }
    }
    return answer;
  }

  /**
   * Sets a puzzle's rating.
   */
  public void setPuzzleRating(long puzzleId, Rating rating) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("rating", rating.serialize());
      db.update("Puzzle", values, "[_id] = ?", new String[]{ Long.toString(puzzleId) });
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Returns the attempt given its ID.
   */
  public Attempt getAttempt(long attemptId) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String sql = ATTEMPT_SELECT_AND_FROM_CLAUSE + "WHERE a._id = ?";
    return fetchAttempt(db, sql, Long.toString(attemptId));
  }

  /**
   * Returns the most recently modified attempt row for the given puzzle, or null
   * if there is no such puzzle or it has no attempt rows.
   */
  public Attempt getCurrentAttemptForPuzzle(long puzzleId) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String sql = ATTEMPT_SELECT_AND_FROM_CLAUSE + "WHERE [puzzleId] = ? ORDER BY [lastTime] DESC";
    return fetchAttempt(db, sql, Long.toString(puzzleId));
  }

  private static Attempt fetchAttempt(SQLiteDatabase db, String sql, String... args) throws SQLException {
    try (Cursor cursor = db.rawQuery(sql, args)) {
      if (cursor.moveToFirst()) {
        Attempt answer = attemptFromCursor(cursor);
        answer.clues = Grid.fromString(cursor.getString(cursor.getColumnIndexOrThrow("clues")));
        answer.properties = cursor.getString(cursor.getColumnIndexOrThrow("properties"));
        String r = cursor.getString(cursor.getColumnIndexOrThrow("rating"));
        if (r != null) answer.rating = Rating.deserialize(r);
        answer.elements = getPuzzleElements(db, answer.puzzleId);
        return answer;
      }
      return null;
    }
  }

  private static Puzzle puzzleFromCursor(Cursor cursor) throws SQLException {
    Puzzle answer = new Puzzle();
    answer._id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
    answer.clues = Grid.fromString(cursor.getString(cursor.getColumnIndexOrThrow("clues")));
    answer.properties = cursor.getString(cursor.getColumnIndexOrThrow("properties"));
    answer.source = cursor.getString(cursor.getColumnIndexOrThrow("source"));
    answer.vote = cursor.getInt(cursor.getColumnIndexOrThrow("vote"));
    answer.voteSaved = getLong(cursor, "voteSaved", 0) != 0;
    answer.stats = cursor.getString(cursor.getColumnIndexOrThrow("stats"));
    answer.statsTime = getLong(cursor, "statsTime", 0);
    String r = cursor.getString(cursor.getColumnIndexOrThrow("rating"));
    if (r != null)
      answer.rating = Rating.deserialize(r);
    return answer;
  }

  private static Attempt attemptFromCursor(Cursor cursor) throws SQLException {
    Attempt answer = new Attempt();
    answer._id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
    answer.puzzleId = cursor.getLong(cursor.getColumnIndexOrThrow("puzzleId"));
    answer.installationId = cursor.getLong(cursor.getColumnIndexOrThrow("installationId"));
    answer.history = cursor.getString(cursor.getColumnIndexOrThrow("history"));
    answer.elapsedMillis = getLong(cursor, "elapsedMillis", 0);
    answer.uiState = cursor.getString(cursor.getColumnIndexOrThrow("uiState"));
    answer.startTime = getLong(cursor, "startTime", 0);
    answer.lastTime = cursor.getLong(cursor.getColumnIndexOrThrow("lastTime"));
    answer.attemptState = AttemptState.fromNumber(cursor.getInt(cursor.getColumnIndexOrThrow("attemptState")));
    answer.replayTime = getLong(cursor, "replayTime", 0);
    answer.saved = getLong(cursor, "saved", 0) != 0;
    return answer;
  }

  private static List<Element> getPuzzleElements(SQLiteDatabase db, long puzzleId) throws SQLException {
    List<Element> answer = Lists.newArrayList();
    try (Cursor cursor = db.rawQuery("SELECT * FROM [Element] WHERE [puzzleId] = ?",
            new String[]{Long.toString(puzzleId)})) {
      while (cursor.moveToNext()) {
        Element element = elementFromCursor(cursor);
        try (Cursor c2 = db.rawQuery("SELECT * FROM [Collection] WHERE [_id] = ?",
                new String[]{Long.toString(cursor.getLong(cursor.getColumnIndexOrThrow("collectionId")))})) {
          c2.moveToFirst();
          element.collection = collectionFromCursor(c2);
        }
        answer.add(element);
      }
    }
    return answer;
  }

  private static Element elementFromCursor(Cursor cursor) {
    Element element = new Element();
    element._id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
    element.puzzleId = cursor.getLong(cursor.getColumnIndexOrThrow("puzzleId"));
    element.createTime = getLong(cursor, "createTime", 0);
    return element;
  }

  private static CollectionInfo collectionFromCursor(Cursor cursor) {
    CollectionInfo collection = new CollectionInfo();
    collection._id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
    collection.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
    collection.source = cursor.getString(cursor.getColumnIndexOrThrow("source"));
    collection.createTime = cursor.getLong(cursor.getColumnIndexOrThrow("createTime"));
    return collection;
  }

  /**
   * Returns the most recently modified attempt row for the given puzzle, or
   * creates a new attempt row if the existing one's state indicates it is no
   * longer in play.
   */
  public Attempt getOpenAttemptForPuzzle(long puzzleId) throws SQLException {
    Attempt answer = getCurrentAttemptForPuzzle(puzzleId);
    if (answer == null || !answer.attemptState.isInPlay()) {
      SQLiteDatabase db = mOpenHelper.getWritableDatabase();
      long attemptId = putUnstartedAttempt(db, puzzleId);
      answer = getAttempt(attemptId);
    }
    return answer;
  }

  /**
   * Modifies the given Attempt object, if its state is currently unstarted, by
   * changing its state to started and setting its start time. Passes nulls
   * through.
   */
  public static Attempt startUnstartedAttempt(Attempt attempt) {
    if (attempt != null && attempt.attemptState == AttemptState.UNSTARTED) {
      attempt.attemptState = AttemptState.STARTED;
      attempt.startTime = System.currentTimeMillis();
    }
    return attempt;
  }

  /**
   * Returns the open attempt row for the first puzzle in the given collection
   * that has one, or null.
   */
  public Attempt getFirstOpenAttempt(long collectionId) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String sql = ATTEMPT_SELECT_AND_FROM_CLAUSE
        + "WHERE [attemptState] IN (?, ?) "
        + "AND EXISTS (SELECT _id FROM [Element] WHERE puzzleId = p._id AND collectionId = ?) "
        + "ORDER BY p._id ASC, [lastTime] ASC";
    return fetchAttempt(db, sql, Integer.toString(AttemptState.UNSTARTED.getNumber()),
        Integer.toString(AttemptState.STARTED.getNumber()), Long.toString(collectionId));
  }

  /**
   * Returns the open attempt row for the first puzzle in the database that has
   * one, or null.
   */
  public Attempt getFirstOpenAttempt() throws SQLException {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String sql = ATTEMPT_SELECT_AND_FROM_CLAUSE
        + "WHERE [attemptState] IN (?, ?) ORDER BY p._id ASC, [lastTime] ASC";
    return fetchAttempt(db, sql, Integer.toString(AttemptState.UNSTARTED.getNumber()),
        Integer.toString(AttemptState.STARTED.getNumber()));
  }

  /**
   * Returns the number of attempts that are unstarted or in progress.
   */
  public int getNumOpenAttempts() throws SQLException {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String sql = "SELECT COUNT(*) FROM [Attempt] WHERE [attemptState] IN (?, ?)";
    try (Cursor cursor = db.rawQuery(sql, new String[]{Integer.toString(AttemptState.UNSTARTED.getNumber()),
            Integer.toString(AttemptState.STARTED.getNumber())})) {
      cursor.moveToFirst();
      return cursor.getInt(0);
    }
  }

  /**
   * Finds all attempts that have not yet been marked as saved. Fills in the
   * puzzle's properties as well as the attempt's basic info.
   */
  public List<Attempt> getUnsavedAttempts() throws SQLException {
    List<Attempt> answer = Lists.newArrayList();
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String sql = ATTEMPT_SELECT_AND_FROM_CLAUSE
        + "WHERE [attemptState] IN (?, ?) AND ([saved] IS NULL OR NOT [saved]) ORDER BY [lastTime] ASC";
    try (Cursor cursor = db.rawQuery(sql, new String[]{
            Integer.toString(AttemptState.FINISHED.getNumber()),
            Integer.toString(AttemptState.GAVE_UP.getNumber()),
    })) {
      while (cursor.moveToNext()) {
        Attempt attempt = attemptFromCursor(cursor);
        attempt.clues = Grid.fromString(cursor.getString(cursor.getColumnIndexOrThrow("clues")));
        attempt.properties = cursor.getString(cursor.getColumnIndexOrThrow("properties"));
        answer.add(attempt);
      }
    }
    return answer;
  }

  /**
   * Saves the given attempt, modifying its last update time to now.
   */
  public void updateAttempt(Attempt attempt) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("history", attempt.history);
      values.put("elapsedMillis", attempt.elapsedMillis);
      values.put("numMoves", attempt.numMoves);
      values.put("numTrails", attempt.numTrails);
      values.put("uiState", attempt.uiState);
      values.put("startTime", attempt.startTime);
      values.put("lastTime", attempt.lastTime = System.currentTimeMillis());
      values.put("attemptState", attempt.attemptState.getNumber());
      values.put("saved", 0);

      db.update("Attempt", values, "[_id] = ?", new String[]{ Long.toString(attempt._id) });

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /** Updates the replay time of the attempt whose ID is given, to now. */
  public void noteReplay(long attemptId) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("replayTime", System.currentTimeMillis());
      db.update("Attempt", values, "[_id] = ?", new String[]{ Long.toString(attemptId) });
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /** Marks an attempt as having been saved to the server. */
  public void markAttemptSaved(long attemptId, long lastTime) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("saved", 1);
      db.update("Attempt", values, "[_id] = ? AND [lastTime] = ?",
          new String[]{ Long.toString(attemptId), Long.toString(lastTime) });
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /** Returns all the puzzles, each with its attempts and collections filled in. */
  public List<Puzzle> getAllPuzzles() throws SQLException {
    List<Puzzle> answer = Lists.newArrayList();
    Map<Long, Puzzle> puzzles = Maps.newHashMap();
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    db.beginTransaction();
    try {
      Cursor cursor = db.rawQuery("SELECT * FROM [Puzzle] ORDER BY [_id]", null);
      try {
        while (cursor.moveToNext()) {
          Puzzle puzzle = puzzleFromCursor(cursor);
          puzzle.elements = Lists.newArrayList();
          puzzle.attempts = Lists.newArrayList();
          answer.add(puzzle);
          puzzles.put(puzzle._id, puzzle);
        }
      } finally {
        cursor.close();
      }
      cursor = db.rawQuery("SELECT * FROM [Attempt] ORDER BY [_id]", null);
      try {
        while (cursor.moveToNext()) {
          Attempt attempt = attemptFromCursor(cursor);
          Puzzle puzzle = puzzles.get(attempt.puzzleId);
          puzzle.attempts.add(attempt);
        }
      } finally {
        cursor.close();
      }
      Map<Long, CollectionInfo> collections = Maps.newHashMap();
      cursor = db.rawQuery("SELECT * FROM [Collection]", null);
      try {
        while (cursor.moveToNext()) {
          CollectionInfo collection = collectionFromCursor(cursor);
          collections.put(collection._id, collection);
        }
      } finally {
        cursor.close();
      }
      cursor = db.rawQuery("SELECT * FROM [Element] ORDER BY [_id]", null);
      try {
        while (cursor.moveToNext()) {
          Element element = elementFromCursor(cursor);
          element.collection = collections.get(cursor.getLong(cursor.getColumnIndexOrThrow("collectionId")));
          Puzzle puzzle = puzzles.get(cursor.getLong(cursor.getColumnIndexOrThrow("puzzleId")));
          puzzle.elements.add(element);
        }
      } finally {
        cursor.close();
      }
    } finally {
      db.endTransaction();
    }
    return answer;
  }

  public List<Puzzle> getPuzzlesWithUnsavedVotes() throws SQLException {
    List<Puzzle> answer = Lists.newArrayList();
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    try (Cursor cursor = db.rawQuery(
            "SELECT * FROM [Puzzle] WHERE NOT [voteSaved] ORDER BY [_id]", null)) {
      while (cursor.moveToNext()) {
        answer.add(puzzleFromCursor(cursor));
      }
    }
    return answer;
  }

  public List<Puzzle> getPuzzlesWithOldStats(long cutoff) throws SQLException {
    List<Puzzle> answer = Lists.newArrayList();
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    try (Cursor cursor = db.rawQuery(
            "SELECT * FROM [Puzzle] WHERE [statsTime] < ? ORDER BY [_id]",
            new String[]{Long.toString(cutoff)})) {
      while (cursor.moveToNext()) {
        answer.add(puzzleFromCursor(cursor));
      }
    }
    return answer;
  }

  public List<CollectionInfo> getAllCollections() throws SQLException {
    List<CollectionInfo> answer = Lists.newArrayList();
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    try (Cursor cursor = db.rawQuery("SELECT * FROM [Collection]", null)) {
      while (cursor.moveToNext()) {
        CollectionInfo collection = collectionFromCursor(cursor);
        answer.add(collection);
      }
    }
    return answer;
  }

  public Puzzle getFullPuzzle(long puzzleId) throws SQLException {
    Puzzle answer = null;
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    db.beginTransaction();
    try {
      String[] idString = { Long.toString(puzzleId) };
      Cursor cursor = db.rawQuery("SELECT * FROM [Puzzle] WHERE [_id] = ?", idString);
      try {
        if (cursor.moveToNext()) {
          answer = puzzleFromCursor(cursor);
        }
      } finally {
        cursor.close();
      }
      if (answer != null) {
        answer.elements = getPuzzleElements(db, puzzleId);
        answer.attempts = Lists.newArrayList();
        String sql = "SELECT * FROM [Attempt] WHERE [puzzleId] = ? ORDER BY [_id]";
        cursor = db.rawQuery(sql, idString);
        try {
          while (cursor.moveToNext()) {
            answer.attempts.add(attemptFromCursor(cursor));
          }
        } finally {
          cursor.close();
        }
      }
    } finally {
      db.endTransaction();
    }
    return answer;
  }

  /** Returns the set of ratings we have precomputed. */
  public List<RatingInfo> getRatingInfo() {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    List<RatingInfo> answer = Lists.newArrayList();
    try (Cursor cursor = db.rawQuery("SELECT * FROM [Rating]", null)) {
      while (cursor.moveToNext()) {
        RatingInfo info = new RatingInfo();
        info._id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
        info.rating = Rating.deserialize(
                cursor.getString(cursor.getColumnIndexOrThrow("rating")));
        info.properties = new JsonParser().parse(
                cursor.getString(cursor.getColumnIndexOrThrow("properties"))).getAsJsonObject();
        answer.add(info);
      }
    }
    return answer;
  }

  /** Adds rating information about a puzzle. */
  public void addRatingInfo(Rating rating, JsonObject puzzleProperties) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("rating", rating.serialize());
      values.put("properties", puzzleProperties.toString());
      db.insertOrThrow("Rating", null, values);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /** Deletes no-longer-needed rating information. */
  public void deleteRatingInfo(Collection<RatingInfo> infos) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      for (RatingInfo info : infos) {
        db.delete("Rating", "[_id] = ?", new String[]{ Long.toString(info._id) });
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /** Returns the set of source strings present in the Puzzle table. */
  public List<String> getPuzzleSources() {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    List<String> answer = Lists.newArrayList();
    try (Cursor cursor = db.rawQuery(
            "SELECT DISTINCT [source] FROM [Puzzle] WHERE [source] IS NOT NULL ORDER BY [source]", null)) {
      while (cursor.moveToNext())
        answer.add(cursor.getString(0));
    }
    return answer;
  }

  /** Returns null if not found in the given database. */
  private static Long getPuzzleId(SQLiteDatabase db, String clues) throws SQLException {
    try (Cursor cursor = db.rawQuery("SELECT [_id] FROM [Puzzle] WHERE [clues] = ?",
            new String[]{clues})) {
      if (cursor.moveToFirst()) return cursor.getLong(0);
      return null;
    }
  }

  /** Returns null if the element isn't found. */
  private static Long getElementId(SQLiteDatabase db, long puzzleId, long collectionId)
      throws SQLException {
    try (Cursor cursor = db.rawQuery(
            "SELECT [_id] FROM [Element] WHERE [puzzleId] = ? AND [collectionId] = ?",
            new String[]{String.valueOf(puzzleId), String.valueOf(collectionId)})) {
      if (cursor.moveToFirst()) return cursor.getLong(0);
      return null;
    }
  }

  private static long getLong(Cursor cursor, String columnName, long defaultValue) throws SQLException {
    int index = cursor.getColumnIndexOrThrow(columnName);
    if (cursor.isNull(index)) return defaultValue;
    return cursor.getLong(index);
  }

  private static class OpenHelper extends SQLiteOpenHelper {

    private final Context mContext;

    OpenHelper(Context context) {
      super(context, "db", null, 11);
      mContext = context;
    }

    @Override public void onCreate(SQLiteDatabase db) {
      db.execSQL(""
          + "CREATE TABLE [Puzzle] ("
          + "  [_id] INTEGER PRIMARY KEY AUTOINCREMENT,"
          + "  [clues] TEXT  NOT NULL  UNIQUE,"
          + "  [properties] TEXT,"
          + "  [source] TEXT,"
          + "  [vote] INTEGER  DEFAULT 0,"
          + "  [voteSaved] INTEGER  DEFAULT 1,"  // boolean
          + "  [stats] TEXT,"
          + "  [statsTime] INTEGER  DEFAULT 0,"
          + "  [rating] TEXT)");
      db.execSQL(""
          + "CREATE TABLE [Installation] ("
          + "  [_id] INTEGER PRIMARY KEY,"
          + "  [id] TEXT  NOT NULL  UNIQUE,"
          + "  [name] TEXT  NOT NULL)");
      db.execSQL(""
          + "CREATE TABLE [Collection] ("
          + "  [_id] INTEGER PRIMARY KEY,"
          + "  [name] TEXT  NOT NULL,"
          + "  [source] TEXT,"
          + "  [createTime] INTEGER  NOT NULL)");
      db.execSQL(""
          + "CREATE TABLE [Element] ("
          + "  [_id] INTEGER PRIMARY KEY,"
          + "  [puzzleId] INTEGER  REFERENCES [Puzzle] ON DELETE CASCADE,"
          + "  [collectionId] INTEGER  REFERENCES [Collection] ON DELETE CASCADE,"
          + "  [createTime] INTEGER  NOT NULL)");
      db.execSQL(""
          + "CREATE INDEX [ElementByPuzzleId] ON [Element] ("
          + "  [puzzleId])");
      db.execSQL(""
          + "CREATE INDEX [ElementByCollectionId] ON [Element] ("
          + "  [collectionId])");
      db.execSQL(""
          + "CREATE UNIQUE INDEX [ElementByIds] ON [Element] ("
          + "  [puzzleId],"
          + "  [collectionId])");
      db.execSQL(""
          + "CREATE TABLE [Attempt] ("
          + "  [_id] INTEGER PRIMARY KEY,"
          + "  [puzzleId] INTEGER  REFERENCES [Puzzle] ON DELETE CASCADE,"
          + "  [installationId] INTEGER  REFERENCES [Installation] ON DELETE CASCADE,"
          + "  [history] TEXT,"
          + "  [elapsedMillis] INTEGER,"
          + "  [numMoves] INTEGER,"
          + "  [numTrails] INTEGER,"
          + "  [uiState] TEXT,"
          + "  [startTime] INTEGER,"
          + "  [lastTime] INTEGER  NOT NULL,"
          + "  [attemptState] INTEGER  NOT NULL,"
          + "  [replayTime] INTEGER,"
          + "  [saved] INTEGER)");  // boolean
      db.execSQL(""
          + "CREATE INDEX [AttemptByPuzzleIdAndLastTime] ON [Attempt] ("
          + "  [puzzleId],"
          + "  [lastTime] DESC)");
      db.execSQL(""
          + "CREATE INDEX [AttemptByStateAndLastTime] ON [Attempt] ("
          + "  [attemptState],"
          + "  [lastTime])");
      db.execSQL(""
          + "CREATE TABLE [Rating] ("
          + "  [_id] INTEGER PRIMARY KEY,"
          + "  [rating] TEXT  NOT NULL,"
          + "  [properties] TEXT  NOT NULL)");

      ContentValues values = new ContentValues();
      values.put("_id", GENERATED_COLLECTION_ID);
      values.put("name", mContext.getString(R.string.text_generated_puzzles));
      values.put("createTime", System.currentTimeMillis());
      db.insertOrThrow("Collection", null, values);
      values.put("_id", CAPTURED_COLLECTION_ID);
      values.put("name", mContext.getString(R.string.text_captured_puzzles));
      db.insertOrThrow("Collection", null, values);
      values.put("_id", SYNCED_COLLECTION_ID);
      values.put("name", mContext.getString(R.string.text_synced_puzzles));
      db.insertOrThrow("Collection", null, values);
      values.put("_id", RECOMMENDED_COLLECTION_ID);
      values.put("name", mContext.getString(R.string.text_recommended_puzzles));
      db.insertOrThrow("Collection", null, values);

      values = new ContentValues();
      values.put("_id", THIS_INSTALLATION_ID);
      values.put("id", Installation.id(mContext));
      values.put("name", Prefs.instance(mContext).getDeviceName());
      db.insertOrThrow("Installation", null, values);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      if (oldVersion < 8)
        throw new AssertionError("Upgrades not supported, please reinstall");
      if (oldVersion < 9) {
        db.execSQL("ALTER TABLE [Puzzle] ADD COLUMN [voteSaved] INTEGER DEFAULT 1");
        db.execSQL("ALTER TABLE [Puzzle] ADD COLUMN [stats] TEXT");
        db.execSQL("ALTER TABLE [Puzzle] ADD COLUMN [statsTime] INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE [Attempt] ADD COLUMN [saved] INTEGER");

        ContentValues values = new ContentValues();
        values.put("voteSaved", 0);
        db.update("Puzzle", values, "[vote] <> 0", null);
      }
      if (oldVersion < 10) {
        db.execSQL("ALTER TABLE [Puzzle] ADD COLUMN [rating] TEXT");
      }
      if (oldVersion < 11) {
        db.execSQL(""
            + "CREATE TABLE [Rating] ("
            + "  [_id] INTEGER PRIMARY KEY,"
            + "  [rating] TEXT  NOT NULL,"
            + "  [properties] TEXT  NOT NULL)");
      }
    }
  }
}