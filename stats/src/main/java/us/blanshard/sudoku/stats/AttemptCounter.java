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
package us.blanshard.sudoku.stats;

import com.google.common.collect.Maps;

import java.util.Date;
import java.util.Map;

/**
 * Reads backup files from app engine's InstallationPuzzle table and counts
 * attempts per installation.
 */
public class AttemptCounter {
  static class Count {
    public int attempted;
    public int won;
    public Date latest;

    @Override
    public String toString() {
      return "<attempted: " + attempted + ", won: " + won + ", latest: " + latest + ">";
    }
  }

  public static void main(String[] args) {
    Map<String, Count> counts = Maps.newHashMap();
    for (AttemptInfo attempt : Attempts.datastoreBackup()) {
      Count count = counts.get(attempt.installationId);
      if (count == null) counts.put(attempt.installationId, count = new Count());
      ++count.attempted;
      if (count.latest == null || attempt.stopTime.after(count.latest))
        count.latest = attempt.stopTime;
      if (attempt.won)
        ++count.won;
    }

    for (Map.Entry<String, Count> entry : counts.entrySet()) {
      System.out.println(entry);
    }
  }
}
