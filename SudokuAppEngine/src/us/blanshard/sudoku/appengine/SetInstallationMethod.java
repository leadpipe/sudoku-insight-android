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

import static java.util.logging.Level.INFO;
import static us.blanshard.sudoku.appengine.Schema.Installation.ACCOUNT_ID;
import static us.blanshard.sudoku.appengine.Schema.Installation.INDEXED_ID;
import static us.blanshard.sudoku.appengine.Schema.Installation.KIND;
import static us.blanshard.sudoku.appengine.Schema.Installation.MANUFACTURER;
import static us.blanshard.sudoku.appengine.Schema.Installation.MODEL;
import static us.blanshard.sudoku.appengine.Schema.Installation.NAME;
import static us.blanshard.sudoku.appengine.Schema.Installation.OPAQUE_ID;
import static us.blanshard.sudoku.appengine.Schema.Installation.STREAM;
import static us.blanshard.sudoku.appengine.Schema.Installation.STREAM_COUNT;

import us.blanshard.sudoku.messages.InstallationRpcs.UpdateParams;
import us.blanshard.sudoku.messages.InstallationRpcs.UpdateResult;
import us.blanshard.sudoku.messages.Rpc;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.users.User;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Iterator;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * @author Luke Blanshard
 */
public class SetInstallationMethod extends RpcMethod<UpdateParams, UpdateResult> {

  private static final TypeToken<UpdateParams> PARAMS_TYPE_TOKEN = new TypeToken<UpdateParams>() {};
  private static final Logger logger = Logger.getLogger(InstallationServlet.class.getName());
  private static final ImmutableSet<String> CLIENT_IDS = ImmutableSet.of(
      "826990774749.apps.googleusercontent.com",
      "826990774749-258fl53lo3h8t964408sftsog11em9ij.apps.googleusercontent.com");
  private final JsonFactory jsonFactory = new GsonFactory();
  private final GoogleIdTokenVerifier tokenVerifier = new GoogleIdTokenVerifier(new NetHttpTransport(), jsonFactory);
  private Iterator<Key> opaqueIds;


  public SetInstallationMethod() {
    super(PARAMS_TYPE_TOKEN);
  }

  @Override public UpdateResult call(UpdateParams params) throws MethodException {

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Transaction tx = ds.beginTransaction();
    boolean update = true;
    Long opaqueId;
    try {
      Key key = KeyFactory.createKey(KIND, params.id);
      Entity entity;
      try {
        entity = ds.get(key);
        opaqueId = (Long) entity.getProperty(OPAQUE_ID);
        if (!Objects.equal(params.manufacturer, entity.getProperty(MANUFACTURER))
            || !Objects.equal(params.model, entity.getProperty(MODEL))) {
          logger.warning(
              "Mfg and/or model changed: old: " + entity + ", new: " + RpcJson.GSON.toJson(params));
        }
      } catch (EntityNotFoundException e) {
        update = false;
        if (!isValidInstallationId(params.id))
          throw new MethodException(Rpc.invalidParams(params.id));
        entity = new Entity(key);
        opaqueId = nextOpaqueId(ds);
        entity.setUnindexedProperty(OPAQUE_ID, opaqueId);
      }

      if (params.account == null) {
        entity.removeProperty(ACCOUNT_ID);
        entity.removeProperty(NAME);
      } else {
        check(params.account.authToken, params.account.id);
        entity.setProperty(ACCOUNT_ID, new User(params.account.id, "gmail.com"));
        entity.setUnindexedProperty(NAME, params.account.installationName);
      }

      // If sharing is acceptable, include this installation in the opaque ID
      // index.
      if (params.shareData)
        entity.setProperty(INDEXED_ID, opaqueId);
      else
        entity.removeProperty(INDEXED_ID);

      entity.setUnindexedProperty(MANUFACTURER, params.manufacturer);
      entity.setUnindexedProperty(MODEL, params.model);
      entity.setUnindexedProperty(STREAM_COUNT, params.streamCount);
      entity.setUnindexedProperty(STREAM, params.stream);

      ds.put(tx, entity);
      tx.commit();
    } finally {
      if (tx.isActive()) tx.rollback();
    }

    logger.info((update ? "Updated" : "Inserted") + " installation " + opaqueId);

    UpdateResult answer = new UpdateResult();
    answer.streamCount = params.streamCount;
    answer.stream = params.stream;
    return answer;
  }

  private static boolean isValidInstallationId(String id) {
    try {
      UUID.fromString(id);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private synchronized long nextOpaqueId(DatastoreService ds) {
    if (opaqueIds == null || !opaqueIds.hasNext()) {
      opaqueIds = ds.allocateIds(Schema.Installation.KIND, 20).iterator();
    }
    return opaqueIds.next().getId();
  }

  private void check(String tokenString, String email) throws MethodException {
    GoogleIdToken token = null;
    boolean ok = false;
    try {
      token = GoogleIdToken.parse(jsonFactory, tokenString);
      if (tokenVerifier.verify(CLIENT_IDS, token)) {
        if (Objects.equal(email, token.getPayload().getEmail()))
          ok = true;
        else
          logger.info("Auth email mismatch, expected " + email);
      }
    } catch (GeneralSecurityException e) {
      logger.log(INFO, "Auth security problem", e);
    } catch (IOException e) {
      logger.log(INFO, "Auth I/O problem", e);
    }
    if (!ok) {
      logger.info("Auth verification failed for " + token);
      throw new MethodException(Rpc.error(Rpc.AUTH_VERIFICATION_FAILED, "Verification failed", null));
    }
  }
}
