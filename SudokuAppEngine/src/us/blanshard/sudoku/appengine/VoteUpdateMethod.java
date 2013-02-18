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

import us.blanshard.sudoku.messages.PuzzleRpcs.VoteParams;
import us.blanshard.sudoku.messages.PuzzleRpcs.VoteResult;
import us.blanshard.sudoku.messages.Rpc;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.gson.reflect.TypeToken;

/**
 * Modifies the vote associated with a puzzle for a given installation.
 */
public class VoteUpdateMethod extends RpcMethod<VoteParams, VoteResult> {

  private static final TypeToken<VoteParams> TOKEN = new TypeToken<VoteParams>() {};

  public VoteUpdateMethod() {
    super(TOKEN);
  }

  @Override public VoteResult call(VoteParams params) throws MethodException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Transaction tx = ds.beginTransaction();
    try {
      Entity attempts;
      Key installationKey = KeyFactory.createKey(Schema.Installation.KIND, params.installationId);
      Key key = installationKey.getChild(Schema.Attempts.KIND, params.puzzle);
      try {
        attempts = ds.get(key);
      } catch (EntityNotFoundException e) {
        throw new MethodException(e, Rpc.invalidParams(params));
      }

      if (params.vote == 0)
        attempts.removeProperty(Schema.Attempts.VOTE);
      else
        attempts.setUnindexedProperty(Schema.Attempts.VOTE, params.vote);

      ds.put(tx, attempts);
      tx.commit();
    } finally {
      if (tx.isActive()) tx.rollback();
    }
    return new VoteResult();
  }
}
