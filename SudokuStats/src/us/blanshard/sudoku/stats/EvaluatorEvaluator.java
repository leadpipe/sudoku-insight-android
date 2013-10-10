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
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.insight.Evaluator;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityTranslator;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.files.RecordReadChannel;
import com.google.appengine.tools.development.testing.LocalBlobstoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Evaluates the Evaluator class by running it over a series of puzzles
 * and accumulating error stats.
 */
public class EvaluatorEvaluator {
  public static final Gson GSON = GameJson.register(new GsonBuilder()).create();
  private static final LocalBlobstoreServiceTestConfig config = new LocalBlobstoreServiceTestConfig();
  static {
    config.setNoStorage(false);
    config.setBackingStoreLocation("/Users/leadpipe/Downloads/datastore-backup-20130720/");
  }
  private static final LocalServiceTestHelper helper = new LocalServiceTestHelper(config);

  @SuppressWarnings("deprecation")
  public static void main(String[] args) throws Exception {
    helper.setUp();
    PrintWriter out = new PrintWriter(System.out);

    FileService fs = FileServiceFactory.getFileService();
    int npuzzles = 0;
    int nwon = 0;
    int nsingle = 0;
    double totalAbsPercentError = 0;
    Stats undershotSingle = new Stats();
    Stats undershotMultiple = new Stats();
    Stats overshotSingle = new Stats();
    Stats overshotMultiple = new Stats();
    for (int shard = 0; shard < 8; ++shard) {
      AppEngineFile file = fs.getBlobFile(new BlobKey("shard" + shard));
      RecordReadChannel chan = fs.openRecordReadChannel(file, false);
      ByteBuffer record;
      while ((record = chan.readRecord()) != null) {
        ++npuzzles;
        out.print('.');
        out.flush();
        if (npuzzles % 100 == 0) out.println();
        EntityProto proto = new EntityProto();
        proto.mergeFrom(record);
        Entity entity = EntityTranslator.createFromPb(proto);
        String puzzleString = (String) entity.getProperty(Schema.InstallationPuzzle.PUZZLE);
        Grid puzzle = Grid.fromString(puzzleString);
        EmbeddedEntity attempt = (EmbeddedEntity) entity.getProperty(Schema.InstallationPuzzle.FIRST_ATTEMPT);
        boolean won = (Boolean) attempt.getProperty(Schema.Attempt.WON);
        if (!won) continue;

        ++nwon;
        double elapsedSeconds = ((Number) attempt.getProperty(Schema.Attempt.ELAPSED_MS)).doubleValue() / 1000.0;
        Stopwatch stopwatch = new Stopwatch().start();
        Evaluator.Result result = Evaluator.evaluate(puzzle, null);
        long micros = stopwatch.elapsed(TimeUnit.MICROSECONDS);
        if (result.singlePass) ++nsingle;
        AttemptInfo info = new AttemptInfo(elapsedSeconds, result, micros);
        totalAbsPercentError += info.getAbsPercentError();
        Stats stats = info.isOvershot()
            ? result.singlePass ? overshotSingle : overshotMultiple
            : result.singlePass ? undershotSingle : undershotMultiple;
        info.addTo(stats);
      }
    }

    out.println();
    out.printf("# puzzles: %d; # won: %d%n", npuzzles, nwon);
    out.printf("Proportion solved in one pass: %.2f%%%n", 100.0 * nsingle / nwon);
    out.printf("MAPE: %.2f%%%n", totalAbsPercentError / nwon);
    out.printf("%nSingle-pass undershot stats:%n%s", undershotSingle);
    out.printf("%nSingle-pass overshot stats:%n%s", overshotSingle);
    out.printf("%nMulti-pass undershot stats:%n%s", undershotMultiple);
    out.printf("%nMulti-pass overshot stats:%n%s", overshotMultiple);
    out.close();
    helper.tearDown();
  }

  static class Stats {
    final DescriptiveStatistics apeStats = new DescriptiveStatistics();
    final DescriptiveStatistics timeStats = new DescriptiveStatistics();

    @Override public String toString() {
      return "Ave Percent Errors = " + apeStats + "Evaluator Micros = " + timeStats;
    }
  }

  static class AttemptInfo {
    final double elapsedSeconds;
    final Evaluator.Result result;
    final long micros;
    final double ape;

    AttemptInfo(double elapsedSeconds, Evaluator.Result result, long micros) {
      this.elapsedSeconds = elapsedSeconds;
      this.result = result;
      this.micros = micros;
      this.ape = calcAbsPercentError(result.estimatedAverageSolutionSeconds, elapsedSeconds);
    }

    boolean isOvershot() {
      return result.estimatedAverageSolutionSeconds < elapsedSeconds;
    }

    double getAbsPercentError() {
      return ape;
    }

    private static double calcAbsPercentError(double a, double b) {
      return 100 * (a < b ? (b - a) / a : (a - b) / b);
    }

    void addTo(Stats stats) {
      stats.apeStats.addValue(getAbsPercentError());
      stats.timeStats.addValue(micros);
    }
  }
}
