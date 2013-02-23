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

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.taskqueue.DeferredTask;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A deferred task to summarize the (first) attempts to solve a puzzle.
 */
public class PuzzleStatsTask implements DeferredTask {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = Logger.getLogger(PuzzleStatsTask.class.getName());

  /**
   * The largest number of attempts we will gather full statistics on. For
   * puzzles with more than this number of attempts, we will not report quartile
   * information.
   */
  private static final int MAX_FULL_STATS = 10000;

  private final String puzzle;

  public PuzzleStatsTask(String puzzle) {
    this.puzzle = puzzle;
  }

  /**
   * Creates a legal task name out of the puzzle string, such that the same
   * puzzle always ends up with the same task name.
   */
  public String getTaskName() {
    return "puzzle-stats-" + puzzle.replace('.', '0');
  }

  /**
   * A second legal task name, to work around the race condition inherent in the
   * task API. We create a backup task when the primary task is already queued,
   * so that if that primary task has already passed the point of including our
   * new data in its calculation, the backup task will eventually get to it.
   */
  public String getBackupTaskName() {
    return "backup-" + getTaskName();
  }

  @Override public void run() {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();

    DescriptiveStatistics elapsed1 = new DescriptiveStatistics();
    DescriptiveStatistics moves1 = new DescriptiveStatistics();
    DescriptiveStatistics trails1 = new DescriptiveStatistics();
    SummaryStatistics elapsed2 = null;
    SummaryStatistics moves2 = null;
    SummaryStatistics trails2 = null;

    int numAttempts = 0;
    int numDownVotes = 0;
    int numUpVotes = 0;

    Query query = new Query(Schema.Attempts.KIND)
        .setFilter(FilterOperator.EQUAL.of(Schema.Attempts.PUZZLE, puzzle));
    for (Entity attempts : ds.prepare(query).asIterable()) {
      EmbeddedEntity attempt = (EmbeddedEntity) attempts.getProperty(Schema.Attempts.FIRST_ATTEMPT);
      ++numAttempts;
      if ((Boolean) attempt.getProperty(Schema.Attempt.WON)) {
        if (attempts.hasProperty(Schema.Attempts.VOTE)) {
          int vote = ((Number) attempts.getProperty(Schema.Attempts.VOTE)).intValue();
          if (vote < 0) ++numDownVotes;
          else if (vote > 0) ++numUpVotes;
        }

        double elapsed = ((Number) attempt.getProperty(Schema.Attempt.ELAPSED_MS)).doubleValue();
        if (elapsed2 == null) elapsed1.addValue(elapsed);
        else elapsed2.addValue(elapsed);

        double numMoves = ((Number) attempt.getProperty(Schema.Attempt.NUM_MOVES)).doubleValue();
        if (moves2 == null) moves1.addValue(numMoves);
        else moves2.addValue(numMoves);

        double numTrails = ((Number) attempt.getProperty(Schema.Attempt.NUM_TRAILS)).doubleValue();
        if (trails2 == null) trails1.addValue(numTrails);
        else trails2.addValue(numTrails);

        if (elapsed2 == null && elapsed1.getN() > MAX_FULL_STATS) {
          elapsed2 = copyToSummary(elapsed1);
          moves2 = copyToSummary(moves1);
          trails2 = copyToSummary(trails1);
        }
      }
    }

    Entity entity;
    Key key = KeyFactory.createKey(Schema.Puzzle.KIND, puzzle);
    Transaction tx = ds.beginTransaction();
    try {
      try {
        entity = ds.get(key);
      } catch (EntityNotFoundException e) {
        logger.log(Level.SEVERE, "Puzzle entity missing: " + puzzle, e);
        return;  // Throwing would be bad, because it would re-queue the task.
      }

      entity.setUnindexedProperty(Schema.Puzzle.STATS_TIMESTAMP, System.currentTimeMillis());

      entity.setUnindexedProperty(Schema.Puzzle.NUM_ATTEMPTS, numAttempts);
      entity.setUnindexedProperty(Schema.Puzzle.NUM_DOWN_VOTES, numDownVotes);
      entity.setUnindexedProperty(Schema.Puzzle.NUM_UP_VOTES, numUpVotes);

      entity.setUnindexedProperty(Schema.Puzzle.ELAPSED_MS_STAT, makeStat(elapsed1, elapsed2));
      entity.setUnindexedProperty(Schema.Puzzle.NUM_MOVES_STAT, makeStat(moves1, moves2));
      entity.setUnindexedProperty(Schema.Puzzle.NUM_TRAILS_STAT, makeStat(trails1, trails2));

      ds.put(tx, entity);
      tx.commit();
    } finally {
      if (tx.isActive()) tx.rollback();
    }

  }

  /**
   * Duplicates the given descriptive stats object as a summary.
   */
  private SummaryStatistics copyToSummary(DescriptiveStatistics in) {
    SummaryStatistics answer = new SummaryStatistics();
    for (int i = 0; i < in.getN(); ++i)
      answer.addValue(in.getElement(i));
    return answer;
  }

  private EmbeddedEntity makeStat(DescriptiveStatistics s1, SummaryStatistics s2) {
    EmbeddedEntity stat = new EmbeddedEntity();
    StatisticalSummary summary = s1 == null ? s2 : s1;
    stat.setUnindexedProperty(Schema.Stat.COUNT, summary.getN());
    stat.setUnindexedProperty(Schema.Stat.MIN, summary.getMin());
    stat.setUnindexedProperty(Schema.Stat.MAX, summary.getMax());
    stat.setUnindexedProperty(Schema.Stat.MEAN, summary.getMean());
    stat.setUnindexedProperty(Schema.Stat.STD_DEV, summary.getStandardDeviation());
    stat.setUnindexedProperty(Schema.Stat.VAR, summary.getVariance());
    if (s1 != null) {
      stat.setUnindexedProperty(Schema.Stat.MEDIAN, s1.getPercentile(0.5));
      stat.setUnindexedProperty(Schema.Stat.Q1, s1.getPercentile(0.25));
      stat.setUnindexedProperty(Schema.Stat.Q3, s1.getPercentile(0.75));
    }
    return stat;
  }
}
