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

import static java.util.logging.Level.SEVERE;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.messages.PuzzleRpcs.AttemptParams;
import us.blanshard.sudoku.messages.PuzzleRpcs.AttemptResult;
import us.blanshard.sudoku.messages.Rpc;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskAlreadyExistsException;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Saves an attempt to solve a puzzle, optionally creating an entity for the
 * puzzle as well.
 */
public class AttemptUpdateMethod extends RpcMethod<AttemptParams, AttemptResult> {

  private static final TypeToken<AttemptParams> TOKEN = new TypeToken<AttemptParams>() {};
  private static final Logger logger = Logger.getLogger(AttemptUpdateMethod.class.getName());

  public AttemptUpdateMethod() {
    super(TOKEN);
  }

  @Override public AttemptResult call(AttemptParams params) throws MethodException {
    String puzzleString;
    boolean won;
    int numTrails;
    try {
      Grid clues = Grid.fromString(params.puzzle);
      Sudoku game = new Sudoku(clues, Sudoku.nullRegistry(), params.history, params.elapsedMs);
      puzzleString = clues.toFlatString();
      won = game.getState().getGrid().getState() == Grid.State.SOLVED;
      numTrails = game.getNumTrails();
    } catch (RuntimeException e) {
      throw new MethodException(e, Rpc.invalidParams(params));
    }
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    savePuzzle(ds, puzzleString, params.name, params.source);
    AttemptResult result = new AttemptResult();
    result.wasFirst = saveAttempt(ds, puzzleString, params, won, numTrails);
    return result;
  }

  /**
   * Creates or updates the Puzzle entity, if necessary.
   */
  private void savePuzzle(DatastoreService ds, String puzzleString, String name, String source) {
    Key key = KeyFactory.createKey(Schema.Puzzle.KIND, puzzleString);
    Entity entity;
    boolean addSource = false;
    try {
      entity = ds.get(key);
      if (source != null)
        addSource = !sourceIsIn(source, entity);
      if ((name == null || entity.hasProperty(Schema.Puzzle.NAME))
          && (source == null || !addSource))
        return;  // nothing to do.
    } catch (EntityNotFoundException e) {
      // ...we'll create it below, within the transaction.
    }

    // At this point, we have work to do.  Open a transaction to do it.
    Transaction tx = ds.beginTransaction();
    try {
      try {
        entity = ds.get(key);
      } catch (EntityNotFoundException e) {
        entity = new Entity(key);
      }

      if (name != null && !entity.hasProperty(Schema.Puzzle.NAME))
        entity.setUnindexedProperty(Schema.Puzzle.NAME, name);

      if (addSource) {
        List<String> sources = Lists.newArrayList();
        if (entity.hasProperty(Schema.Puzzle.SOURCES)) {
          @SuppressWarnings("unchecked")
          Collection<String> existing = (Collection<String>) entity.getProperty(Schema.Puzzle.SOURCES);
          sources.addAll(existing);
        }
        sources.add(source);
        entity.setUnindexedProperty(Schema.Puzzle.SOURCES, sources);
      }

      ds.put(tx, entity);
      tx.commit();
    } finally {
      if (tx.isActive()) tx.rollback();
    }
  }

  /**
   * Checks whether the given source string is included in the given puzzle
   * entity. Ignores case and leading/trailing whitespace.
   */
  private boolean sourceIsIn(String source, Entity entity) {
    if (!entity.hasProperty(Schema.Puzzle.SOURCES))
      return false;

    @SuppressWarnings("unchecked")
    Collection<String> sources = (Collection<String>) entity.getProperty(Schema.Puzzle.SOURCES);
    String strippedSource = source.trim().toLowerCase(Locale.ENGLISH);
    for (String s : sources) {
      if (s.trim().toLowerCase(Locale.ENGLISH).equals(strippedSource))
        return true;
    }

    return false;
  }

