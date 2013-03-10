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

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static us.blanshard.sudoku.appengine.Schema.Installation.ACCOUNT_ID;
import static us.blanshard.sudoku.appengine.Schema.Installation.ANDROID_APP_VERSION;
import static us.blanshard.sudoku.appengine.Schema.Installation.ANDROID_SDK;
import static us.blanshard.sudoku.appengine.Schema.Installation.INDEXED_ID;
import static us.blanshard.sudoku.appengine.Schema.Installation.KIND;
import static us.blanshard.sudoku.appengine.Schema.Installation.MANUFACTURER;
import static us.blanshard.sudoku.appengine.Schema.Installation.MODEL;
import static us.blanshard.sudoku.appengine.Schema.Installation.MONTH_NUMBER;
import static us.blanshard.sudoku.appengine.Schema.Installation.NAME;
import static us.blanshard.sudoku.appengine.Schema.Installation.OPAQUE_ID;
import static us.blanshard.sudoku.appengine.Schema.Installation.STREAM;
import static us.blanshard.sudoku.appengine.Schema.Installation.STREAM_COUNT;
import static us.blanshard.sudoku.messages.InstallationRpcs.monthNumber;

import us.blanshard.sudoku.gen.Generator;
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
import com.google.appengine.api.datastore.TransactionOptions;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saves information about an installation, and optionally an account (if the user
 * has asked to correlate installations using a Google account).
 */
public class InstallationUpdateMethod extends RpcMethod<UpdateParams, UpdateResult> {

  private static final TypeToken<UpdateParams> TOKEN = new TypeToken<UpdateParams>() {};
  private static final Logger logger = Logger.getLogger(InstallationUpdateMethod.class.getName());
  private static final ImmutableSet<String> CLIENT_IDS = ImmutableSet.of(
      "826990774749.apps.googleusercontent.com",
      "826990774749-258fl53lo3h8t964408sftsog11em9ij.apps.googleusercontent.com");
  private static final Pattern BASE_NAME_PATTERN = Pattern.compile("(.*)\\s+\\d+");
  private final JsonFactory jsonFactory = new GsonFactory();
  private final GoogleIdTokenVerifier tokenVerifier = new GoogleIdTokenVerifier(new NetHttpTransport(), jsonFactory);
  private Iterator<Key> opaqueIds;
  private Iterator<Key> opaqueAccountIds;


  public InstallationUpdateMethod() {
    super(TOKEN);
  }

