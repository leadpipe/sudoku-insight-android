package us.blanshard.sudoku.insight2;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static us.blanshard.sudoku.insight2.TestHelper.*;

public class AnalyzerTest implements Analyzer.Callback {
  private final List<Insight> taken = new ArrayList<>();

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
    assertThat(taken).containsExactly(new Overlap(b(4), n(1), us(r(5), 2, 3)),
                                      new Overlap(b(2), n(1), us(c(5), 2, 3))).inOrder();
    // Ensure the antecedents were already calculated too.
    assertThat(taken.get(0).collectAntecedents(null)).containsExactly(ea(1, 1, 1));
    assertThat(taken.get(1).collectAntecedents(null)).containsExactly(ea(1, 1, 1));
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
    assertThat(taken).containsExactly(new Overlap(b(4), n(1), us(r(5), 2, 3)),
                                      new Overlap(b(2), n(1), us(c(5), 2, 3)),
                                      new Overlap(r(6), n(1), us(b(5), 8, 9)),
                                      new Overlap(c(6), n(1), us(b(5), 6, 9))).inOrder();
    // Ensure the antecedents were already calculated too.
    assertThat(taken.get(0).collectAntecedents(null)).containsExactly(ea(1, 1, 1), ea(4, 8, 1));
    assertThat(taken.get(1).collectAntecedents(null)).containsExactly(ea(1, 1, 1), ea(8, 4, 1));
  }

  @Test
  public void findOverlaps_blocks_skipAssignment3() {
    Analyzer.findOverlaps(
        m(" . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . 8 | . . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " 4 . . | . . 3 | . . 8 " +
          " 6 . 3 | . . . | . . . " +
          " 9 . . | 8 . . | . . . "), this);

    // Note that (5,3) compares after both (9,4) and (7,9) because of the number
    // of open locations in its units.  (This example is why we calculate
    // antecedents in findOverlaps.)

    assertThat(taken).containsExactly(new Overlap(b(7), n(8), us(c(2), 1,2,3)),
                                      new Overlap(c(1), n(8), us(b(1), 2,5,8))).inOrder();
    assertThat(taken.get(0).collectAntecedents(null)).containsExactly(ea(5, 3, 8));
  }

  @Test
  public void findOverlaps_blocks_skipAssignment4() {
    Insight set1 = new Implication(ImmutableSet.of(ea(1,9,5)),
                                   new LockedSet(ns(1,2,5), us(b(7),3,8,9), false));
    Insight set2 = new LockedSet(ns(1,2,7), us(b(7),2,3,9), false);
    Marks marks = mb(" . . . | . . . | . . . " +
                     " . . . | . . . | . . . " +
                     " . . . | . . . | . . . " +
                     "-------+-------+-------" +
                     " . . . | . . . | . . . " +
                     " . . . | . . . | . . . " +
                     " . . . | . . . | . . . " +
                     "-------+-------+-------" +
                     " 4 . . | . . 3 | . . . " +
                     " 6 . 3 | . . . | . . . " +
                     " 9 . . | . . . | . . . ")
        .add(set1).add(set2).build();
    Analyzer.findOverlaps(marks, this);

    // Note that these sets are utterly bogus.  Can't find a real example that
    // demonstrates retaining the second possible location's eliminations even
    // if they sort greater.

    assertThat(taken).containsExactly(new Overlap(b(7), n(5), us(c(2), 1,2,3,4,5,6)),
                                      new Overlap(b(7), n(7), us(c(2), 1,2,3,4,5,6)),
                                      new Overlap(b(7), n(8), us(c(2), 1,2,3,4,5,6))).inOrder();
    assertThat(taken.get(2).collectAntecedents(null)).containsExactly(set1);
    assertThat(marks.compare(set1, set2)).isGreaterThan(0);
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
    assertThat(taken).containsExactly(new Overlap(r(5), n(1), us(b(5), 4, 5, 6)),
                                      new Overlap(c(5), n(1), us(b(5), 4, 5, 6))).inOrder();
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
    assertThat(taken).containsExactly(new Overlap(c(5), n(1), us(b(5), 2, 8)),
                                      new Overlap(r(5), n(1), us(b(5), 4, 6))).inOrder();
    // Ensure the antecedents were already calculated too.
    assertThat(taken.get(0).collectAntecedents(null)).containsExactly(ea(1, 1, 1));
    assertThat(taken.get(1).collectAntecedents(null)).containsExactly(ea(1, 1, 1));
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

  @Test
  public void findErrors_conflict() {
    Analyzer.findErrors(
        m(" . . . | . . 2 | . . . " +
          " . . . | 1 1 . | . . . " +
          " . . . | . 2 2 | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . "), this);
    assertThat(taken).containsExactly(new Conflict(n(1), us(b(2), 4, 5)),
                                      new Conflict(n(2), us(b(2), 3, 8, 9)),
                                      new Conflict(n(1), us(r(2), 4, 5)),
                                      new Conflict(n(2), us(c(6), 1, 3)),
                                      new Conflict(n(2), us(r(3), 5, 6)));
  }

  @Test
  public void findErrors_barredLoc() {
    Analyzer.findErrors(
        m(" . . . | . 1 . | . . . " +
          " . . . | . 2 . | . . . " +
          " . . . | . 3 . | . . . " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " 4 5 6 | . . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " . . . | . 7 . | . . . " +
          " . . . | . 8 . | . . . " +
          " . . . | . 9 . | . . . "), this);
    assertThat(taken).containsExactly(new BarredLoc(l(5, 5)));
  }

  @Test
  public void findErrors_barredNum() {
    Analyzer.findErrors(
        m(" . . . | . . 1 | . . . " +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          "-------+-------+-------" +
          " 1 . . | . . . | . . . " +
          " . . . | . 2 . | . . . " +
          " . . . | . . . | . . 1 " +
          "-------+-------+-------" +
          " . . . | . . . | . . . " +
          " . . . | . . . | . . . " +
          " . . . | 1 . . | . . . "), this);
    assertThat(taken).containsExactly(new BarredNum(un(b(5), 1)),
                                      new BarredNum(un(r(5), 1)),
                                      new BarredNum(un(c(5), 1)));
  }

  @Test
  public void findErrors_noConflict() {
    Analyzer.findErrors(
        mb(" . . . | . . . | . . . " +
           " . . . | . . . | . . . " +
           " . . . | . . . | . . . " +
           "-------+-------+-------" +
           " . . . | . . . | . . . " +
           " . . . | . 1 . | . . . " +
           " . . . | . . . | . . . " +
           "-------+-------+-------" +
           " . . . | . . . | . . . " +
           " . . . | . . . | . . . " +
           " . . . | . . . | . . . ")
        .add(ee(5, 5, 1))
        .build(), this);
    assertThat(taken).containsExactly(new BarredLoc(l(5, 5)),
                                      new BarredNum(un(b(5), 1)),
                                      new BarredNum(un(r(5), 1)),
                                      new BarredNum(un(c(5), 1)));
  }

  @Test
  public void analyze_stopped() {
    boolean complete = Analyzer.analyze(
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
          " . . . | . . . | . . . "),
        new Analyzer.Callback() {
          @Override public void take(Insight insight) {
            throw new Analyzer.StopException();
          }
        });
    assertThat(complete).isFalse();
  }

  @Test
  public void analyze_interrupted() {
    boolean complete = Analyzer.analyze(
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
          " . . . | . . . | . . . "),
        new Analyzer.Callback() {
          @Override public void take(Insight insight) {
            Thread.currentThread().interrupt();
          }
        });
    assertThat(complete).isFalse();
    assertThat(Thread.interrupted()).isTrue();  // Also clears the bit.
  }

  @Test
  public void analyze_complete() {
    boolean complete = Analyzer.analyze(
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
    assertThat(complete).isTrue();
  }

  @Test
  public void analyze_real() {
    // Puzzle 1:2:2017-2:67
    Marks marks = m(
        " . . . | 1 . 9 | . . 6 " +
        " . 2 . | . 5 8 | 1 . . " +
        " . . . | 6 . . | . . 3 " +
        "-------+-------+-------" +
        " . . 9 | 2 . . | . 1 . " +
        " . . 8 | . . . | 5 . . " +
        " . 6 . | . . 4 | 7 . . " +
        "-------+-------+-------" +
        " 4 . . | . . 3 | . . . " +
        " . . 3 | 7 . . | . . . " +
        " 9 . . | 8 . 1 | . . 5 ");
    assertThat(Analyzer.analyze(marks, this)).isTrue();

    assertThat(taken).containsAllOf(imp(new ForcedLoc(b(1), n(9), l(3,2)),
                                        ea(4,3,9), ea(9,1,9), ea(1,6,9)),
                                    imp(new Overlap(b(4), n(4), us(c(2), 1,3)),
                                        ea(7,1,4), ea(6,6,4)));
  }
}