  /**
   * Creates or adds an Attempts entity under the Installation entity whose ID
   * is in the given parameters, and adds an Attempt embedded entity to the
   * Attempts. Makes a distinction between a first attempt and subsequent
   * attempts, and returns a flag indicating whether this was the first attempt.
   */
  private boolean saveAttempt(DatastoreService ds, String puzzleString, AttemptParams params,
      boolean won, int numTrails) {
    boolean wasFirst = false;
    Transaction tx = ds.beginTransaction();
    try {
      Entity attempts;
      Key installationKey = KeyFactory.createKey(Schema.Installation.KIND, params.installationId);
      Key key = installationKey.getChild(Schema.Attempts.KIND, puzzleString);
      try {
        attempts = ds.get(key);
      } catch (EntityNotFoundException e) {
        attempts = new Entity(key);
        attempts.setProperty(Schema.Attempts.PUZZLE, puzzleString);
      }

      attempts.setUnindexedProperty(Schema.Attempts.PUZZLE_ID, params.puzzleId);
      if (params.name != null)
        attempts.setUnindexedProperty(Schema.Attempts.NAME, params.name);
      if (params.source != null)
        attempts.setUnindexedProperty(Schema.Attempts.SOURCE, params.source);

      EmbeddedEntity attempt = new EmbeddedEntity();
      attempt.setUnindexedProperty(Schema.Attempt.ATTEMPT_ID, params.attemptId);
      attempt.setUnindexedProperty(Schema.Attempt.ELAPSED_MS, params.elapsedMs);
      attempt.setUnindexedProperty(Schema.Attempt.MOVES, new Text(RpcJson.GSON.toJson(params.history)));
      attempt.setUnindexedProperty(Schema.Attempt.NUM_MOVES, params.history.size());
      attempt.setUnindexedProperty(Schema.Attempt.NUM_TRAILS, numTrails);
      attempt.setUnindexedProperty(Schema.Attempt.STOP_TIME, new Date(params.stopTime));
      attempt.setUnindexedProperty(Schema.Attempt.WON, won);

      if (attempts.hasProperty(Schema.Attempts.FIRST_ATTEMPT)) {
        if (isSameAttempt(attempt, (EmbeddedEntity) attempts.getProperty(Schema.Attempts.FIRST_ATTEMPT))) {
          wasFirst = true;
          logger.info("First attempt already present for " + puzzleString);
        } else {
          boolean alreadyThere = false;
          List<EmbeddedEntity> later = Lists.newArrayList();
          if (attempts.hasProperty(Schema.Attempts.LATER_ATTEMPTS)) {
            @SuppressWarnings("unchecked")
            Collection<EmbeddedEntity> existing = (Collection<EmbeddedEntity>) attempts.getProperty(Schema.Attempts.LATER_ATTEMPTS);
            for (EmbeddedEntity e : existing) {
              later.add(e);
              if (isSameAttempt(attempt, e))
                alreadyThere = true;
            }
          }
          if (alreadyThere) {
            logger.info("Later attempt already present for " + puzzleString);
          } else {
            later.add(attempt);
            attempts.setUnindexedProperty(Schema.Attempts.LATER_ATTEMPTS, later);
          }
        }
      } else {
        attempts.setUnindexedProperty(Schema.Attempts.FIRST_ATTEMPT, attempt);
        wasFirst = true;
      }

      ds.put(tx, attempts);
      tx.commit();

      if (wasFirst)
        queuePuzzleStatsTask(puzzleString);
    } finally {
      if (tx.isActive()) tx.rollback();
    }
    return wasFirst;
  }

  /**
   * Tells whether the two attempt entities have the same ID.
   */
  private boolean isSameAttempt(EmbeddedEntity a, EmbeddedEntity b) {
    return Objects.equal(
        a.getProperty(Schema.Attempt.ATTEMPT_ID), b.getProperty(Schema.Attempt.ATTEMPT_ID));
  }

  private void queuePuzzleStatsTask(String puzzle) {
    Queue queue = QueueFactory.getDefaultQueue();
    PuzzleStatsTask task = new PuzzleStatsTask(puzzle);
    try {
      queue.add(TaskOptions.Builder
          .withCountdownMillis(TimeUnit.SECONDS.toMillis(30))
          .payload(task)
          .taskName(task.getTaskName()));
    } catch (TaskAlreadyExistsException e) {
      logger.info("puzzle stats task already exists for " + puzzle);
    } catch (Exception e) {
      logger.log(SEVERE, "Unable to queue puzzle stats task for " + puzzle, e);
    }
  }
}
