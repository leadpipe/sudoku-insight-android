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

import us.blanshard.sudoku.game.Move;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Structs for REST results.
 */
public class Rest {
  public static class InstallationIds {
    public List<Long> ids;
    @Nullable public String nextPageToken;
  }

  public static class Installation {
    public long id;
    @Nullable public Integer androidSdk;
    @Nullable public String model;
    @Nullable public String manufacturer;
  }

  public static class InstallationPuzzles {
    public List<String> puzzles;
    @Nullable public String nextPageToken;
  }

  public static class InstallationPuzzle {
    public long installationId;
    public String puzzle;
    public Attempt firstAttempt;
    public List<Attempt> laterAttempts;
  }

  public static class Attempt {
    public List<Move> moves;
    public long elapsedMs;
    public long stopTime;
    public boolean won;
  }

  public static class Puzzle {
    public String puzzle;
    @Nullable public String name;
    @Nullable public List<String> sources;
    @Nullable public String symmetry;
    @Nullable public String brokenSymmetry;
    public int numAttempts;
    public int numSolutions;
    public int numDownVotes;
    public int numUpVotes;
    public Stat elapsedMsStat;
    public Stat numMovesStat;
    public Stat numTrailsStat;
    public long statsTimestamp;
  }

  public static class Stat {
    public int count;
    public double min;
    public double max;
    public double mean;
    public double stdDev;
    public double var;
    @Nullable public Double median;
    @Nullable public Double q1;
    @Nullable public Double q3;
  }
}