  @Override public UpdateResult call(UpdateParams params) throws MethodException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    TransactionOptions options = TransactionOptions.Builder.withXG(true);
    Transaction tx = ds.beginTransaction(options);
    boolean update = true;
    Long opaqueId;
    try {
      Key key = KeyFactory.createKey(KIND, params.id);
      Entity entity;
      try {
        entity = ds.get(key);
        opaqueId = ((Number) entity.getProperty(OPAQUE_ID)).longValue();
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

      String oldAccountId = (String) entity.getProperty(ACCOUNT_ID);
      String newAccountId = null;

      if (params.account == null) {
        entity.removeProperty(ACCOUNT_ID);
        entity.removeProperty(NAME);
      } else {
        checkAuth(params.account.authToken, params.account.id);
        newAccountId = params.account.id;
        entity.setProperty(ACCOUNT_ID, newAccountId);
        entity.setUnindexedProperty(NAME, params.account.installationName);
      }

      // If sharing is acceptable, include this installation in the opaque ID
      // index.
      if (params.shareData)
        entity.setProperty(INDEXED_ID, opaqueId);
      else
        entity.removeProperty(INDEXED_ID);

      if (params.androidSdk > 0)
        entity.setUnindexedProperty(ANDROID_SDK, params.androidSdk);
      if (params.androidAppVersion > 0)
        entity.setUnindexedProperty(ANDROID_APP_VERSION, params.androidAppVersion);

      entity.setUnindexedProperty(MANUFACTURER, params.manufacturer);
      entity.setUnindexedProperty(MODEL, params.model);
      entity.setUnindexedProperty(STREAM_COUNT, params.streamCount);
      entity.setUnindexedProperty(STREAM, params.stream);
      entity.setUnindexedProperty(MONTH_NUMBER, params.monthNumber);

      if (!Objects.equal(oldAccountId, newAccountId)) {
        removeInstallationFromAccount(oldAccountId, ds, tx, params, opaqueId);
        addInstallationToAccount(newAccountId, ds, tx, params);
      }

      ds.put(tx, entity);
      Transactions.commit(tx);
    } finally {
      if (tx.isActive()) tx.rollback();
    }

    logger.info((update ? "Updated" : "Inserted") + " installation " + opaqueId);

    UpdateResult result = new UpdateResult();
    result.streamCount = getConfigStreamCount(ds);
    result.stream = params.stream;
    if (params.account != null) {
      result.installationName = params.account.installationName;
      resolveClashes(ds, params, result, opaqueId);
    }
    return result;
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
      opaqueIds = ds.allocateIds(KIND, 20).iterator();
    }
    return opaqueIds.next().getId();
  }

  private synchronized long nextOpaqueAccountId(DatastoreService ds) {
    if (opaqueAccountIds == null || !opaqueAccountIds.hasNext()) {
      opaqueAccountIds = ds.allocateIds(Schema.Account.KIND, 10).iterator();
    }
    return opaqueAccountIds.next().getId();
  }

  private void checkAuth(String tokenString, String email) throws MethodException {
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

  private void removeInstallationFromAccount(String accountId, DatastoreService ds,
      Transaction tx, UpdateParams params, Long opaqueId) {
    if (accountId == null)
      return;

    Key accountKey = KeyFactory.createKey(Schema.Account.KIND, accountId);
    try {
      Entity account = ds.get(accountKey);
      Set<String> ids = getInstallationIds(account);
      ids.remove(params.id);
      account.setUnindexedProperty(Schema.Account.INSTALLATION_IDS, ids);
      ds.put(tx, account);
    } catch (EntityNotFoundException e) {
      logger.log(WARNING, "Old account not found: " + accountId
          + ", for " + opaqueId);
    }
  }

  private void addInstallationToAccount(String accountId, DatastoreService ds,
      Transaction tx, UpdateParams params) {
    if (accountId == null)
      return;

    Key accountKey = KeyFactory.createKey(Schema.Account.KIND, accountId);
    Entity account;
    Set<String> ids;
    try {
      account = ds.get(accountKey);
      ids = getInstallationIds(account);
    } catch (EntityNotFoundException e) {
      account = new Entity(accountKey);
      account.setProperty(OPAQUE_ID, nextOpaqueAccountId(ds));
      ids = Sets.newLinkedHashSet();
    }
    ids.add(params.id);
    account.setUnindexedProperty(Schema.Account.INSTALLATION_IDS, ids);
    ds.put(tx, account);
  }

  @SuppressWarnings("unchecked")
  private Set<String> getInstallationIds(Entity account) {
    return Sets.newLinkedHashSet(
        (Collection<String>) account.getProperty(Schema.Account.INSTALLATION_IDS));
  }

  /**
   * Checks all the installations for the account mentioned in the update
   * parameters, and looks for clashing streams and installation names. If it
   * finds any, it alters the given result such that if the installation makes
   * the recommended changes its clashes with other installations will go away.
   */
  private void resolveClashes(
      DatastoreService ds, UpdateParams params, UpdateResult result, Long opaqueId) {
    Key accountKey = KeyFactory.createKey(Schema.Account.KIND, params.account.id);
    try {
      Entity account = ds.get(accountKey);
      Set<String> otherNames = Sets.newHashSet();
      Set<Integer> otherStreams = Sets.newHashSet();
      Calendar cal = new GregorianCalendar();
      cal.add(Calendar.MONTH, -1);
      int activeMonthNumber = monthNumber(cal);

      for (String id : getInstallationIds(account)) {
        if (id.equals(params.id)) continue;
        Entity other = ds.get(KeyFactory.createKey(KIND, id));
        String name = (String) other.getProperty(NAME);
        if (name != null) otherNames.add(name);
        if (((Number) other.getProperty(MONTH_NUMBER)).intValue() >= activeMonthNumber) {
          otherStreams.add(((Number) other.getProperty(STREAM)).intValue());
        }
      }

      if (otherNames.contains(result.installationName)) {
        // Tweak the installation name by appending 2, 3, etc. Note it's
        // possible for 2 installations to ping-pong names with this approach,
        // eg both going to "asdf", then "asdf 2", then "asdf". I don't expect
        // that to ever happen in practice, and I'd be hugely surprised if it
        // didn't settle out in a single iteration.
        String base = result.installationName;
        Matcher m = BASE_NAME_PATTERN.matcher(base);
        if (m.matches()) base = m.group(1);
        for (int counter = 2; true; ++counter) {
          String name = base + " " + counter;
          if (!otherNames.contains(name)) {
            result.installationName = name;
            break;
          }
        }
        logger.info("Updated installation name of " + opaqueId + " to " + result.installationName);
      }

      if (otherStreams.contains(result.stream)) {
        List<Integer> available = Lists.newArrayList();
        for (int s = 1; s <= result.streamCount; ++s)
          if (!otherStreams.contains(s))
            available.add(s);
        if (available.isEmpty()) {
          for (int s = result.streamCount + 1; true; ++s)
            if (!otherStreams.contains(s)) {
              result.stream = s;
              break;
            }
        } else {
          result.stream = available.get(new Random().nextInt(available.size()));
        }
        logger.info("Updated stream of " + opaqueId + " to " + result.stream);
      }

    } catch (EntityNotFoundException e) {
      logger.log(WARNING, "Account or linked installation not found after update: "
          + params.account.id + ", for " + opaqueId, e);
    }
  }

  private int getConfigStreamCount(DatastoreService ds) throws MethodException {
    Key configKey = KeyFactory.createKey(Schema.Config.KIND, Schema.Config.ID);
    Entity config;
    try {
      config = ds.get(configKey);
    } catch (EntityNotFoundException e) {
      config = new Entity(configKey);
      config.setUnindexedProperty(Schema.Config.STREAM_COUNT, Generator.NUM_STREAMS);
      Transaction tx = ds.beginTransaction();
      try {
        ds.put(tx, config);
        Transactions.commit(tx);
      } finally {
        if (tx.isActive()) tx.rollback();
      }
    }
    return ((Number) config.getProperty(Schema.Config.STREAM_COUNT)).intValue();
  }
}
