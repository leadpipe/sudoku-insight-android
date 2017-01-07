package us.blanshard.sudoku.insight2;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

import us.blanshard.sudoku.core.Block;
import us.blanshard.sudoku.core.Column;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Row;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumeral;
import us.blanshard.sudoku.core.UnitSubset;

import static com.google.common.truth.Truth.assertThat;
import static us.blanshard.sudoku.core.NumSetTest.set;

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

  private static Marks m(String s) { return Marks.builder(Grid.fromString(s)).build(); }
  private static Numeral n(int num) { return Numeral.of(num); }
  private static Row r(int num) { return Row.of(num); }
  private static Column c(int num) { return Column.of(num); }
  private static Block b(int num) { return Block.of(num); }
  private static UnitNumeral un(Unit unit, int num) { return UnitNumeral.of(unit, n(num)); }
  private static UnitSubset us(Unit unit, int... nums) { return UnitSubset.ofBits(unit, set(nums).bits); }
}
