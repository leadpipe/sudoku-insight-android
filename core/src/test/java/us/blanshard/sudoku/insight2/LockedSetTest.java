package us.blanshard.sudoku.insight2;

import com.google.common.truth.Truth;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;
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
}
