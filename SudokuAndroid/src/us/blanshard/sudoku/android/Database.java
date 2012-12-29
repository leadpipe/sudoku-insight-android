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

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.gen.Generator;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Interface to the SQLite database backing the Insight app.  The methods of this
 * class should generally not be called from the UI thread.
 *
 * @author Luke Blanshard
 */
public class Database {

  private static final String GAME_SELECT_AND_FROM_CLAUSE =
      "SELECT g.*, clues FROM [Game] g JOIN [Puzzle] p ON g.puzzleId = p._id ";

  public static final int ALL_PSEUDO_COLLECTION_ID = 0;
  public static final int GENERATED_COLLECTION_ID = 1;
  public static final int CAPTURED_COLLECTION_ID = 2;
  public static final int SYNCED_COLLECTION_ID = 3;

  private final OpenHelper mOpenHelper;
  private static Database sInstance;

  private Database(Context context) {
    this.mOpenHelper = new OpenHelper(context.getApplicationContext());
  }

  public static synchronized Database instance(Context context) {
    if (sInstance == null) sInstance = new Database(context);
    return sInstance;
  }

  public enum GameState {
    UNSTARTED(0),
    STARTED(1),
    GAVE_UP(2),
    FINISHED(3),
    SKIPPED(4),
    ;

    private final int number;
    private static final GameState[] numbersToValues;

    static {
      GameState[] values = values();
      numbersToValues = new GameState[values.length];
      for (GameState g : values)
        numbersToValues[g.number] = g;
    }

    private GameState(int number) {
      this.number = number;
    }

    public int getNumber() {
      return number;
    }

    public boolean isInPlay() {
      return this.number < GAVE_UP.number;
    }

    public static GameState fromNumber(int number) {
      return numbersToValues[number];
    }
  }

  public static class Game implements Cloneable {
    public long _id;
    public long puzzleId;
    public String history;
    public long elapsedMillis;
    public int numMoves;
    public int numTrails;
    public String uiState;
    public long startTime;
    public long lastTime;
    public GameState gameState;
    public long replayTime;

    // Optional other stuff
    public Grid clues;
    public List<Element> elements;

    @Override public Game clone() {
      try {
        return (Game) super.clone();
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
    public List<Game> games;
    public List<Element> elements;
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
  public long addGeneratedPuzzle(JSONObject properties) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      String clues = (String) properties.remove(Generator.PUZZLE_KEY);
      long puzzleId = addOrUpdatePuzzle(clues, properties, null);

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
   * Adds the puzzle puzzle with the given clues to the database, and also to
   * the captured-puzzles collection.  Returns the puzzle's ID.
   */
  public long addCapturedPuzzle(Grid clues, String source) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      long puzzleId = addOrUpdatePuzzle(clues.toFlatString(), null, source);

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
   * Adds the puzzle with the given clues, properties, and source to the
   * database, or updates it by merging the properties and source with what's
   * already there.  If not already present creates a game row for it as well.
   * Returns the puzzle's ID.
   */
  private long addOrUpdatePuzzle(String clues, JSONObject properties, String source) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("clues", clues);
      if (properties != null)
        values.put("properties", properties.toString());
      if (source != null)
        values.put("source", source);

      Long puzzleId = getPuzzleId(db, clues);
      if (puzzleId == null) {
        puzzleId = db.insertOrThrow("Puzzle", null, values);
        putUnstartedGame(db, puzzleId);
      } else {
        Puzzle puzzle = getFullPuzzle(puzzleId);
        if (puzzle.properties != null && properties != null) {
          JSONObject replacement = new JSONObject(puzzle.properties);
          for (Iterator<?> keys = properties.keys(); keys.hasNext(); ) {
            String key = (String) keys.next();
            replacement.put(key, properties.get(key));
          }
          values.put("properties", replacement.toString());
        }
        // source: no further logic required to replace or leave an existing value.
        db.update("Puzzle", values, "[_id] = ?", new String[]{ Long.toString(puzzleId) });
      }

      db.setTransactionSuccessful();
      return puzzleId;
    } catch (JSONException e) {
      throw new RuntimeException(e);
    } finally {
      db.endTransaction();
    }
  }

