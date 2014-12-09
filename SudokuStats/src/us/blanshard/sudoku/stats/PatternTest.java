package us.blanshard.sudoku.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.UnitSubset;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.GridMarks;
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.LockedSet;
import us.blanshard.sudoku.stats.Pattern.Coll;
import us.blanshard.sudoku.stats.Pattern.PeerMetrics;
import us.blanshard.sudoku.stats.Pattern.UnitCategory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PatternTest {
  private static final Grid grid = Grid.fromString(
      " . 1 . | 2 . . | . . ." +
      " . . . | 6 8 4 | . . ." +
      " 7 . . | . 9 . | 8 . ." +
      "-------+-------+------" +
      " . 5 4 | . . . | . . 7" +
      " 3 8 . | . . . | . 9 1" +
      " 6 . . | . . . | 4 5 ." +
      "-------+-------+------" +
      " . . 5 | . . . | 2 . 6" +
      " . . . | 4 6 3 | . . ." +
      " . . . | . . . | . 4 .");


  private void testPeerMetrics(int row, int col, String expected) {
    PeerMetrics pm = peerMetrics(row, col);
    String s = pm.toString();
    assertEquals(expected, s);
    assertEquals(pm, PeerMetrics.fromString(s));
  }

  private PeerMetrics peerMetrics(int row, int col) {
    return Pattern.peerMetrics(grid, Location.of(row, col));
  }

  @Test public void peerMetrics() {
    testPeerMetrics(1, 1, "830000500:830200000:805044000");
    testPeerMetrics(5, 5, "000080000:260080062:066080040");
    testPeerMetrics(6, 3, "077110308:308000770:000708700");
    testPeerMetrics(4, 1, "833510500:833000006:006855000");
    testPeerMetrics(5, 7, "001833510:260000833:006085400");
    testPeerMetrics(6, 7, "001011830:200000830:004008400");
    testPeerMetrics(4, 5, "080000000:022080002:044800040");

    assertTrue(peerMetrics(4, 5).compareTo(peerMetrics(5, 5)) > 0);
    assertTrue(peerMetrics(1, 1).compareTo(peerMetrics(1, 1)) == 0);
    assertTrue(peerMetrics(1, 1).compareTo(peerMetrics(5, 5)) > 0);
  }

  static class SetFinder implements Analyzer.Callback {
    List<LockedSet> sets = new ArrayList<LockedSet>();
    @Override public void take(Insight insight) {
      if (insight instanceof LockedSet) {
        sets.add((LockedSet) insight);
      }
    }
  }

  @Test public void lockedSets() {
    SetFinder finder = new SetFinder();
    Analyzer.findSets(new GridMarks(grid), finder);
    LockedSet set1 = finder.sets.get(0);
    LockedSet set2 = finder.sets.get(1);
    LockedSet set3 = finder.sets.get(2);
    LockedSet set4 = finder.sets.get(3);
    assertEquals(UnitSubset.ofBits(Block.of(6), 0402), set1.getLocations());
    assertEquals(UnitSubset.ofBits(Block.of(3), 0030), set2.getLocations());
    assertEquals(UnitSubset.ofBits(Column.of(7), 0603), set3.getLocations());
    assertEquals(UnitSubset.ofBits(Row.of(2), 0407), set4.getLocations());

    assertEquals("s:21:b:2:h:o", Pattern.lockedSet(set1, grid).toString());
    assertEquals("s:24:l:2:h:d", Pattern.lockedSet(set2, grid).toString());
    assertEquals("s:30:l:4:h:o", Pattern.lockedSet(set3, grid).toString());
    assertEquals("ns:38:l:4:1", Pattern.lockedSet(set4, grid).toString());
  }

  @Test public void moreLockedSets() {
    Grid grid = Grid.fromString(
        " 4 5 . | . . 3 | . . ." +
        " . 6 . | . . . | 3 . ." +
        " . . 7 | . . . | . . ." +
        "-------+-------+------" +
        " . . 1 | . . . | . . ." +
        " . . 2 | . . . | . . ." +
        " . . . | . . . | . . ." +
        "-------+-------+------" +
        " . . . | . . . | . . ." +
        " . . . | . . . | . . ." +
        " . . . | . . . | . . .");
    LockedSet set = new LockedSet(
        NumSet.ofBits(7), UnitSubset.ofBits(Block.of(1), 0310), false);
    assertEquals("s:27:b:3:h:d", Pattern.lockedSet(set, grid).toString());

    grid = Grid.fromString(
        " 4 5 . | . . 3 | . . ." +
        " . 6 . | . . . | 3 . ." +
        " . . 7 | . . . | . . ." +
        "-------+-------+------" +
        " . . 1 | . . . | . . ." +
        " . . 2 | . . . | . . ." +
        " . . 3 | . . . | . . ." +
        "-------+-------+------" +
        " . . . | . . . | . . ." +
        " . . . | . . . | . . ." +
        " . . . | . . . | . . .");
    assertEquals("s:25:b:3:h:o", Pattern.lockedSet(set, grid).toString());
  }

  private void testPattern(String s1, Pattern p1) {
    Pattern p2 = Pattern.fromString(s1);
    String s2 = p1.toString();
    assertEquals(s1, s2);
    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());
    assertEquals(0, p1.compareTo(p2));
  }

  @Test public void patterns() {
    testPattern("c:0:b", Pattern.conflict(UnitCategory.BLOCK));
    testPattern("c:1:l", Pattern.conflict(UnitCategory.LINE));
    testPattern("bl:-1:833510500:833000006:006855000", Pattern.barredLocation(null, peerMetrics(4, 1)));
    testPattern("bn:8:b", Pattern.barredNumeral(UnitCategory.BLOCK));
    testPattern("fl:11:l", Pattern.forcedLocation(UnitCategory.LINE));
    testPattern("fn:-1:833510500:833000006:006855000", Pattern.forcedNumeral(null, peerMetrics(4, 1)));
    testPattern("o:19:b", Pattern.overlap(UnitCategory.BLOCK));
    testPattern("s:-1:b:4:n:d", new Pattern.LockedSet(null, UnitCategory.BLOCK, 4, true, false));
    testPattern("s:-1:l:2:h:o", new Pattern.LockedSet(null, UnitCategory.LINE, 2, false, true));
    testPattern("i:-1:o:19:b+o:20:l=3:fl:10:b",
        Pattern.implication(Arrays.asList(Pattern.Overlap.LINE, Pattern.Overlap.BLOCK),
            Pattern.ForcedLoc.BLOCK, 3));
    testPattern("i:-1:fl:11:l+o:19:b=5:i:-1:fn:-1:833510500:833000006:006855000+s:-1:l:2:n:o=3:c:0:b",
        Pattern.implication(Arrays.asList(Pattern.Overlap.BLOCK, Pattern.ForcedLoc.LINE),
            Pattern.implication(Arrays.asList(new Pattern.LockedSet(null, UnitCategory.LINE, 2, true, true),
                Pattern.forcedNumeral(null, peerMetrics(4, 1))), Pattern.Conflict.BLOCK, 3), 5));
  }

  @Test public void coll() throws Exception {
    Coll coll = Pattern.collFromString("7:123:");
    assertEquals(0, coll.patterns.size());
    assertEquals(7, coll.realmVector);
    assertEquals(123, coll.numScanTargets);
    StringBuilder sb = new StringBuilder();
    Pattern.appendTo(sb, coll);
    assertEquals("7:123:", sb.toString());

    String string = "2:5:c:0:b,s:0:b:4:n:d";
    coll = Pattern.collFromString(string);
    assertEquals(2, coll.patterns.size());
    assertEquals(2, coll.realmVector);
    assertEquals(5, coll.numScanTargets);
    assertEquals(Pattern.Conflict.BLOCK, coll.patterns.get(0));
    assertEquals(new Pattern.LockedSet(null, UnitCategory.BLOCK, 4, true, false), coll.patterns.get(1));
    sb.setLength(0);
    Pattern.appendTo(sb, coll);
    assertEquals(string, sb.toString());
  }

  @Test public void colls() throws Exception {
    List<Coll> multi = Pattern.collsFromString("");
    assertEquals(0, multi.size());
    StringBuilder sb = new StringBuilder();
    Pattern.appendAllTo(sb, multi);
    assertEquals(0, sb.length());

    String string = "4:1:c:0:b;4:4:s:0:b:4:n:o;4:2:fl:10:b,fl:11:l";
    multi = Pattern.collsFromString(string);
    assertEquals(3, multi.size());
    assertEquals(Collections.singletonList(Pattern.Conflict.BLOCK), multi.get(0).patterns);
    assertEquals(Collections.singletonList(new Pattern.LockedSet(null, UnitCategory.BLOCK, 4, true, true)),
        multi.get(1).patterns);
    assertEquals(Arrays.asList(Pattern.forcedLocation(UnitCategory.BLOCK),
        Pattern.forcedLocation(UnitCategory.LINE)), multi.get(2).patterns);
    Pattern.appendAllTo(sb, multi);
    assertEquals(string, sb.toString());
  }
}
