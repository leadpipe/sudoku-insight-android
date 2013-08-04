package us.blanshard.sudoku.stats;

import static org.junit.Assert.assertEquals;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.stats.Pattern.PeerMetrics;
import us.blanshard.sudoku.stats.Pattern.UnitCategory;

import org.junit.Test;

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
    testPeerMetrics(1, 1, "10100:54-----:--3----");
    testPeerMetrics(5, 5, "00000:43cd-----:-32-c----");
    testPeerMetrics(6, 3, "01220:----:----");
    testPeerMetrics(4, 1, "21200:8---:1---");
    testPeerMetrics(5, 7, "21110:65--:-2b-");
    testPeerMetrics(6, 7, "11011:3a--:--6-");
    testPeerMetrics(4, 5, "00000:4bc------:---4ab---");
  }

  private void testPattern(String s1, Pattern p1) {
    Pattern p2 = Pattern.fromString(s1);
    String s2 = p1.toString();
    assertEquals(s1, s2);
    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());
  }

  @Test public void patterns() {
    testPattern("c:b", Pattern.conflict(UnitCategory.BLOCK));
    testPattern("c:l", Pattern.conflict(UnitCategory.LINE));
    testPattern("bl:21200:8---:1---", Pattern.barredLocation(peerMetrics(4, 1)));
    testPattern("bn:b", Pattern.barredNumeral(UnitCategory.BLOCK));
    testPattern("fl:l", Pattern.forcedLocation(UnitCategory.LINE));
    testPattern("fn:21200:8---:1---", Pattern.forcedNumeral(peerMetrics(4, 1)));
    testPattern("o:b", Pattern.overlap(UnitCategory.BLOCK));
    testPattern("s:b:4:n", Pattern.lockedSet(UnitCategory.BLOCK, 4, true));
    testPattern("s:l:2:h", Pattern.lockedSet(UnitCategory.LINE, 2, false));
  }
}
