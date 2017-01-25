package us.blanshard.sudoku.insight2;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;
import static us.blanshard.sudoku.insight2.TestHelper.*;

public class AnalyzerTest implements Analyzer.Callback {
  private final Collection<Insight> taken = new ArrayList<>();

  @Override
  public void take(Insight insight) {
    taken.add(insight);
  }

  @Test
  public void findOverlaps_blocks() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | 2 . 3 | . . . " +
          " . . . | 4 . 5 | . . . " +
          "-------+-------+-------" +
          " . 2 3 | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . 4 5 | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . "), this);
    assertThat(taken).containsExactly(new Overlap(b(2), n(1), us(c(5), 2, 3)),
                                      new Overlap(b(4), n(1), us(r(5), 2, 3)));
  }

  @Test
  public void findOverlaps_blocks_skipAssignment() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | 2 . 3 | . . . " +
          " . . . | 4 . 5 | . . 1 " +
          "-------+-------+-------" +
          " . 2 3 | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . 4 5 | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . 1 | . . . | . . . "), this);
    assertThat(taken).containsExactly(new Overlap(b(2), n(1), us(c(5), 2, 3)),
                                      new Overlap(b(4), n(1), us(r(5), 2, 3)));
  }

  @Test
  public void findOverlaps_blocks_skipAssignment2() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | 2 . 3 | . . . " +
          " . . . | . . 5 | . . 1 " +
          "-------+-------+-------" +
          " . 2 . | . . . | . 1 . " +
          " . . . | . . . | . . . " +
          " . 4 5 | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | 1 . . | . . . " +
          " . . 1 | . . . | . . . "), this);
    assertThat(taken).containsExactly(new Overlap(b(2), n(1), us(c(5), 2, 3)),
                                      new Overlap(b(4), n(1), us(r(5), 2, 3)),
                                      new Overlap(r(6), n(1), us(b(5), 5, 6)),
                                      new Overlap(c(6), n(1), us(b(5), 5, 6)));
  }

  @Test
  public void findOverlaps_lines() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | . 2 . | . . . " +
          " . . . | . 3 . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . 2 3 | . . . | 4 5 6 " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . 4 . | . . . " +
          " . . . | . 5 . | . . . " +
          " . . . | . 6 . | . . . "), this);
    assertThat(taken).containsExactly(new Overlap(c(5), n(1), us(b(5), 4, 5, 6)),
                                      new Overlap(r(5), n(1), us(b(5), 4, 5, 6)));
  }

  @Test
  public void findOverlaps_lines_skipAssignment() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | . 2 . | . . . " +
          " . . . | . 3 . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . 7 . | . . . " +
          " . . . | . . . | . . 1 " +
          "-------+-------+-------" +
          " . . . | . 4 . | . . . " +
          " . . . | . 5 . | . . . " +
          " . . . | . 6 . | . . . "), this);
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . 2 3 | . 7 . | 4 5 6 " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . 1 | . . . "), this);
    assertThat(taken).containsExactly(new Overlap(c(5), n(1), us(b(5), 4, 6)),
                                      new Overlap(r(5), n(1), us(b(5), 4, 6)));
  }

  @Test
  public void findOverlaps_none_forcedLoc() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | 2 . 3 | . . . " +
          " . . . | 4 5 6 | . . . " +
          "-------+-------+-------" +
          " . 2 3 | . . . | . . . " +
          " . . 4 | . . . | . . . " +
          " . 5 6 | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . "), this);
    // Finds the line overlaps, but not the spurious block 2 and 4 overlaps.
    assertThat(taken).containsExactly(new Overlap(r(3), n(1), us(b(3), 7, 8, 9)),
                                      new Overlap(c(3), n(1), us(b(7), 7, 8, 9)));
  }

  @Test
  public void findOverlaps_none_blocks_alreadyThere() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | 2 1 3 | . . . " +
          " . . . | 4 . 6 | . . . " +
          "-------+-------+-------" +
          " . 2 3 | . . . | . . . " +
          " . 1 . | . . . | . . . " +
          " . 5 6 | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . "), this);
    assertThat(taken).containsExactly();
  }

  @Test
  public void findOverlaps_none_lines_alreadyThere() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | . 2 . | . . . " +
          " . . . | . 3 . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . 2 3 | . 1 . | 4 5 6 " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . 4 . | . . . " +
          " . . . | . 5 . | . . . " +
          " . . . | . 6 . | . . . "), this);
    assertThat(taken).containsExactly();
  }

  @Test
  public void findOverlaps_none_blocks() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | 2 . 3 | . . . " +
          " . . . | . . 5 | . . 1 " +
          "-------+-------+-------" +
          " . 2 . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . 4 5 | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . 1 | . . . | . . . "), this);
    assertThat(taken).containsExactly();
  }

  @Test
  public void findOverlaps_none_nothingToEliminate() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . 1 " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . 1 | . . . | . . . "), this);
    assertThat(taken).containsExactly();
  }

  @Test
  public void findOverlaps_none_error_blocks() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | 2 . 3 | . . . " +
          " . . . | 4 . 5 | . 1 . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | 1 . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . 1 . | . . . "), this);
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . 2 3 | . . . | . . . " +
          " . . . | . . . | . . 1 " +
          " . 4 5 | 1 . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . 1 | . . . | . . . " +
          " . . . | . . . | . . . "), this);
    assertThat(taken).containsExactly();
  }

  @Test
  public void findOverlaps_none_error_lines() {
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | . 2 . | . . . " +
          " . . . | . 3 . | . . . " +
          "-------+-------+-------" +
          " . 1 . | . . . | . . . " +
          " . . . | . 7 . | . . . " +
          " . . . | . . . | . . 1 " +
          "-------+-------+-------" +
          " . . . | . 4 . | . . . " +
          " . . . | . 5 . | . . . " +
          " . . . | . 6 . | . . . "), this);
    Analyzer.findOverlaps(
        m(" 1 . . | . . . | . . . " +
          " . . . | 1 . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . 2 3 | . 7 . | 4 5 6 " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . 1 | . . . "), this);
    assertThat(taken).containsExactly();
  }

  @Test
  public void findSets_naked_justOne() {
    // Both b8 and c4 have naked sets, but they are equivalent.
    Analyzer.findSets(
        m(" . . . | 1 . . | . . . " +
          " . . . | 2 . . | . . . " +
          " . . . | 3 . . | . . . " +
          "-------+-------+-------" +
          " . . . | 4 . . | . . . " +
          " . . . | 5 . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . 6 | . . . "), this);
    assertThat(taken).containsExactly(
        new LockedSet(ns(7, 8, 9), us(b(8), 1, 4, 7), /*isNaked = */true));
  }

  @Test
  public void findSets_hidden() {
    Analyzer.findSets(
        m(" . . . | 1 . . | . . . " +
          " . . . | 2 . . | . . . " +
          " . . . | 3 . . | . . . " +
          "-------+-------+-------" +
          " . . . | . 1 . | . . . " +
          " . . . | . 2 . | . . . " +
          " . . . | . 3 . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . "), this);
    assertThat(taken).containsExactly(
        new LockedSet(ns(1, 2, 3), us(b(8), 3, 6, 9), /*isNaked = */false),
        new LockedSet(ns(1, 2, 3), us(c(6), 7, 8, 9), /*isNaked = */false));
  }

  @Test
  public void findSets_both() {
    Analyzer.findSets(
        m(" . . . | 1 . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | 2 . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | 3 . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . 4 . | . . . " +
          " . . . | . 5 . | . . . " +
          " . . . | . 6 . | . . . "), this);
    assertThat(taken).containsExactly(
        new LockedSet(ns(1, 2, 3), us(b(8), 3, 6, 9), /*isNaked = */false),
        new LockedSet(ns(4, 5, 6), us(c(4), 2, 4, 6), /*isNaked = */false),
        new LockedSet(ns(7, 8, 9), us(b(8), 1, 4, 7), /*isNaked = */true));
  }

  @Test
  public void findSets_both_allOpen() {
    Analyzer.findSets(
        m(" . . . | 1 4 . | . . . " +
          " . . . | 2 5 . | . . . " +
          " . . . | 3 6 . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . "), this);
    assertThat(taken).containsExactly(
        new LockedSet(ns(7, 8, 9), us(b(2), 3, 6, 9), /*isNaked = */true),
        new LockedSet(ns(7, 8, 9), us(b(2), 3, 6, 9), /*isNaked = */false));
  }

  @Test
  public void findSets_none_nothingToDo() {
    // Lots of sets, but none that eliminates anything new.
    Analyzer.findSets(
        m(" . . . | 1 . . | . . . " +
          " . . . | 2 . . | . . . " +
          " . . . | 3 . . | . . . " +
          "-------+-------+-------" +
          " . . . | . 4 . | . . . " +
          " . . . | . 5 . | . . . " +
          " . . . | . 6 . | . . . " +
          "-------+-------+-------" +
          " . . . | . . 7 | . . . " +
          " . . . | . . 8 | . . . " +
          " . . . | . . 9 | . . . "), this);
    assertThat(taken).containsExactly();
  }
}
