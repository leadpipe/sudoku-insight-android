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
package us.blanshard.sudoku.gen;

import static com.google.common.base.Preconditions.checkArgument;

import us.blanshard.sudoku.core.Solver.Result;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;

import java.util.Iterator;
import java.util.Random;

/**
 * Static methods to generate Sudokus. They are returned as JSON objects
 * containing properties keyed by constants defined in this class.
 *
 * @author Luke Blanshard
 */
public class Generator {

  /**
   * The JSON property key whose value is the flattened string of the puzzle's
   * starting grid.
   */
  public static final String PUZZLE_KEY = "puzzle";

  /**
   * The JSON property key whose value is the puzzle's name.  Generated puzzles
   * are named as version:stream:year-month:counter, where version is the
   * generation algorithm version number, stream is the generated-puzzle-stream
   * number, year is a 4-digit year number, month is a month number between 1
   * and 12, and counter is a positive integer.
   */
  public static final String NAME_KEY = "name";

  /**
   * The JSON property key whose value is the symmetry exhibited by the puzzle's
   * clues, if any.
   */
  public static final String SYMMETRY_KEY = "symmetry";

  /**
   * The JSON property key whose value is the symmetry underlying the puzzle's
   * clues, when that symmetry has been broken.
   */
  public static final String BROKEN_SYMMETRY_KEY = "brokenSymmetry";

  /**
   * The JSON property key whose value is the number of solutions the puzzle
   * has.
   */
  public static final String NUM_SOLUTIONS_KEY = "numSolutions";

  /**
   * A JSON property key for a puzzle's source. Not used in puzzles generated by
   * this class.
   */
  public static final String SOURCE_KEY = "source";

  /**
   * A JSON property key for a puzzle's generation algorithm version number.
   */
  public static final String VERSION_KEY = "version";

  /**
   * A JSON property key for a puzzle's generated-puzzle-stream number.
   */
  public static final String STREAM_KEY = "stream";

  /**
   * A JSON property key for a puzzle's year.
   */
  public static final String YEAR_KEY = "year";

  /**
   * A JSON property key for a puzzle's month.
   */
  public static final String MONTH_KEY = "month";

  /**
   * A JSON property key for a puzzle's counter.
   */
  public static final String COUNTER_KEY = "counter";

  /**
   * Identifies the generation algorithm corresponding to {@link
   * #generateBasicPuzzle}.
   */
  public static final int BASIC_VERSION = 1;

  /**
   * The basic number of streams we will shard everyone into.
   */
  public static final int NUM_STREAMS = 5;

  /**
   * How likely it is that a given puzzle will be generated with parameters that
   * allow it to be improper. (Around 20% of such generations end up with single
   * solutions anyway.) Changing this requires bumping the
   * {@linkplain #BASIC_VERSION version number}.
   */
  private static final double CHANCE_OF_IMPROPER = 0.125;

  /**
   * The largest number of solutions for any puzzle generated. Changing this
   * requires bumping the {@linkplain #BASIC_VERSION version number}.
   */
  public static final int MAX_SOLUTIONS = 3;

  /**
   * The largest number of "holes" (unassigned locations) permitted in the
   * intersection of solutions to generated improper puzzles. Changing this
   * requires bumping the {@linkplain #BASIC_VERSION version number}.
   */
  public static final int MAX_HOLES = 7;

  /**
   * Generates a proper Sudoku with the given parameters, returns it as a JSON
   * object with the properties {@link #PUZZLE_KEY}, {@link #NAME_KEY}, and
   * either {@link #SYMMETRY_KEY} or {@link #BROKEN_SYMMETRY_KEY}.
   */
  public static JsonObject generateBasicPuzzle(int stream, int year, int month, int counter) {
    return generatePuzzle(BASIC_VERSION, stream, year, month, counter);
  }

  /**
   * Splits a puzzle name as returned by {@link #generatePuzzle} into its
   * constituent properties {@link #VERSION_KEY}, {@link #STREAM_KEY},
   * {@link #YEAR_KEY}, {@link #MONTH_KEY}, and {@link #COUNTER_KEY}.
   */
  public static JsonObject parsePuzzleName(String name) {
    Iterator<String> it = Splitter.on(':').split(name).iterator();
    String version = it.next();
    String stream = it.next();
    String yearMonth = it.next();
    String counter = it.next();
    checkArgument(!it.hasNext());
    it = Splitter.on('-').split(yearMonth).iterator();
    String year = it.next();
    String month = it.next();
    checkArgument(!it.hasNext());

    JsonObject answer = new JsonObject();
    answer.addProperty(VERSION_KEY, Integer.parseInt(version));
    answer.addProperty(STREAM_KEY, Integer.parseInt(stream));
    answer.addProperty(YEAR_KEY, Integer.parseInt(year));
    answer.addProperty(MONTH_KEY, Integer.parseInt(month));
    answer.addProperty(COUNTER_KEY, Integer.parseInt(counter));
    return answer;
  }

  /**
   * Generates the puzzle implied by the name in the given puzzle descriptor.
   */
  public static JsonObject regeneratePuzzle(JsonObject puzzleDesc) {
    return regeneratePuzzle(puzzleDesc.get(NAME_KEY).getAsString());
  }

  /**
   * Generates the puzzle whose generated name is as given.
   */
  public static JsonObject regeneratePuzzle(String name) {
    JsonObject parts = parsePuzzleName(name);
    return generatePuzzle(
        parts.get(VERSION_KEY).getAsInt(),
        parts.get(STREAM_KEY).getAsInt(),
        parts.get(YEAR_KEY).getAsInt(),
        parts.get(MONTH_KEY).getAsInt(),
        parts.get(COUNTER_KEY).getAsInt());
  }

  /**
   * Generates the puzzle with the given parameters.
   */
  public static JsonObject generatePuzzle(
      int version, int stream, int year, int month, int counter) {
    String name = version + ":" + stream + ":" + year + "-" + month + ":" + counter;
    HashCode hash = Hashing.murmur3_128().hashString(name, Charsets.UTF_8);
    Random random = new Random(hash.asLong());

    switch (version) {
      case BASIC_VERSION:
        return generateBasicPuzzle(name, random);

      default:
        throw new IllegalArgumentException("Unrecognized generation algorithm version number " + version);
    }
  }

  private static JsonObject generateBasicPuzzle(String name, Random random) {
    int maxSolutions = random.nextDouble() < CHANCE_OF_IMPROPER ? MAX_SOLUTIONS : 1;
    int maxHoles = maxSolutions == 1 ? 0 : MAX_HOLES;
    Symmetry symmetry = Symmetry.choose(random);
    Result result = GenerationStrategy.SUBTRACTIVE_RANDOM.generate(
        random, symmetry, maxSolutions, maxHoles);
    String symKey = symmetry.describes(result.start) ? SYMMETRY_KEY : BROKEN_SYMMETRY_KEY;

    JsonObject answer = new JsonObject();
    answer.addProperty(PUZZLE_KEY, result.start.toFlatString());
    answer.addProperty(NAME_KEY, name);
    answer.addProperty(symKey, symmetry.getName());
    answer.addProperty(NUM_SOLUTIONS_KEY, result.numSolutions);
    return answer;
  }
}
