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

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.gen.Generator;

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
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A deferred task to summarize the (first) attempts to solve a puzzle.
 */
public class PuzzleStatsTask implements DeferredTask {
  /**
   *
   */
  private static final int MAX_SOURCES = 10;
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

    String name = null;
    Set<String> otherNames = Sets.newHashSet();
    Multiset<String> sources = HashMultiset.create();

    DescriptiveStatistics elapsed1 = new DescriptiveStatistics();
    DescriptiveStatistics moves1 = new DescriptiveStatistics();
    DescriptiveStatistics trails1 = new DescriptiveStatistics();
    SummaryStatistics elapsed2 = null;
    SummaryStatistics moves2 = null;
    SummaryStatistics trails2 = null;

    int numAttempts = 0;
    int numDownVotes = 0;
    int numUpVotes = 0;

    Query query = new Query(Schema.InstallationPuzzle.KIND)
        .setFilter(FilterOperator.EQUAL.of(Schema.InstallationPuzzle.PUZZLE, puzzle));
    for (Entity instPuzzle : ds.prepare(query).asIterable()) {
      if (instPuzzle.hasProperty(Schema.InstallationPuzzle.NAME)) {
        String newName = (String) instPuzzle.getProperty(Schema.InstallationPuzzle.NAME);
        if (name == null) name = newName;
        else if (!name.equals(newName) && otherNames.add(newName)) {
          // Don't really expect to ever see this, but it could happen.
          logger.warning("Duplicate names found: " + name + " and " + newName);
        }
      }
      if (instPuzzle.hasProperty(Schema.InstallationPuzzle.SOURCE))
        sources.add((String) instPuzzle.getProperty(Schema.InstallationPuzzle.SOURCE));
      EmbeddedEntity attempt = (EmbeddedEntity) instPuzzle.getProperty(Schema.InstallationPuzzle.FIRST_ATTEMPT);
      ++numAttempts;
      if ((Boolean) attempt.getProperty(Schema.Attempt.WON)) {
        if (instPuzzle.hasProperty(Schema.InstallationPuzzle.VOTE)) {
          int vote = ((Number) instPuzzle.getProperty(Schema.InstallationPuzzle.VOTE)).intValue();
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

    Key key = KeyFactory.createKey(Schema.Puzzle.KIND, puzzle);
    Entity entity;
    Transaction tx = ds.beginTransaction();
    try {
      try {
        entity = ds.get(tx, key);
      } catch (EntityNotFoundException e) {
        entity = new Entity(key);
        JsonObject props = Generator.makePuzzleProperties(Solver.solve(Grid.fromString(puzzle), 20));
        entity.setUnindexedProperty(Schema.Puzzle.NUM_SOLUTIONS,
            props.get(Generator.NUM_SOLUTIONS_KEY).getAsInt());
        if (props.has(Generator.BROKEN_SYMMETRY_KEY))
          entity.setUnindexedProperty(Schema.Puzzle.BROKEN_SYMMETRY,
              props.get(Generator.BROKEN_SYMMETRY_KEY).getAsString());
        if (props.has(Generator.SYMMETRY_KEY))
          entity.setUnindexedProperty(Schema.Puzzle.SYMMETRY,
              props.get(Generator.SYMMETRY_KEY).getAsString());
      }
      if (name != null)
        entity.setUnindexedProperty(Schema.Puzzle.NAME, name);

      if (!sources.isEmpty()) {
        if (sources.elementSet().size() > MAX_SOURCES) {
          sources = Multisets.copyHighestCountFirst(sources);
        }
        List<String> savedSources = Lists.newArrayList();
        for (String s : sources.elementSet()) {
          savedSources.add(s);
          if (savedSources.size() >= MAX_SOURCES)
            break;
        }
        Collections.sort(savedSources, String.CASE_INSENSITIVE_ORDER);
        entity.setUnindexedProperty(Schema.Puzzle.SOURCES, savedSources);
      }

      entity.setProperty(Schema.Puzzle.STATS_TIMESTAMP, System.currentTimeMillis());

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
