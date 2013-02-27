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
package us.blanshard.sudoku.appengine;

/**
 * Constants for database kind schemas.
 */
public class Schema {
  /**
   * A root entity containing global configuration data for the app.
   */
  public static class Config {
    public static final String KIND = "Config";
    public static final String ID = "config";

    public static final String STREAM_COUNT = "streamCount";
  }

  /**
   * A root entity that describes a single installation of Sudoku Insight on a
   * device. Its key is the installation's UUID; there is also an opaque numeric
   * ID, which is optionally indexed (as INDEXED_ID) if the user wishes to make
   * their data public.
   */
  public static class Installation {
    public static final String KIND = "Installation";

    public static final String ACCOUNT_ID = "accountId";  // email
    public static final String INDEXED_ID = "indexedId";
    public static final String MANUFACTURER = "manufacturer";
    public static final String MODEL = "model";
    public static final String MONTH_NUMBER = "monthNumber";
    public static final String NAME = "name";  // installation name
    public static final String OPAQUE_ID = "opaqueId";
    public static final String STREAM_COUNT = "streamCount";
    public static final String STREAM = "stream";
  }

  /**
   * A child entity of Installation that describes all the attempts on a single
   * puzzle that have occurred in that installation. Its key is the puzzle
   * string, and it is also indexed on the puzzle string.
   */
  public static class Attempts {
    public static final String KIND = "Attempts";

    public static final String FIRST_ATTEMPT = "firstAttempt";
    public static final String LATER_ATTEMPTS = "laterAttempts";
    public static final String NAME = "name";  // from Generator
    public static final String PUZZLE = "puzzle";
    public static final String PUZZLE_ID = "puzzleId";
    public static final String SOURCE = "source";  // user text
    public static final String VOTE = "vote";
  }

  /**
   * An embedded entity used within Attempts that describes one attempt.
   */
  public static class Attempt {
    public static final String ATTEMPT_ID = "attemptId";
    public static final String ELAPSED_MS = "elapsedMs";
    public static final String MOVES = "moves";  // json string
    public static final String NUM_MOVES = "numMoves";
    public static final String NUM_TRAILS = "numTrails";
    public static final String STOP_TIME = "stopTime";  // when completed
    public static final String WON = "won";
  }

  /**
   * A root entity that describes a single puzzle. Its key is the puzzle string,
   * ie the row-major string of the initial clues with periods standing in for
   * the blank locations. The stats are over successful first attempts only. The
   * number of attempts is actually the number of first attempts.
   */
  public static class Puzzle {
    public static final String KIND = "Puzzle";

    public static final String ELAPSED_MS_STAT = "elapsedMsStat";
    public static final String NAME = "name";  // from Generator
    public static final String NUM_ATTEMPTS = "numAttempts";
    public static final String NUM_DOWN_VOTES = "numDownVotes";
    public static final String NUM_MOVES_STAT = "numMovesStat";
    public static final String NUM_TRAILS_STAT = "numTrailsStat";
    public static final String NUM_UP_VOTES = "numUpVotes";
    public static final String SOURCES = "sources";
    public static final String STATS_TIMESTAMP = "statsTimestamp";
  }

  /**
   * An embedded entity used within Puzzle to describe various statistical
   * summaries. The quartiles and median may or may not be present, we only
   * calculate them if the count is sufficiently small.
   */
  public static class Stat {
    public static final String COUNT = "count";
    public static final String MAX = "max";
    public static final String MEAN = "mean";
    public static final String MEDIAN = "median";
    public static final String MIN = "min";
    public static final String Q1 = "q1";  // first quartile
    public static final String Q3 = "q3";  // third quartile
    public static final String STD_DEV = "stdDev";
    public static final String VAR = "var";
  }

  /**
   * A root entity that correlates all the installations that are associated
   * with one Google account. The key is the account's email address; there is
   * also an opaque numeric ID which is indexed.
   */
  public static class Account {
    public static final String KIND = "Account";

    public static final String INSTALLATION_IDS = "installationIds";
    public static final String OPAQUE_ID = "opaqueId";
  }
}
