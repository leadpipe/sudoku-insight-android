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
import us.blanshard.sudoku.insight.Rating;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityTranslator;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.files.RecordReadChannel;
import com.google.appengine.tools.development.testing.LocalBlobstoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Reads Sudoku attempt history from one of various sources and returns
 * collections of AttemptInfo objects.
 */
@SuppressWarnings("deprecation")
public class Attempts {

  /**
   * Returns all the attempts from a datastore backup directory.
   */
  public static Iterable<AttemptInfo> datastoreBackup() {
    List<AttemptInfo> answer = Lists.newArrayList();

    LocalBlobstoreServiceTestConfig config = new LocalBlobstoreServiceTestConfig();
    config.setNoStorage(false);
    config.setBackingStoreLocation("/Users/leadpipe/Downloads/datastore-backup-20130720/");
    LocalServiceTestHelper helper = new LocalServiceTestHelper(config);
    helper.setUp();
    try {
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
          String cluesString = (String) entity.getProperty(Schema.InstallationPuzzle.PUZZLE);
          EmbeddedEntity attempt = (EmbeddedEntity) entity.getProperty(Schema.InstallationPuzzle.FIRST_ATTEMPT);
          Text historyText = (Text) attempt.getProperty(Schema.Attempt.MOVES);
          Date stopTime = (Date) attempt.getProperty(Schema.Attempt.STOP_TIME);

          answer.add(new AttemptInfo(installationId, cluesString, historyText.getValue(), stopTime));
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Problem reading datastore backup", e);
    } finally {
      helper.tearDown();
    }

    return answer;
  }

  /**
   * Returns all the attempts from the 2013 phone.
   */
  public static Iterable<AttemptInfo> phone2013() {
    return fromTsv("/Users/leadpipe/insight/data/games-phone-2013-12-01.tsv");
  }

  /**
   * Returns all the attempts from the tablet as of 2014-02-09.
   */
  public static Iterable<AttemptInfo> tablet2014() {
    return fromTsv("/Users/leadpipe/insight/data/games-tablet-2014-02-09.tsv");
  }

  private static Iterable<AttemptInfo> fromTsv(String fname) {
    List<AttemptInfo> answer = Lists.newArrayList();
    BufferedReader in = null;

    try {
      in = new BufferedReader(new FileReader(fname));
      Splitter splitter = Splitter.on('\t');
      for (String line; (line = in.readLine()) != null; ) {
        Iterator<String> iter = splitter.split(line).iterator();
        String cluesString = iter.next();
        String historyString = iter.next();
        String ratingString = iter.next();

        Rating rating = null;
        if (!ratingString.isEmpty())
          rating = Rating.deserialize(ratingString);

        answer.add(new AttemptInfo(cluesString, historyString, rating));
      }
    } catch (IOException e) {
      throw new RuntimeException("Problem reading TSV data", e);
    } finally {
      if (in != null)
        try { in.close(); }
        catch (IOException e) {}
    }

    return answer;
  }
}
