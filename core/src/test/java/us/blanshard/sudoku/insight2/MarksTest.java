package us.blanshard.sudoku.insight2;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;

import org.junit.Test;

import us.blanshard.sudoku.core.Grid;

import static com.google.common.truth.Truth.assertThat;
import static us.blanshard.sudoku.insight2.TestHelper.*;

public class MarksTest {

  @Test public void possibles() {
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
    assertThat(marks.isEliminatedByAssignment(l(1, 1), n(1))).isFalse();
    assertThat(marks.isPossibleAssignment(l(1, 1), n(7))).isFalse();
    assertThat(marks.isEliminatedByAssignment(l(1, 1), n(7))).isTrue();
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
    assertThat(marks.isEliminatedByAssignment(l(1, 2), n(4))).isFalse();
    assertThat(marks.isEliminatedByAssignment(l(1, 2), n(5))).isTrue();
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
    assertThat(builder.build().isEliminatedByAssignment(l(1, 1), n(1))).isFalse();
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

  @Test public void getAssignmentInsight() {
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

    assertThat(marks.getAssignmentInsight(a(4, 1, 7))).isEqualTo(ea(4, 1, 7));
    try {
      marks.getAssignmentInsight(a(4, 1, 6));
      Truth.THROW_ASSERTION_ERROR.fail("Expected exception");
    } catch (RuntimeException ignored) {
    }
  }

  private static void assertCompareEqual(Marks marks, Insight a, Insight b) {
    assertThat(marks.compare(a, b)).isEqualTo(0);
    assertThat(marks.compare(b, a)).isEqualTo(0);
  }

  private static void assertCompareInOrder(Marks marks, Insight a, Insight b) {
    assertThat(marks.compare(a, b)).isLessThan(0);
    assertThat(marks.compare(b, a)).isGreaterThan(0);
  }

  @Test public void compare() {
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

    // Note these insights are not realistic.
    ExplicitAssignment ea = ea(4, 1, 7);
    Overlap overlap = new Overlap(b(5), n(7), us(r(5), 7, 8, 9));
    ForcedLoc flb = new ForcedLoc(b(4), n(3), l(4, 2));
    ForcedLoc fll = new ForcedLoc(c(2), n(3), l(4, 2));

    assertCompareInOrder(marks, ea,
                         new Implication(ImmutableSet.of(ea), overlap));  // cost
    assertCompareInOrder(marks, ea, overlap);  // type
    assertCompareInOrder(marks,
                         new Implication(ImmutableSet.of(ea), flb),
                         new Implication(ImmutableSet.of(ea), overlap));  // nub type

    assertCompareInOrder(marks, flb, fll);  // blocks before lines
    assertCompareInOrder(marks, ea(4, 9, 2), ea(4, 6, 5));  // 5,7,8 vs 5,7,9 open
    assertCompareInOrder(marks, ea, ea(4, 9, 2));  // same open, earlier location
    assertCompareEqual(marks, ea, ea);

    assertCompareInOrder(marks, overlap,
                         new Overlap(r(5), n(7), us(b(5), 7, 8, 9)));  // blocks before lines
    assertCompareInOrder(marks, overlap,
                         new Overlap(b(2), n(7), us(c(5), 7, 8, 9)));  // fuller block first
    assertCompareInOrder(marks, new Overlap(b(4), n(5), us(r(6), 7, 8, 9)),
                         overlap);  // unit order
    assertCompareEqual(marks, overlap, new Overlap(b(5), n(5), us(r(6), 7, 8, 9)));

    assertCompareInOrder(marks,
                         new LockedSet(ns(3, 4), us(b(5), 3, 4), true),
                         new LockedSet(ns(3, 4, 5), us(b(5), 3, 4, 7), true));  // size
    assertCompareInOrder(marks,
                         new LockedSet(ns(3, 4), us(b(5), 3, 4), false),
                         new LockedSet(ns(3, 5), us(b(2), 4, 5), true));  // hidden first
    assertCompareInOrder(marks,
                         new LockedSet(ns(3, 4), us(b(5), 3, 4), false),
                         new LockedSet(ns(3, 5), us(b(2), 4, 5), false));  // fuller block first
    assertCompareInOrder(marks,
                         new LockedSet(ns(3, 4), us(b(4), 3, 4), false),
                         new LockedSet(ns(3, 5), us(b(5), 4, 5), false));  // unit order
    assertCompareEqual(marks,
                       new LockedSet(ns(3, 4), us(b(5), 3, 4), false),
                       new LockedSet(ns(3, 5), us(b(5), 4, 5), false));
  }

  private static Implication makeImplication(Marks marks, Insight insight) {
    return new Implication(insight.getAntecedents(marks), insight);
  }

  @Test public void collectAntecedents() {
    // Puzzle 1:2:2017-2:67
    Marks marks = m(
        " . . . | 1 3 9 | . . 6 " +
        " 3 2 6 | 4 5 8 | 1 . . " +
        " . 9 . | 6 . . | . . 3 " +
        "-------+-------+-------" +
        " . . 9 | 2 . . | . 1 . " +
        " . . 8 | . . . | 5 . . " +
        " . 6 . | . . 4 | 7 . . " +
        "-------+-------+-------" +
        " 4 . . | . . 3 | . . . " +
        " 6 . 3 | 7 . . | . . . " +
        " 9 . . | 8 . 1 | . . 5 ");

    Implication b7_fn7 = makeImplication(marks, new ForcedNum(l(9,2), n(7)));
    assertThat(b7_fn7.getAntecedents()).containsExactly(ea(9,6,1), ea(2,2,2), ea(8,3,3), ea(7,1,4),
                                                       ea(9,9,5), ea(8,1,6), ea(9,4,8), ea(9,1,9));

    Implication b7_ov8 = makeImplication(marks, new Overlap(b(7), n(8), us(c(2), 1)));
    assertThat(b7_ov8.getAntecedents()).containsExactly(ea(5,3,8));

    Implication b4_ov4 = makeImplication(marks, new Overlap(b(4), n(4), us(c(2), 1)));
    assertThat(b4_ov4.getAntecedents()).containsExactly(ea(7,1,4), ea(6,6,4));

    Implication b4_set = makeImplication(marks, new LockedSet(ns(3,4), us(b(4), 2,5), false));
    assertThat(b4_set.getAntecedents()).containsExactly(ea(2,1,3), ea(8,3,3), ea(7,1,4), ea(6,6,4));

    Implication b7_set158 = makeImplication(marks,
                                            new LockedSet(ns(8,1,5), us(b(7), 2,3,5), false));
    assertThat(b7_set158.getAntecedents()).containsExactly(ea(9,4,8), ea(9,6,1), ea(9,9,5));

    marks = marks.toBuilder()  // Note we're not adding the assignments here.
        .add(b7_ov8)
        .add(b4_ov4)
        .add(b4_set)
        .add(b7_set158)
        .build();

    Implication c2_ov1 = makeImplication(marks, new Overlap(c(2), n(1), us(b(7), 3,9)));
    assertThat(c2_ov1.getAntecedents()).containsExactly(ea(1,4,1), b4_set);

    Implication b7_fl2 = makeImplication(marks, new ForcedLoc(b(7), n(2), l(9,3)));
    assertThat(b7_fl2.getAntecedents()).containsExactly(ea(2,2,2), b7_set158);

    assertCompareInOrder(marks, b7_fl2, b7_fn7);

    marks = marks.toBuilder()  // Here we add the cheapest assignment.
        .add(c2_ov1)
        .add(b7_fl2)
        .build();

    Implication b7_set18 = makeImplication(marks, new LockedSet(ns(1,8), us(b(7), 2,5), false));
    assertThat(b7_set18.getAntecedents()).containsExactly(ea(5,3,8), ea(9,4,8), ea(9,6,1), c2_ov1);

    marks = marks.toBuilder()
        .add(b7_set18)
        .build();

    Implication b7_fl7 = makeImplication(marks, new ForcedLoc(b(7), n(7), l(9,2)));
    assertThat(b7_fl7.getAntecedents()).containsExactly(b7_set158, b7_fl2);

    assertCompareInOrder(marks, b7_fl7, b7_fn7);

    marks = marks.toBuilder()
        .add(b7_fl7)
        .build();

    Implication b1_fn5 = makeImplication(marks, new ForcedNum(l(1,2), n(5)));
    assertThat(b1_fn5.getAntecedents()).containsExactly(ea(1,4,1), ea(2,2,2), ea(2,1,3), b4_ov4,
                                                        ea(2,3,6), b7_fl7, b7_ov8, ea(1,6,9));

    marks = marks.toBuilder()
        .add(b1_fn5)
        .build();

    // This is the point of this test: the old app showed the antecedents of
    // this assignment including b1_fn5.  These antecedents make more sense.
    Implication b7_fl5 = makeImplication(marks, new ForcedLoc(b(7), n(5), l(7,3)));
    assertThat(b7_fl5.getAntecedents()).containsExactly(ea(9,9,5), b7_set18);

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
