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
package us.blanshard.sudoku.appengine;

/**
 * Constants for database kind schemas.
 */
public class Schema {
  /**
   * A root entity that describes a single installation of the app on a device.
   */
  public static class Installation {
    public static final String KIND = "Installation";

    public static final String ACCOUNT_ID = "accountId";  // email
    public static final String INDEXED_ID = "indexedId";
    public static final String MANUFACTURER = "manufacturer";
    public static final String MODEL = "model";
    public static final String NAME = "name";  // installation name
    public static final String OPAQUE_ID = "opaqueId";
    public static final String STREAM_COUNT = "streamCount";
    public static final String STREAM = "stream";
  }

  /**
   * A child entity of Installation that describes all the attempts on a single
   * puzzle that have occurred in that installation.
   */
  public static class Attempts {
    public static final String KIND = "Attempts";

    public static final String FIRST_ATTEMPT = "firstAttempt";
    public static final String LATER_ATTEMPTS = "laterAttempts";
    public static final String NAME = "name";  // from Generator
    public static final String PUZZLE = "puzzle";
    public static final String PUZZLE_ID = "puzzleId";
    public static final String SOURCE = "source";  // user text
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
    public static final String STOP_TIME = "stopTime";
    public static final String WON = "won";
  }

  /**
   * A root entity that describes a single puzzle.
   */
  public static class Puzzle {
    public static final String KIND = "Puzzle";

    public static final String NAME = "name";  // from Generator
    public static final String NUM_ATTEMPTS = "numAttempts";
    public static final String NUM_WON = "numWon";
    public static final String SOURCES = "sources";
  }
}
