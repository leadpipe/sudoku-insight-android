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

import static us.blanshard.sudoku.appengine.Schema.Puzzle.ELAPSED_MS_STAT;
import static us.blanshard.sudoku.appengine.Schema.Puzzle.KIND;
import static us.blanshard.sudoku.appengine.Schema.Puzzle.NAME;
import static us.blanshard.sudoku.appengine.Schema.Puzzle.NUM_ATTEMPTS;
import static us.blanshard.sudoku.appengine.Schema.Puzzle.NUM_DOWN_VOTES;
import static us.blanshard.sudoku.appengine.Schema.Puzzle.NUM_MOVES_STAT;
import static us.blanshard.sudoku.appengine.Schema.Puzzle.NUM_TRAILS_STAT;
import static us.blanshard.sudoku.appengine.Schema.Puzzle.NUM_UP_VOTES;
import static us.blanshard.sudoku.appengine.Schema.Puzzle.SOURCES;

import us.blanshard.sudoku.messages.PuzzleRpcs.PuzzleParams;
import us.blanshard.sudoku.messages.PuzzleRpcs.PuzzleResult;
import us.blanshard.sudoku.messages.PuzzleRpcs.Stat;
import us.blanshard.sudoku.messages.Rpc;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;

import java.util.Collection;

/**
 * Retrieves information about a puzzle.
 */
public class PuzzleGetMethod extends RpcMethod<PuzzleParams, PuzzleResult> {

  private static final TypeToken<PuzzleParams> TOKEN = new TypeToken<PuzzleParams>() {};

  public PuzzleGetMethod() {
    super(TOKEN);
  }

  @Override public PuzzleResult call(PuzzleParams params) throws MethodException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Key key = KeyFactory.createKey(KIND, params.puzzle);
    Entity entity;
    try {
      entity = ds.get(key);
    } catch (EntityNotFoundException e) {
      throw new MethodException(e, Rpc.invalidParams(params));
    }
    PuzzleResult result = new PuzzleResult();
    result.name = (String) entity.getProperty(NAME);

    if (entity.hasProperty(SOURCES)) {
      @SuppressWarnings("unchecked")
      Collection<String> sources = (Collection<String>) entity.getProperty(SOURCES);
      result.sources = Lists.newArrayList(sources);
    }

    if (entity.hasProperty(NUM_ATTEMPTS))
      result.numAttempts = ((Number) entity.getProperty(NUM_ATTEMPTS)).intValue();
    if (entity.hasProperty(NUM_UP_VOTES))
      result.numUpVotes = ((Number) entity.getProperty(NUM_UP_VOTES)).intValue();
    if (entity.hasProperty(NUM_DOWN_VOTES))
      result.numDownVotes = ((Number) entity.getProperty(NUM_DOWN_VOTES)).intValue();

    if (entity.hasProperty(ELAPSED_MS_STAT))
      result.elapsedMsStat = getStat(entity, ELAPSED_MS_STAT);
    if (entity.hasProperty(NUM_MOVES_STAT))
      result.numMovesStat = getStat(entity, NUM_MOVES_STAT);
    if (entity.hasProperty(NUM_TRAILS_STAT))
      result.numTrailsStat = getStat(entity, NUM_TRAILS_STAT);

    return result;
  }

  /**
   * Converts an embedded entity into a Stat.
   */
  private Stat getStat(Entity entity, String propertyName) {
    Stat stat = new Stat();
    EmbeddedEntity e = (EmbeddedEntity) entity.getProperty(propertyName);
    stat.count = ((Number) e.getProperty(Schema.Stat.COUNT)).intValue();
    stat.min = ((Number) e.getProperty(Schema.Stat.MIN)).doubleValue();
    stat.max = ((Number) e.getProperty(Schema.Stat.MAX)).doubleValue();
    stat.mean = ((Number) e.getProperty(Schema.Stat.MEAN)).doubleValue();
    stat.stdDev = ((Number) e.getProperty(Schema.Stat.STD_DEV)).doubleValue();
    stat.var = ((Number) e.getProperty(Schema.Stat.VAR)).doubleValue();
    if (e.hasProperty(Schema.Stat.MEDIAN)) {
      stat.median = ((Number) e.getProperty(Schema.Stat.MEDIAN)).doubleValue();
      stat.q1 = ((Number) e.getProperty(Schema.Stat.Q1)).doubleValue();
      stat.q3 = ((Number) e.getProperty(Schema.Stat.Q3)).doubleValue();
    }
    return stat;
  }
}
