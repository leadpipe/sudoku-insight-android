package us.blanshard.sudoku.insight2;

import org.junit.Test;

import us.blanshard.sudoku.core.Grid;

import static com.google.common.truth.Truth.assertThat;
import static us.blanshard.sudoku.insight2.TestHelper.*;

public class MarksTest {

  @Test
  public void possibles() {
    Marks marks = m(
        " . 4 . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . 4 " +
        "-------+-------+-------" +
        " 7 . . | 1 9 . | . . 2 " +
        " . . . | . . . | . . . " +
        " . . 6 | . . . | . . . " +
        "-------+-------+-------" +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . ");

    assertThat(marks.getPossibleNumerals(l(4, 1))).isEqualTo(ns(7));
    assertThat(marks.getOnlyPossibleNumeral(l(4, 1))).isSameAs(n(7));
    assertThat(marks.getOnlyPossibleNumeral(l(4, 2))).isNull();
    assertThat(marks.getPossibleNumerals(l(4, 2))).isEqualTo(ns(3, 5, 8));
    assertThat(marks.getPossibleLocations(un(r(4), 7))).isEqualTo(us(r(4), 1));
    assertThat(marks.toBuilder().getBitsForPossibleLocations(un(r(4), 4))).isEqualTo(0xe4);
    assertThat(marks.getSizeOfPossibleLocations(un(r(4), 4))).isEqualTo(4);
    assertThat(marks.getOnlyPossibleLocation(un(b(1), 4))).isSameAs(l(1, 2));
    assertThat(marks.getOnlyPossibleLocation(un(b(1), 1))).isNull();
    assertThat(marks.isPossibleAssignment(l(1, 1), n(1))).isTrue();
    assertThat(marks.isPossibleAssignment(l(1, 1), n(7))).isFalse();
  }

  @Test public void assignments() {
    Grid grid = g(
        " . 4 . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . 4 " +
        "-------+-------+-------" +
        " 7 . . | 1 9 . | . . 2 " +
        " . . . | . . . | . . . " +
        " . . 6 | . . . | . . . " +
        "-------+-------+-------" +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . ");
    Marks marks = m(grid);

    assertThat(marks.getAssignedNumeral(l(3, 9))).isSameAs(n(4));
    assertThat(marks.getAssignedNumeral(l(3, 8))).isNull();
    assertThat(marks.getAssignedLocation(un(r(3), 4))).isSameAs(l(3, 9));
    assertThat(marks.getAssignedLocation(un(c(9), 4))).isSameAs(l(3, 9));
    assertThat(marks.getAssignedLocation(un(b(3), 4))).isSameAs(l(3, 9));
    assertThat(marks.getAssignedLocation(un(b(1), 5))).isNull();
    assertThat(marks.hasAssignment(un(b(1), 4))).isTrue();
    assertThat(marks.hasAssignment(un(b(1), 5))).isFalse();
    assertThat(marks.getAssignedLocations())
        .isEqualTo(ls(l(1, 2), l(3, 9), l(4, 1), l(4, 4), l(4, 5), l(4, 9), l(6, 3)));
    assertThat(marks.toBuilder().toGrid()).isEqualTo(grid);
    assertThat(marks.getNumAssignments()).isEqualTo(7);
    assertThat(marks.getNumOpenLocations()).isEqualTo(74);
    assertThat(marks.isSolved()).isFalse();
  }

  @Test public void unassigned() {
    Marks marks = m(
        " . 4 . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . 4 " +
        "-------+-------+-------" +
        " 7 . . | 1 9 . | . . 2 " +
        " . . . | . . . | . . . " +
        " . . 6 | . . . | . . . " +
        "-------+-------+-------" +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . ");

    assertThat(marks.getUnassignedNumerals(b(1))).isEqualTo(ns(1,2,3,5,6,7,8,9));
    assertThat(marks.getUnassignedLocations(b(1))).isEqualTo(us(b(1), 1,3,4,5,6,7,8,9));

    assertThat(marks.getUnassignedNumerals(r(4))).isEqualTo(ns(3,4,5,6,8));
    assertThat(marks.getUnassignedLocations(r(4))).isEqualTo(us(r(4), 2,3,6,7,8));

    assertThat(marks.getUnassignedNumerals(c(9))).isEqualTo(ns(1,3,5,6,7,8,9));
    assertThat(marks.getUnassignedLocations(c(9))).isEqualTo(us(c(9), 1,2,5,6,7,8,9));
  }

  @Test public void add() {
    Marks marks = m(
        " . 4 . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . 4 " +
        "-------+-------+-------" +
        " 7 . . | 1 9 . | . . 2 " +
        " . . . | . . . | . . . " +
        " . . 6 | . . . | . . . " +
        "-------+-------+-------" +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . ");

    Marks.Builder builder = marks.toBuilder();
    builder.add(ee(1, 1, 1));
    assertThat(builder.hasErrors()).isFalse();
    assertThat(marks.isPossibleAssignment(l(1, 1), n(1))).isTrue();
    assertThat(builder.build().isPossibleAssignment(l(1, 1), n(1))).isFalse();
  }

  @Test public void add_failure() {
    Marks marks = m(
        " . 4 . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . 4 " +
        "-------+-------+-------" +
        " 7 . . | 1 9 . | . . 2 " +
        " . . . | . . . | . . . " +
        " . . 6 | . . . | . . . " +
        "-------+-------+-------" +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . ");

    Marks.Builder builder = marks.toBuilder();
    builder.add(ea(1, 2, 1));
    assertThat(builder.hasErrors()).isTrue();
    assertThat(builder.getPossibleNumerals(l(1, 2))).isEmpty();
    assertThat(builder.getPossibleLocations(un(b(1), 4))).isEmpty();
  }

  @Test public void getEliminationInsights() {
    Marks marks = m(
        " . 4 . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . 4 " +
        "-------+-------+-------" +
        " 7 . . | 1 9 . | . . 2 " +
        " . . . | . . 4 | . . . " +
        " . . 6 | . . . | . . . " +
        "-------+-------+-------" +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . ");

    assertThat(marks.getEliminationInsights(a(5, 2, 4))).containsExactly(ea(1, 2, 4), ea(5, 6, 4));
  }

  @Test public void string() {
    Marks marks = m(
        " . 4 . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . 4 " +
        "-------+-------+-------" +
        " 7 . . | 1 9 . | . . 2 " +
        " . . . | . . . | . . . " +
        " . . 6 | . . . | . . . " +
        "-------+-------+-------" +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . " +
        " . . . | . . . | . . . ");

    assertThat(marks.toString()).isEqualTo(
        "  1235689     4!      1235789  |  2356789   1235678  12356789  | 12356789  12356789   1356789 \n" +
        "  1235689  12356789   1235789  | 23456789  12345678  123456789 | 12356789  12356789   1356789 \n" +
        "  1235689  12356789   1235789  |  2356789   1235678  12356789  | 12356789  12356789     4!    \n" +
        "-------------------------------+-------------------------------+-------------------------------\n" +
        "    7!        358      3458    |    1!        9!       34568   |   34568     34568      2!    \n" +
        "  1234589   123589    1234589  |  2345678   2345678   2345678  | 13456789  13456789   1356789 \n" +
        "  1234589   123589      6!     |  234578    234578    234578   |  1345789   1345789   135789  \n" +
        "-------------------------------+-------------------------------+-------------------------------\n" +
        " 12345689  12356789  12345789  | 23456789  12345678  123456789 | 123456789 123456789  1356789 \n" +
        " 12345689  12356789  12345789  | 23456789  12345678  123456789 | 123456789 123456789  1356789 \n" +
        " 12345689  12356789  12345789  | 23456789  12345678  123456789 | 123456789 123456789  1356789 \n");

    marks = m(
        " 1 2 3 | 4 5 6 | 7 8 9 " +
        " 4 5 6 | 7 8 9 | 1 2 3 " +
        " 7 8 9 | 1 2 3 | 4 5 6 " +
        "-------+-------+-------" +
        " 2 3 4 | 5 6 7 | 8 9 1 " +
        " 5 6 7 | 8 9 1 | 2 3 4 " +
        " 8 9 1 | 2 3 4 | 5 6 7 " +
        "-------+-------+-------" +
        " 3 4 5 | 6 7 8 | 9 1 2 " +
        " 6 7 8 | 9 1 2 | 3 4 5 " +
        " 9 1 2 | 3 4 5 | 6 7 8 ");

    assertThat(marks.toString()).isEqualTo(
        " 1! 2! 3! | 4! 5! 6! | 7! 8! 9!\n" +
        " 4! 5! 6! | 7! 8! 9! | 1! 2! 3!\n" +
        " 7! 8! 9! | 1! 2! 3! | 4! 5! 6!\n" +
        "----------+----------+----------\n" +
        " 2! 3! 4! | 5! 6! 7! | 8! 9! 1!\n" +
        " 5! 6! 7! | 8! 9! 1! | 2! 3! 4!\n" +
        " 8! 9! 1! | 2! 3! 4! | 5! 6! 7!\n" +
        "----------+----------+----------\n" +
        " 3! 4! 5! | 6! 7! 8! | 9! 1! 2!\n" +
        " 6! 7! 8! | 9! 1! 2! | 3! 4! 5!\n" +
        " 9! 1! 2! | 3! 4! 5! | 6! 7! 8!\n");
    assertThat(marks.isSolved()).isTrue();
  }
}
