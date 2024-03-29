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
import us.blanshard.sudoku.insight.Insight;
import us.blanshard.sudoku.insight.LockedSet;
import us.blanshard.sudoku.insight.Marks;
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
  private static final Marks marks = Marks.fromGrid(grid);


  private void testPeerMetrics(int row, int col, String expected) {
    PeerMetrics pm = peerMetrics(row, col);
    String s = pm.toString();
    assertEquals(expected, s);
    assertEquals(pm, PeerMetrics.fromString(s));
  }

  private PeerMetrics peerMetrics(int row, int col) {
    return Pattern.peerMetrics(marks, Location.of(row, col));
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
    Analyzer.findSets(Marks.fromGrid(grid), finder);
    assertEquals(6, finder.sets.size());
    LockedSet set1 = finder.sets.get(0);
    LockedSet set2 = finder.sets.get(1);
    LockedSet set3 = finder.sets.get(2);
    LockedSet set4 = finder.sets.get(3);
    LockedSet set5 = finder.sets.get(4);
    LockedSet set6 = finder.sets.get(5);
    assertEquals(UnitSubset.ofBits(Block.of(6), 0402), set1.getLocations());
    assertEquals(UnitSubset.ofBits(Block.of(6), 0011), set2.getLocations());
    assertEquals(UnitSubset.ofBits(Block.of(3), 0030), set3.getLocations());
    assertEquals(UnitSubset.ofBits(Column.of(7), 0030), set4.getLocations());
    assertEquals(UnitSubset.ofBits(Row.of(2), 0407), set5.getLocations());
    assertEquals(UnitSubset.ofBits(Column.of(7), 0603), set6.getLocations());

    assertEquals("s:!21:b:2:h:o", Pattern.lockedSet(true, set1, marks).toString());
    assertEquals("s:-22:l:2:h:d", Pattern.lockedSet(false, set3, marks).toString());
    assertEquals("s:-32:l:4:n:d", Pattern.lockedSet(false, set5, marks).toString());
    assertEquals("s:!30:l:4:h:o", Pattern.lockedSet(true, set6, marks).toString());
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
    assertEquals("s:!25:b:3:h:d", Pattern.lockedSet(true, set, Marks.fromGrid(grid)).toString());

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
    assertEquals("s:-25:b:3:h:o", Pattern.lockedSet(false, set, Marks.fromGrid(grid)).toString());
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
    testPattern("c:!0:b", Pattern.conflict(true, UnitCategory.BLOCK));
    testPattern("c:-1:l", Pattern.conflict(false, UnitCategory.LINE));
    testPattern("bl:!-1:833510500:833000006:006855000", Pattern.barredLocation(null, peerMetrics(4, 1)));
    testPattern("bn:!8:b", Pattern.barredNumeral(true, UnitCategory.BLOCK));
    testPattern("fl:-11:l", Pattern.forcedLocation(false, UnitCategory.LINE));
    testPattern("fn:--1:833510500:833000006:006855000", Pattern.forcedNumeral(false, null, peerMetrics(4, 1)));
    testPattern("o:!19:b", Pattern.overlap(true, UnitCategory.BLOCK));
    testPattern("s:!-1:b:4:n:d", new Pattern.LockedSet(true, null, UnitCategory.BLOCK, 4, true, false));
    testPattern("s:--1:l:2:h:o", new Pattern.LockedSet(false, null, UnitCategory.LINE, 2, false, true));
    testPattern("i:!-1:o:!19:b+o:-20:l=fl:!10:b",
        Pattern.implication(Arrays.asList(Pattern.Overlap.LINE_DIFF, Pattern.Overlap.BLOCK_SAME),
            Pattern.ForcedLoc.BLOCK_SAME));
    testPattern("i:!-1:fl:-11:l+o:!19:b=i:!-1:fn:--1:833510500:833000006:006855000+s:!-1:l:2:n:o=c:!0:b",
        Pattern.implication(Arrays.asList(Pattern.Overlap.BLOCK_SAME, Pattern.ForcedLoc.LINE_DIFF),
            Pattern.implication(Arrays.asList(new Pattern.LockedSet(true, null, UnitCategory.LINE, 2, true, true),
                Pattern.forcedNumeral(false, null, peerMetrics(4, 1))), Pattern.Conflict.BLOCK_SAME)));
  }

  @Test public void coll() throws Exception {
    Coll coll = Pattern.collFromString("");
    assertEquals(0, coll.patterns.size());
    StringBuilder sb = new StringBuilder();
    Pattern.appendTo(sb, coll);
    assertEquals("", sb.toString());

    String string = "c:-0:b,s:-0:b:4:n:d";
    coll = Pattern.collFromString(string);
    assertEquals(2, coll.patterns.size());
    assertEquals(Pattern.Conflict.BLOCK_DIFF, coll.patterns.get(0));
    assertEquals(new Pattern.LockedSet(false, null, UnitCategory.BLOCK, 4, true, false), coll.patterns.get(1));
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

    String string = "c:!0:b;s:!0:b:4:n:o;fl:-10:b,fl:!11:l";
    multi = Pattern.collsFromString(string);
    assertEquals(3, multi.size());
    assertEquals(Collections.singletonList(Pattern.Conflict.BLOCK_SAME), multi.get(0).patterns);
    assertEquals(Collections.singletonList(new Pattern.LockedSet(true, null, UnitCategory.BLOCK, 4, true, true)),
        multi.get(1).patterns);
    assertEquals(Arrays.asList(Pattern.forcedLocation(false, UnitCategory.BLOCK),
        Pattern.forcedLocation(true, UnitCategory.LINE)), multi.get(2).patterns);
    Pattern.appendAllTo(sb, multi);
    assertEquals(string, sb.toString());
  }
}
