/*
 * Copyright 2013 Google Inc. Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package us.blanshard.sudoku.messages;

import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.gen.Generator;

import java.util.List;

import javax.annotation.Nullable;

/**
 * RPC messages for attempts to solve puzzles.
 */
public class AttemptInfo {
  /** The RPC tag for update. */
  public static final String UPDATE_TAG = "attempt.update";

  public static class UpdateRequest {
    /** The installation ID. */
    public String installationId;
    /** The identifier of the attempt within the given installation. */
    public long attemptId;
    /** The flattened string of the puzzle's starting grid. */
    public String puzzle;
    /** If generated by {@link Generator}, its name for the puzzle. */
    @Nullable public String name;
    /**
     * If captured or entered from an external source, the user's description of
     * that source.
     */
    @Nullable public String source;
    /** The moves made during this solution attempt. */
    public List<Move> history;
    /**
     * Total elapsed milliseconds. This must match the timestamp on the last
     * move, if the attempt succeeded, but may be larger than that if the
     * attempt failed.
     */
    public long elapsedMs;
    /** When the attempt was finished, in milliseconds since the epoch. */
    public long stopTime;
  }

  public static class UpdateResponse {

  }
}