  private static long putUnstartedGame(SQLiteDatabase db, long puzzleId) throws SQLException {
    ContentValues values = new ContentValues();
    values.put("puzzleId", puzzleId);
    values.put("gameState", GameState.UNSTARTED.getNumber());
    values.put("lastTime", System.currentTimeMillis());
    return db.insertOrThrow("Game", null, values);
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
      db.update("Puzzle", values, "[_id] = ?", new String[]{ Long.toString(puzzleId) });
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /**
   * Returns the game given its ID.
   */
  public Game getGame(long gameId) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String sql = GAME_SELECT_AND_FROM_CLAUSE + "WHERE g._id = ?";
    return fetchGame(db, sql, Long.toString(gameId));
  }

  /**
   * Returns the most recently modified game row for the given puzzle, or null
   * if there is no such puzzle or it has no game rows.
   */
  public Game getCurrentGameForPuzzle(long puzzleId) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String sql = GAME_SELECT_AND_FROM_CLAUSE + "WHERE [puzzleId] = ? ORDER BY [lastTime] DESC";
    return fetchGame(db, sql, Long.toString(puzzleId));
  }

  private static Game fetchGame(SQLiteDatabase db, String sql, String... args) throws SQLException {
    Cursor cursor = db.rawQuery(sql, args);
    try {
      if (cursor.moveToFirst()) {
        Game answer = gameFromCursor(cursor);
        answer.clues = Grid.fromString(cursor.getString(cursor.getColumnIndexOrThrow("clues")));
        answer.elements = getPuzzleElements(db, answer.puzzleId);
        return answer;
      }
      return null;
    } finally {
      cursor.close();
    }
  }

  private static Puzzle puzzleFromCursor(Cursor cursor) throws SQLException {
    Puzzle answer = new Puzzle();
    answer._id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
    answer.clues = Grid.fromString(cursor.getString(cursor.getColumnIndexOrThrow("clues")));
    answer.properties = cursor.getString(cursor.getColumnIndexOrThrow("properties"));
    answer.source = cursor.getString(cursor.getColumnIndexOrThrow("source"));
    answer.vote = cursor.getInt(cursor.getColumnIndexOrThrow("vote"));
    return answer;
  }

  private static Game gameFromCursor(Cursor cursor) throws SQLException {
    Game answer = new Game();
    answer._id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
    answer.puzzleId = cursor.getLong(cursor.getColumnIndexOrThrow("puzzleId"));
    answer.history = cursor.getString(cursor.getColumnIndexOrThrow("history"));
    answer.elapsedMillis = getLong(cursor, "elapsedMillis", 0);
    answer.uiState = cursor.getString(cursor.getColumnIndexOrThrow("uiState"));
    answer.startTime = getLong(cursor, "startTime", 0);
    answer.lastTime = cursor.getLong(cursor.getColumnIndexOrThrow("lastTime"));
    answer.gameState = GameState.fromNumber(cursor.getInt(cursor.getColumnIndexOrThrow("gameState")));
    answer.replayTime = getLong(cursor, "replayTime", 0);
    return answer;
  }

