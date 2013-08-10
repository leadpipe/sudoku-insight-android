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

import us.blanshard.sudoku.appengine.Schema;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityTranslator;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.files.RecordReadChannel;
import com.google.appengine.labs.repackaged.com.google.common.collect.Maps;
import com.google.appengine.tools.development.testing.LocalBlobstoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Map;

/**
 * Reads backup files from app engine's InstallationPuzzle table and counts
 * attempts per installation.
 */
public class AttemptCounter {
  private final LocalBlobstoreServiceTestConfig config = new LocalBlobstoreServiceTestConfig();
  {
    config.setNoStorage(false);
    config.setBackingStoreLocation("/Users/leadpipe/Downloads/datastore-backup-20130720/");
  }
  private final LocalServiceTestHelper helper = new LocalServiceTestHelper(config);

  @Before public void before() {
    helper.setUp();
  }

  @After public void after() {
    helper.tearDown();
  }

  static class Count {
    public int attempted;
    public int won;
    public Date latest;

    @Override
    public String toString() {
      return "<attempted: " + attempted + ", won: " + won + ", latest: " + latest + ">";
    }
  }

  @SuppressWarnings("deprecation")
  @Test public void count() throws Exception {
    Map<String, Count> counts = Maps.newHashMap();
    FileService fs = FileServiceFactory.getFileService();
    for (int shard = 0; shard < 8; ++shard) {
      AppEngineFile file = fs.getBlobFile(new BlobKey("shard" + shard));
      RecordReadChannel chan = fs.openRecordReadChannel(file, false);
      ByteBuffer record;
      while ((record = chan.readRecord()) != null) {
        EntityProto proto = new EntityProto();
        proto.mergeFrom(record);
        Entity entity = EntityTranslator.createFromPb(proto);
        String installationId = entity.getKey().getParent().getName();
        Count count = counts.get(installationId);
        if (count == null) counts.put(installationId, count = new Count());
        ++count.attempted;
        EmbeddedEntity attempt = (EmbeddedEntity) entity.getProperty(Schema.InstallationPuzzle.FIRST_ATTEMPT);
        Date stopTime = (Date) attempt.getProperty(Schema.Attempt.STOP_TIME);
        if (count.latest == null || stopTime.after(count.latest))
          count.latest = stopTime;
        if ((Boolean) attempt.getProperty(Schema.Attempt.WON))
          ++count.won;
      }
    }
    for (Map.Entry<String, Count> entry : counts.entrySet()) {
      System.out.println(entry);
    }
  }
}
