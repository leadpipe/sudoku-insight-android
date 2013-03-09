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
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.gen.Generator;
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
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.util.Collection;
import java.util.Date;
import java.util.List;
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
      puzzleString = clues.toFlatString();
      if (!puzzleString.equals(params.puzzle)) {
        throw new IllegalArgumentException("Incoming puzzle not in canonical form: " + params.puzzle);
      }
      if (params.name != null) {
        JsonObject generated = Generator.regeneratePuzzle(params.name);
        if (!puzzleString.equals(generated.get(Generator.PUZZLE_KEY).getAsString())) {
          logger.warning("Regenerated puzzle doesn't match for " + params.name + ": given "
              + puzzleString + ", generated " + generated);
          params.name = null;
        }
      }
      long lastTs = 0;
      for (Move move : params.history) {
        if (move.timestamp < lastTs)
          throw new IllegalArgumentException("Bad move timestamp " + move);
        lastTs = move.timestamp;
      }
      if (params.elapsedMs < lastTs)
        throw new IllegalArgumentException("Bad elapsed time");
      Sudoku game = new Sudoku(clues, Sudoku.nullRegistry(), params.history, params.elapsedMs);
      won = game.getState().getGrid().getState() == Grid.State.SOLVED;
      numTrails = game.getNumTrails();
    } catch (RuntimeException e) {
      throw new MethodException(e, Rpc.invalidParams(params));
    }
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    AttemptResult result = new AttemptResult();
    result.wasFirst = saveAttempt(ds, puzzleString, params, won, numTrails);
    return result;
  }

  /**
   * Creates or adds an InstallationPuzzle entity under the Installation entity
   * whose ID is in the given parameters, and adds an Attempt embedded entity to
   * the InstallationPuzzle. Makes a distinction between a first attempt and
   * subsequent attempts, and returns a flag indicating whether this was the
   * first attempt.
   */
  private boolean saveAttempt(DatastoreService ds, String puzzleString, AttemptParams params,
      boolean won, int numTrails) throws MethodException {
    boolean wasFirst = false;
    Transaction tx = ds.beginTransaction();
    try {
      Entity instPuzzle;
      Key installationKey = KeyFactory.createKey(Schema.Installation.KIND, params.installationId);
      Key key = installationKey.getChild(Schema.InstallationPuzzle.KIND, puzzleString);
      try {
        instPuzzle = ds.get(key);
      } catch (EntityNotFoundException e) {
        instPuzzle = new Entity(key);
        instPuzzle.setProperty(Schema.InstallationPuzzle.PUZZLE, puzzleString);
      }

      instPuzzle.setUnindexedProperty(Schema.InstallationPuzzle.PUZZLE_ID, params.puzzleId);
      if (params.name != null)
        instPuzzle.setUnindexedProperty(Schema.InstallationPuzzle.NAME, params.name);
      if (params.source != null)
        instPuzzle.setUnindexedProperty(Schema.InstallationPuzzle.SOURCE, params.source.trim());

      EmbeddedEntity attempt = new EmbeddedEntity();
      attempt.setUnindexedProperty(Schema.Attempt.ATTEMPT_ID, params.attemptId);
      attempt.setUnindexedProperty(Schema.Attempt.ELAPSED_MS, params.elapsedMs);
      attempt.setUnindexedProperty(Schema.Attempt.MOVES, new Text(RpcJson.GSON.toJson(params.history)));
      attempt.setUnindexedProperty(Schema.Attempt.NUM_MOVES, params.history.size());
      attempt.setUnindexedProperty(Schema.Attempt.NUM_TRAILS, numTrails);
      attempt.setUnindexedProperty(Schema.Attempt.STOP_TIME, new Date(params.stopTime));
      attempt.setUnindexedProperty(Schema.Attempt.WON, won);

      boolean hasFirst = instPuzzle.hasProperty(Schema.InstallationPuzzle.FIRST_ATTEMPT);
      if (!hasFirst || isSameAttempt(attempt, (EmbeddedEntity) instPuzzle.getProperty(Schema.InstallationPuzzle.FIRST_ATTEMPT))) {
        instPuzzle.setUnindexedProperty(Schema.InstallationPuzzle.FIRST_ATTEMPT, attempt);
        wasFirst = true;
        if (hasFirst)
          logger.info("First attempt already present for " + puzzleString);
      } else {
        boolean alreadyThere = false;
        List<EmbeddedEntity> later = Lists.newArrayList();
        if (instPuzzle.hasProperty(Schema.InstallationPuzzle.LATER_ATTEMPTS)) {
          @SuppressWarnings("unchecked")
          Collection<EmbeddedEntity> existing = (Collection<EmbeddedEntity>) instPuzzle.getProperty(Schema.InstallationPuzzle.LATER_ATTEMPTS);
          for (EmbeddedEntity e : existing) {
            if (isSameAttempt(attempt, e)) {
              later.add(attempt);
              alreadyThere = true;
              logger.info("Later attempt already present for " + puzzleString);
            } else {
              later.add(e);
            }
          }
        }
        if (!alreadyThere)
          later.add(attempt);
        instPuzzle.setUnindexedProperty(Schema.InstallationPuzzle.LATER_ATTEMPTS, later);
      }

      ds.put(tx, instPuzzle);
      Transactions.commit(tx);

      if (wasFirst)
        TaskQueuer.queuePuzzleStatsTask(puzzleString);
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
}