  private static List<Element> getPuzzleElements(SQLiteDatabase db, long puzzleId) throws SQLException {
    List<Element> answer = Lists.newArrayList();
    Cursor cursor = db.rawQuery("SELECT * FROM [Element] WHERE [puzzleId] = ?",
        new String[] { Long.toString(puzzleId) });
    try {
      while (cursor.moveToNext()) {
        Element element = elementFromCursor(cursor);
        Cursor c2 = db.rawQuery("SELECT * FROM [Collection] WHERE [_id] = ?",
            new String[] { Long.toString(cursor.getLong(cursor.getColumnIndexOrThrow("collectionId"))) });
        try {
          c2.moveToFirst();
          element.collection = collectionFromCursor(c2);
        } finally {
          c2.close();
        }
        answer.add(element);
      }
    } finally {
      cursor.close();
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
   * Returns the most recently modified game row for the given puzzle, or
   * creates a new game row if the existing one's state indicates it is no
   * longer in play. If the game is unstarted, modifies the returned object (but
   * not the corresponding row) to started.
   */
  public Game getOpenGameForPuzzle(long puzzleId) throws SQLException {
    Game answer = getCurrentGameForPuzzle(puzzleId);
    if (answer == null || !answer.gameState.isInPlay()) {
      SQLiteDatabase db = mOpenHelper.getWritableDatabase();
      long gameId = putUnstartedGame(db, puzzleId);
      answer = getGame(gameId);
    }
    return startUnstartedGame(answer);
  }

  /**
   * Modifies the given Game object, if its state is currently unstarted, by
   * changing its state to started and setting its start time. Passes nulls
   * through.
   */
  public static Game startUnstartedGame(Game game) {
    if (game != null && game.gameState == GameState.UNSTARTED) {
      game.gameState = GameState.STARTED;
      game.startTime = System.currentTimeMillis();
    }
    return game;
  }

  /**
   * Returns the least recently modified open game row in the database, or null.
   * If the game is unstarted, modifies the returned object (but not the
   * corresponding row) to started.
   */
  public Game getFirstOpenGame() throws SQLException {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String sql = GAME_SELECT_AND_FROM_CLAUSE + "WHERE [gameState] IN (?, ?) ORDER BY [lastTime] ASC";
    Game answer = fetchGame(db, sql, Integer.toString(GameState.UNSTARTED.getNumber()),
        Integer.toString(GameState.STARTED.getNumber()));
    return startUnstartedGame(answer);
  }

  /**
   * Returns the number of games that are unstarted or in progress.
   */
  public int getNumOpenGames() throws SQLException {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    String sql = "SELECT COUNT(*) FROM [Game] WHERE [gameState] IN (?, ?)";
    Cursor cursor = db.rawQuery(sql, new String[] {Integer.toString(GameState.UNSTARTED.getNumber()),
        Integer.toString(GameState.STARTED.getNumber())});
    try {
      cursor.moveToFirst();
      return cursor.getInt(0);
    } finally {
      cursor.close();
    }
  }

  /**
   * Saves the given game, modifying its last update time to now.
   */
  public void updateGame(Game game) throws SQLException {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("history", game.history);
      values.put("elapsedMillis", game.elapsedMillis);
      values.put("numMoves", game.numMoves);
      values.put("numTrails", game.numTrails);
      values.put("uiState", game.uiState);
      values.put("startTime", game.startTime);
      values.put("lastTime", game.lastTime = System.currentTimeMillis());
      values.put("gameState", game.gameState.getNumber());

      db.update("Game", values, "[_id] = ?", new String[]{ Long.toString(game._id) });

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /** Updates the replay time of the game whose ID is given, to now. */
  public void noteReplay(long gameId) {
    SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put("replayTime", System.currentTimeMillis());
      db.update("Game", values, "[_id] = ?", new String[]{ Long.toString(gameId) });
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /** Returns all the puzzles, each with its games and collections filled in. */
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
          puzzle.games = Lists.newArrayList();
          answer.add(puzzle);
          puzzles.put(puzzle._id, puzzle);
        }
      } finally {
        cursor.close();
      }
      cursor = db.rawQuery("SELECT * FROM [Game] ORDER BY [_id]", null);
      try {
        while (cursor.moveToNext()) {
          Game game = gameFromCursor(cursor);
          Puzzle puzzle = puzzles.get(game.puzzleId);
          puzzle.games.add(game);
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

  public List<CollectionInfo> getAllCollections() throws SQLException {
    List<CollectionInfo> answer = Lists.newArrayList();
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    db.beginTransaction();
    try {
      Cursor cursor = db.rawQuery("SELECT * FROM [Collection]", null);
      try {
        while (cursor.moveToNext()) {
          CollectionInfo collection = collectionFromCursor(cursor);
          answer.add(collection);
        }
      } finally {
        cursor.close();
      }
    } finally {
      db.endTransaction();
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
        answer.games = Lists.newArrayList();
        String sql = "SELECT * FROM [Game] WHERE [puzzleId] = ? ORDER BY [_id]";
        cursor = db.rawQuery(sql, idString);
        try {
          while (cursor.moveToNext()) {
            answer.games.add(gameFromCursor(cursor));
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

  /** Returns the set of source strings present in the Puzzle table. */
  public List<String> getPuzzleSources() {
    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
    Cursor cursor = db.rawQuery(
        "SELECT DISTINCT [source] FROM [Puzzle] WHERE [source] IS NOT NULL ORDER BY [source]", null);
    List<String> answer = Lists.newArrayList();
    try {
      while (cursor.moveToNext())
        answer.add(cursor.getString(0));
    } finally {
      cursor.close();
    }
    return answer;
  }

  /** Returns null if not found in the given database. */
  private static Long getPuzzleId(SQLiteDatabase db, String clues) throws SQLException {
    Cursor cursor = db.rawQuery("SELECT [_id] FROM [Puzzle] WHERE [clues] = ?",
        new String[]{ clues });
    try {
      if (cursor.moveToFirst()) return cursor.getLong(0);
      return null;
    } finally {
      cursor.close();
    }
  }

  /** Returns null if the element isn't found. */
  private static Long getElementId(SQLiteDatabase db, long puzzleId, long collectionId)
      throws SQLException {
    Cursor cursor = db.rawQuery(
        "SELECT [_id] FROM [Element] WHERE [puzzleId] = ? AND [collectionId] = ?",
        new String[]{ String.valueOf(puzzleId), String.valueOf(collectionId) });
    try {
      if (cursor.moveToFirst()) return cursor.getLong(0);
      return null;
    } finally {
      cursor.close();
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
      super(context, "db", null, 7);
      mContext = context;
    }

    @Override public void onCreate(SQLiteDatabase db) {
      db.execSQL(""
          + "CREATE TABLE [Puzzle] ("
          + "  [_id] INTEGER PRIMARY KEY AUTOINCREMENT,"
          + "  [clues] TEXT  NOT NULL  UNIQUE,"
          + "  [properties] TEXT,"
          + "  [source] TEXT,"
          + "  [vote] INTEGER  DEFAULT 0)");
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
          + "CREATE TABLE [Game] ("
          + "  [_id] INTEGER PRIMARY KEY,"
          + "  [puzzleId] INTEGER  REFERENCES [Puzzle] ON DELETE CASCADE,"
          + "  [history] TEXT,"
          + "  [elapsedMillis] INTEGER,"
          + "  [numMoves] INTEGER,"
          + "  [numTrails] INTEGER,"
          + "  [uiState] TEXT,"
          + "  [startTime] INTEGER,"
          + "  [lastTime] INTEGER  NOT NULL,"
          + "  [gameState] INTEGER  NOT NULL,"
          + "  [replayTime] INTEGER)");
      db.execSQL(""
          + "CREATE INDEX [GameByPuzzleIdAndLastTime] ON [Game] ("
          + "  [puzzleId],"
          + "  [lastTime] DESC)");
      db.execSQL(""
          + "CREATE INDEX [GameByStateAndLastTime] ON [Game] ("
          + "  [gameState],"
          + "  [lastTime])");

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
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      if (oldVersion < 7)
        throw new AssertionError("Upgrades not supported, please reinstall");
    }
  }
}
