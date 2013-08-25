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

import static com.google.common.base.Preconditions.checkNotNull;
import static us.blanshard.sudoku.game.GameJson.HISTORY_TYPE;

import us.blanshard.sudoku.appengine.Schema;
import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Analyzer.StopException;
import us.blanshard.sudoku.insight.BarredLoc;
import us.blanshard.sudoku.insight.BarredNum;
import us.blanshard.sudoku.insight.Conflict;
import us.blanshard.sudoku.insight.ForcedLoc;
import us.blanshard.sudoku.insight.ForcedNum;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Implication;
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.LockedSet;
import us.blanshard.sudoku.insight.Overlap;
import us.blanshard.sudoku.stats.Pattern.UnitCategory;

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
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Analyzes sudoku game histories to measure the time taken to make use of
 * different insights' patterns.
 *
 * @author Luke Blanshard
 */
public class InsightMeasurer implements Runnable {
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
    PrintWriter out = new PrintWriter("measurer.txt");

    FileService fs = FileServiceFactory.getFileService();
    int npuzzles = 0;
    for (int shard = 0; shard < 8; ++shard) {
      AppEngineFile file = fs.getBlobFile(new BlobKey("shard" + shard));
      RecordReadChannel chan = fs.openRecordReadChannel(file, false);
      ByteBuffer record;
      while ((record = chan.readRecord()) != null) {
        ++npuzzles;
        EntityProto proto = new EntityProto();
        proto.mergeFrom(record);
        Entity entity = EntityTranslator.createFromPb(proto);
        String puzzleString = (String) entity.getProperty(Schema.InstallationPuzzle.PUZZLE);
        Grid puzzle = Grid.fromString(puzzleString);
        EmbeddedEntity attempt = (EmbeddedEntity) entity.getProperty(Schema.InstallationPuzzle.FIRST_ATTEMPT);
        Text historyString = (Text) attempt.getProperty(Schema.Attempt.MOVES);
        List<Move> history = GSON.fromJson(historyString.getValue(), HISTORY_TYPE);
        new InsightMeasurer(puzzle, history, out).run();
        System.out.print('.');
        if (npuzzles % 100 == 0) System.out.println();
        out.flush();
      }
    }

    out.close();
    helper.tearDown();
  }

  private final Grid solution;
  private final Sudoku game;
  private final List<Move> history;
  private final PrintWriter out;

  private InsightMeasurer(Grid puzzle, List<Move> history, PrintWriter out) {
    Solver.Result result = Solver.solve(puzzle, 10, new Random());
    checkNotNull(this.solution = result.intersection);
    this.game = new Sudoku(puzzle, Sudoku.nullRegistry()).resume();
    this.history = history;
    this.out = out;
  }

  @Override public void run() {
    long prevSetTime = 0;
    for (Move move : history) {
      prevSetTime = applyMove(move, prevSetTime);
    }
  }

  long applyMove(Move move, long prevSetTime) {
    if (move instanceof Move.Set) {
      Grid grid = game.getState(move.trailId).getGrid();
      if (grid.containsKey(move.getLocation()))
        grid = grid.toBuilder().remove(move.getLocation()).build();
      GridMarks gridMarks = new GridMarks(grid);
      Collector collector = new Collector(gridMarks, move.getAssignment());
      Analyzer.analyze(gridMarks, collector);
      long elapsed = move.timestamp - prevSetTime;
      try {
        Pattern.appendTo(out, collector.found)
            .append('\t')
            .append(String.valueOf(elapsed))
            .append('\t');
        Pattern.appendTo(out, collector.missed);
        out.println();
      } catch (IOException e) {
        throw new AssertionError(e);
      }
      prevSetTime = move.timestamp;
    }
    game.move(move);
    return prevSetTime;
  }

  class Collector implements Analyzer.Callback {
    final GridMarks gridMarks;
    final Assignment assignment;
    final Set<Pattern> found = Sets.newHashSet();
    final Multiset<Pattern> missed = HashMultiset.create();

    Collector(GridMarks gridMarks, Assignment assignment) {
      this.gridMarks = gridMarks;
      this.assignment = assignment;
    }

    @Override public void take(Insight insight) throws StopException {
      Assignment a = insight.getImpliedAssignment();
      boolean isError = insight.isError();
      if (isError || a != null) {
        Pattern pattern = getPattern(Analyzer.minimize(gridMarks, insight));
        if (isError || !assignment.equals(a)) {
          missed.add(pattern);
        } else {
          found.add(pattern);
        }
      }
    }

    private Pattern getPattern(Insight insight) {
      switch (insight.type) {
        case CONFLICT:
          return Pattern.conflict(((Conflict) insight).getLocations().unit);
        case BARRED_LOCATION:
          return Pattern.barredLocation(gridMarks.grid, ((BarredLoc) insight).getLocation());
        case BARRED_NUMERAL:
          return Pattern.barredNumeral(((BarredNum) insight).getUnit());
        case FORCED_LOCATION:
          return Pattern.forcedLocation(((ForcedLoc) insight).getUnit());
        case FORCED_NUMERAL:
          return Pattern.forcedNumeral(gridMarks.grid, ((ForcedNum) insight).getLocation());
        case OVERLAP:
          return Pattern.overlap(((Overlap) insight).getUnit());
        case LOCKED_SET: {
          LockedSet ls = (LockedSet) insight;
          UnitSubset locs = ls.getLocations();
          return Pattern.lockedSet(UnitCategory.forUnit(locs.unit), locs.size(), ls.isNakedSet());
        }
        case IMPLICATION: {
          Implication imp = (Implication) insight;
          List<Pattern> antecedents = Lists.newArrayList();
          for (Insight a : imp.getAntecedents())
            antecedents.add(getPattern(a));
          return Pattern.implication(antecedents, getPattern(imp.getConsequent()));
        }
        default:
          throw new IllegalArgumentException(insight.toShortString());
      }
    }
  }
}
