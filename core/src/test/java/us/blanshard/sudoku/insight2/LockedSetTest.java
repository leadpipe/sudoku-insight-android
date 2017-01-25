package us.blanshard.sudoku.insight2;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static us.blanshard.sudoku.insight2.TestHelper.*;

public class LockedSetTest {
  @Test public void getEliminations_naked_overlap() {
    LockedSet set = new LockedSet(ns(1, 2), us(r(1), 1, 2), /*isNaked = */true);
    assertThat((Object) set.getOverlappingUnit()).isEqualTo(b(1));
    assertThat(set.getEliminations()).containsExactly(a(1, 3, 1), a(1, 3, 2),
        a(2, 1, 1), a(2, 1, 2), a(2, 2, 1), a(2, 2, 2), a(2, 3, 1), a(2, 3, 2),
        a(3, 1, 1), a(3, 1, 2), a(3, 2, 1), a(3, 2, 2), a(3, 3, 1), a(3, 3, 2),
        a(1, 4, 1), a(1, 4, 2), a(1, 5, 1), a(1, 5, 2), a(1, 6, 1), a(1, 6, 2),
        a(1, 7, 1), a(1, 7, 2), a(1, 8, 1), a(1, 8, 2), a(1, 9, 1), a(1, 9, 2));
  }

  @Test public void getEliminations_naked_noOverlap() {
    LockedSet set = new LockedSet(ns(1, 2), us(b(1), 1, 5), /*isNaked = */true);
    assertThat(set.getEliminations()).containsExactly(
        a(1, 2, 1), a(1, 2, 2), a(1, 3, 1), a(1, 3, 2),
        a(2, 1, 1), a(2, 1, 2), a(2, 3, 1), a(2, 3, 2),
        a(3, 1, 1), a(3, 1, 2), a(3, 2, 1), a(3, 2, 2), a(3, 3, 1), a(3, 3, 2));
  }

  @Test public void getEliminations_hidden_overlap_block() {
    LockedSet set = new LockedSet(ns(1, 2), us(b(1), 1, 2), /*isNaked = */false);
    assertThat(set.getEliminations()).containsExactly(
        a(1, 1, 3), a(1, 1, 4), a(1, 1, 5), a(1, 1, 6), a(1, 1, 7), a(1, 1, 8), a(1, 1, 9),
        a(1, 2, 3), a(1, 2, 4), a(1, 2, 5), a(1, 2, 6), a(1, 2, 7), a(1, 2, 8), a(1, 2, 9),
        a(1, 4, 1), a(1, 4, 2), a(1, 5, 1), a(1, 5, 2), a(1, 6, 1), a(1, 6, 2),
        a(1, 7, 1), a(1, 7, 2), a(1, 8, 1), a(1, 8, 2), a(1, 9, 1), a(1, 9, 2));
  }

  @Test public void getEliminations_hidden_overlap_line() {
    LockedSet set = new LockedSet(ns(1, 2), us(r(1), 1, 2), /*isNaked = */false);
    assertThat(set.getEliminations()).containsExactly(
        a(1, 1, 3), a(1, 1, 4), a(1, 1, 5), a(1, 1, 6), a(1, 1, 7), a(1, 1, 8), a(1, 1, 9),
        a(1, 2, 3), a(1, 2, 4), a(1, 2, 5), a(1, 2, 6), a(1, 2, 7), a(1, 2, 8), a(1, 2, 9),
        a(2, 1, 1), a(2, 1, 2), a(2, 2, 1), a(2, 2, 2), a(2, 3, 1), a(2, 3, 2),
        a(3, 1, 1), a(3, 1, 2), a(3, 2, 1), a(3, 2, 2), a(3, 3, 1), a(3, 3, 2));
  }

  @Test public void getEliminations_hidden_noOverlap() {
    LockedSet set = new LockedSet(ns(1, 2), us(b(1), 1, 5), /*isNaked = */false);
    assertThat(set.getEliminations()).containsExactly(
        a(1, 1, 3), a(1, 1, 4), a(1, 1, 5), a(1, 1, 6), a(1, 1, 7), a(1, 1, 8), a(1, 1, 9),
        a(2, 2, 3), a(2, 2, 4), a(2, 2, 5), a(2, 2, 6), a(2, 2, 7), a(2, 2, 8), a(2, 2, 9));
  }

  @Test public void makeEliminations() {
    Marks marks = m(
        " . . . | 1 . . | . . . " +
        " . . . | 2 . . | . . . " +
        " . . . | 3 . . | . . . " +
        "-------+-------+-------" +
        " . . . | . 4 . | . . . " +
        " . . . | . 5 . | . . . " +
        " . . . | . 6 . | . . . " +
        "-------+-------+-------" +
        " . . . | . . 7 | . . . " +
        " . . . | . . 8 | . . . " +
        " . . . | . . 9 | . . . ");

    assertThat(
        LockedSet.makeEliminations(ns(1, 2, 3), us(b(8), 2, 5, 8), /*isNaked = */true, c(5), marks))
        .isEmpty();
    assertThat(
        LockedSet.makeEliminations(ns(1, 2, 3), us(b(8), 2, 5, 8), /*isNaked = */false, c(5), marks))
        .isEmpty();

    marks = m(
        " . . . | 1 . . | . . . " +
        " . . . | 2 . . | . . . " +
        " . . . | 3 . . | . . . " +
        "-------+-------+-------" +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . " +
        "-------+-------+-------" +
        " . . . | . . 7 | . . . " +
        " . . . | . . 8 | . . . " +
        " . . . | . . 9 | . . . ");

    assertThat(
        LockedSet.makeEliminations(ns(4, 5, 6), us(b(8), 1, 4, 7), /*isNaked = */true, c(4), marks))
        .containsExactly(a(7, 5, 4), a(7, 5, 5), a(7, 5, 6),
                         a(8, 5, 4), a(8, 5, 5), a(8, 5, 6),
                         a(9, 5, 4), a(9, 5, 5), a(9, 5, 6),
                         a(4, 4, 4), a(4, 4, 5), a(4, 4, 6),
                         a(5, 4, 4), a(5, 4, 5), a(5, 4, 6),
                         a(6, 4, 4), a(6, 4, 5), a(6, 4, 6));
    assertThat(
        LockedSet.makeEliminations(ns(1, 2, 3), us(b(8), 2, 5, 8), /*isNaked = */false, c(5), marks))
        .containsExactly(a(7, 5, 4), a(7, 5, 5), a(7, 5, 6),
                         a(8, 5, 4), a(8, 5, 5), a(8, 5, 6),
                         a(9, 5, 4), a(9, 5, 5), a(9, 5, 6),
                         a(4, 5, 1), a(4, 5, 2), a(4, 5, 3),
                         a(5, 5, 1), a(5, 5, 2), a(5, 5, 3),
                         a(6, 5, 1), a(6, 5, 2), a(6, 5, 3));
  }
}
