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
import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.LocSet;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumSet;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.game.UndoDetector;
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
import us.blanshard.sudoku.insight.Insight.Type;
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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

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
  private final UndoDetector undoDetector;
  private final Multimap<Integer, Location> mistakes = HashMultimap.create();
  private final Set<Integer> trails = Sets.newHashSet();
  private final PrintWriter out;

  private InsightMeasurer(Grid puzzle, List<Move> history, PrintWriter out) {
    Solver.Result result = Solver.solve(puzzle, 10, new Random());
    this.solution = checkNotNull(result.intersection);
    this.game = new Sudoku(puzzle).resume();
    this.history = history;
    this.undoDetector = new UndoDetector(game);
    this.out = out;
  }

  @Override public void run() {
    long prevTime = 0;
    Numeral prevNumeral = null;
    for (Move move : history) {
      applyMove(move, prevTime, prevNumeral);
      prevTime = move.timestamp;
      prevNumeral = move.getNumeral();
    }
  }

  private void applyMove(Move move, long prevTime, @Nullable Numeral prevNumeral) {
    noteMistakes(move);
    if (move instanceof Move.Set
        && !undoDetector.isUndoOrRedo(move)
        && !isApparentCorrection(move)) {
      Grid grid = game.getState(move.trailId).getGrid();
      if (grid.containsKey(move.getLocation()))
        grid = grid.toBuilder().remove(move.getLocation()).build();
      GridMarks gridMarks = new GridMarks(grid);
      Collector collector = new Collector(gridMarks, move.getAssignment(), prevNumeral);
      Analyzer.analyze(gridMarks, collector, true);
      long elapsed = move.timestamp - prevTime;
      boolean isTrailhead = move.trailId >= 0 && game.getTrail(move.trailId).getSetCount() == 0;
      try {
        Pattern.appendTo(out, collector.found)
            .append('\t')
            .append(String.valueOf(elapsed))
            .append('\t')
            .append(String.valueOf(Location.COUNT - grid.size()))
            .append('\t')
            .append(String.valueOf(collector.getNumTargets()))
            .append('\t')
            .append(String.valueOf(collector.isBlockNumeralMove()))
            .append('\t')
            .append(String.valueOf(collector.getNumBlockNumeralMoves()))
            .append('\t')
            .append(String.valueOf(collector.getNumOpenBlockNumerals()))
            .append('\t')
            .append(String.valueOf(isTrailhead))
            .append('\t')
            .append(String.valueOf(trails.size()))
            .append('\t')
            ;
        Pattern.appendAllTo(out, collector.missed.values());
        out.println();
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
    game.move(move);
    if (move.trailId >= 0) trails.add(move.trailId);
  }

  private void noteMistakes(Move move) {
    if (solution.containsKey(move.getLocation())
        && solution.get(move.getLocation()) != move.getNumeral()) {
      mistakes.put(move.trailId, move.getLocation());
    }
  }

  private boolean isApparentCorrection(Move move) {
    return mistakes.containsEntry(move.trailId, move.getLocation());
  }

  private class Collector implements Analyzer.Callback {
    final GridMarks gridMarks;
    final Assignment assignment;
    final Numeral prevNumeral;
    final List<Pattern> found = Lists.newArrayList();
    final Map<Object, List<Pattern>> missed = Maps.newHashMap();
    final LocSet locTargets = new LocSet();
    final UnitNumSet unitNumTargets = new UnitNumSet();
    int numBlockNumeralMoves;
    boolean isBlockNumeralMove;

    Collector(GridMarks gridMarks, Assignment assignment, @Nullable Numeral prevNumeral) {
      this.gridMarks = gridMarks;
      this.assignment = assignment;
      this.prevNumeral = prevNumeral;
    }

    @Override public void take(Insight insight) throws StopException {
      Assignment a = insight.getImpliedAssignment();
      boolean isError = insight.isError();
      if (isError || a != null) {
        Insight minimized = Analyzer.minimize(gridMarks, insight);
        Pattern pattern = getPattern(minimized);
        if (isError)
          missed.put(new Object(), Collections.singletonList(pattern));
        else if (assignment.equals(a))
          found.add(pattern);
        else {
          List<Pattern> list = missed.get(a);
          if (list == null) missed.put(a, list = Lists.newArrayList());
          list.add(pattern);
        }
        insight.addScanTargets(locTargets, unitNumTargets);
      }
      if (a != null && prevNumeral != null && insight.type == Type.FORCED_LOCATION) {
        ForcedLoc fl = (ForcedLoc) insight;
        if (fl.getUnit().getType() == Unit.Type.BLOCK && fl.getNumeral() == prevNumeral) {
          ++numBlockNumeralMoves;
          if (assignment.equals(a)) isBlockNumeralMove = true;
        }
      }
    }

    int getNumTargets() {
      return locTargets.size() + unitNumTargets.size();
    }

    int getNumBlockNumeralMoves() {
      return numBlockNumeralMoves;
    }

    boolean isBlockNumeralMove() {
      return isBlockNumeralMove;
    }

    int getNumOpenBlockNumerals() {
      if (prevNumeral == null) return 0;
      int count = 0;
      for (Block block : Block.all()) {
        UnitSubset locs = gridMarks.marks.get(UnitNumeral.of(block, prevNumeral));
        if (locs.size() > 1 || locs.size() == 1 && !gridMarks.grid.containsKey(locs.get(0)))
          ++count;
      }
      return count;
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
          return Pattern.implication(antecedents, getPattern(imp.getConsequent()), imp.getScanTargetCount());
        }
        default:
          throw new IllegalArgumentException(insight.toShortString());
      }
    }
  }
}
