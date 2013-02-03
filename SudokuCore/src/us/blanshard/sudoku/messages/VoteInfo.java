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
package us.blanshard.sudoku.messages;

/**
 * RPC messages for a puzzle's vote.
 */
public class VoteInfo {
  /** The RPC method for update. */
  public static final String UPDATE_METHOD = "vote.update";

  public static class UpdateParams {
    /** The installation ID. */
    public String installationId;
    /** The flattened string of the puzzle's starting grid. */
    public String puzzle;
    /** The vote for the puzzle: 1, 0, or -1. */
    public int vote;
  }

  public static class UpdateResult {

  }
}
