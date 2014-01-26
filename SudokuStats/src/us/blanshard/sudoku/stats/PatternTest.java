package us.blanshard.sudoku.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.insight.Evaluator.MoveKind;
import us.blanshard.sudoku.stats.Pattern.Coll;
import us.blanshard.sudoku.stats.Pattern.PeerMetrics;
import us.blanshard.sudoku.stats.Pattern.UnitCategory;

import org.junit.Test;

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

  private void testPattern(String s1, Pattern p1) {
    Pattern p2 = Pattern.fromString(s1);
    String s2 = p1.toString();
    assertEquals(s1, s2);
    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());
    assertEquals(0, p1.compareTo(p2));
  }

  @Test public void patterns() {
    testPattern("c:b", Pattern.conflict(UnitCategory.BLOCK));
    testPattern("c:l", Pattern.conflict(UnitCategory.LINE));
    testPattern("bl:833510500:833000006:006855000", Pattern.barredLocation(peerMetrics(4, 1)));
    testPattern("bn:b", Pattern.barredNumeral(UnitCategory.BLOCK));
    testPattern("fl:l", Pattern.forcedLocation(UnitCategory.LINE));
    testPattern("fn:833510500:833000006:006855000", Pattern.forcedNumeral(peerMetrics(4, 1)));
    testPattern("o:b", Pattern.overlap(UnitCategory.BLOCK));
    testPattern("s:b:4:n", Pattern.lockedSet(UnitCategory.BLOCK, 4, true));
    testPattern("s:l:2:h", Pattern.lockedSet(UnitCategory.LINE, 2, false));
    testPattern("i:o:b+o:l=3:fl:b",
        Pattern.implication(Arrays.asList(Pattern.Overlap.LINE, Pattern.Overlap.BLOCK),
            Pattern.ForcedLoc.BLOCK, 3));
    testPattern("i:fl:l+o:b=5:i:fn:833510500:833000006:006855000+s:l:2:n=3:c:b",
        Pattern.implication(Arrays.asList(Pattern.Overlap.BLOCK, Pattern.ForcedLoc.LINE),
            Pattern.implication(Arrays.asList(Pattern.lockedSet(UnitCategory.LINE, 2, true),
                Pattern.forcedNumeral(peerMetrics(4, 1))), Pattern.Conflict.BLOCK, 3), 5));
  }

  @Test public void coll() throws Exception {
    Coll coll = Pattern.collFromString("DIRECT_HARD:7:123:");
    assertEquals(0, coll.patterns.size());
    assertEquals(MoveKind.DIRECT_HARD, coll.kind);
    assertEquals(7, coll.realmVector);
    assertEquals(123, coll.numScanTargets);
    StringBuilder sb = new StringBuilder();
    Pattern.appendTo(sb, coll);
    assertEquals("DIRECT_HARD:7:123:", sb.toString());

    String string = "DIRECT_EASY:2:5:c:b,s:b:4:n";
    coll = Pattern.collFromString(string);
    assertEquals(2, coll.patterns.size());
    assertEquals(MoveKind.DIRECT_EASY, coll.kind);
    assertEquals(2, coll.realmVector);
    assertEquals(5, coll.numScanTargets);
    assertEquals(Pattern.Conflict.BLOCK, coll.patterns.get(0));
    assertEquals(Pattern.lockedSet(UnitCategory.BLOCK, 4, true), coll.patterns.get(1));
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

    String string = "SIMPLY_IMPLIED_HARD:4:1:c:b;SIMPLY_IMPLIED_HARD:4:4:s:b:4:n;SIMPLY_IMPLIED_HARD:4:2:fl:b,fl:l";
    multi = Pattern.collsFromString(string);
    assertEquals(3, multi.size());
    assertEquals(Collections.singletonList(Pattern.Conflict.BLOCK), multi.get(0).patterns);
    assertEquals(Collections.singletonList(Pattern.lockedSet(UnitCategory.BLOCK, 4, true)),
        multi.get(1).patterns);
    assertEquals(Arrays.asList(Pattern.forcedLocation(UnitCategory.BLOCK),
        Pattern.forcedLocation(UnitCategory.LINE)), multi.get(2).patterns);
    Pattern.appendAllTo(sb, multi);
    assertEquals(string, sb.toString());
  }
}
