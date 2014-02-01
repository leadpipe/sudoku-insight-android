/*
Copyright 2014 Luke Blanshard

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package us.blanshard.sudoku.insight;

import static org.junit.Assert.assertEquals;
import static us.blanshard.sudoku.insight.Evaluator.KIND_COMPARE;
import static us.blanshard.sudoku.insight.Evaluator.KIND_REVERSE;
import static us.blanshard.sudoku.insight.PartialComparators.allLessOrEqual;
import static us.blanshard.sudoku.insight.PartialComparators.updateMinima;

import us.blanshard.sudoku.insight.Evaluator.MoveKind;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

public class PartialComparatorsTest {
  @Test public void updateMinima_empty() {
    // given
    Set<MoveKind> set = of();

    // when
    updateMinima(set, MoveKind.DIRECT_HARD, KIND_COMPARE);

    // then
    assertEquals(of(MoveKind.DIRECT_HARD), set);
  }

  @Test public void updateMinima_equal() {
    // given
    Set<MoveKind> set = of(MoveKind.DIRECT_HARD);

    // when
    updateMinima(set, MoveKind.DIRECT_HARD, KIND_COMPARE);

    // then
    assertEquals(of(MoveKind.DIRECT_HARD), set);
  }

  @Test public void updateMinima_less() {
    // given
    Set<MoveKind> set = of(MoveKind.DIRECT_HARD);

    // when
    updateMinima(set, MoveKind.DIRECT_EASY, KIND_COMPARE);

    // then
    assertEquals(of(MoveKind.DIRECT_EASY), set);
  }

  @Test public void updateMinima_greater() {
    // given
    Set<MoveKind> set = of(MoveKind.DIRECT_HARD);

    // when
    updateMinima(set, MoveKind.SIMPLY_IMPLIED_HARD, KIND_COMPARE);

    // then
    assertEquals(of(MoveKind.DIRECT_HARD), set);
  }

  @Test public void updateMinima_notComparable() {
    // given
    Set<MoveKind> set = of(MoveKind.DIRECT_HARD);

    // when
    updateMinima(set, MoveKind.SIMPLY_IMPLIED_EASY, KIND_COMPARE);

    // then
    assertEquals(of(MoveKind.DIRECT_HARD, MoveKind.SIMPLY_IMPLIED_EASY), set);
  }

  @Test public void updateMinima_lessThanOne() {
    // given
    Set<MoveKind> set = of(MoveKind.DIRECT_HARD, MoveKind.COMPLEXLY_IMPLIED_EASY);

    // when
    updateMinima(set, MoveKind.SIMPLY_IMPLIED_EASY, KIND_COMPARE);

    // then
    assertEquals(of(MoveKind.DIRECT_HARD, MoveKind.SIMPLY_IMPLIED_EASY), set);
  }

  @Test public void updateMinima_lessThanBoth() {
    // given
    Set<MoveKind> set = of(MoveKind.DIRECT_HARD, MoveKind.COMPLEXLY_IMPLIED_EASY);

    // when
    updateMinima(set, MoveKind.DIRECT_EASY, KIND_COMPARE);

    // then
    assertEquals(of(MoveKind.DIRECT_EASY), set);
  }

  @Test public void updateMinima_reverse_bottom() {
    // given
    Set<MoveKind> set = of(MoveKind.DIRECT_EASY);

    // when
    updateMinima(set, MoveKind.COMPLEXLY_IMPLIED_HARD, KIND_REVERSE);

    // then
    assertEquals(of(MoveKind.COMPLEXLY_IMPLIED_HARD), set);
  }

  @Test public void allLessOrEqual_top() {
    // given
    Set<MoveKind> set = of(MoveKind.COMPLEXLY_IMPLIED_HARD);

    // when
    Set<MoveKind> leq = allLessOrEqual(MoveKind.class, set, KIND_COMPARE);

    // then
    assertEquals(EnumSet.allOf(MoveKind.class), leq);
  }

  @Test public void allLessOrEqual_bottom() {
    // given
    Set<MoveKind> set = of(MoveKind.DIRECT_EASY);

    // when
    Set<MoveKind> leq = allLessOrEqual(MoveKind.class, set, KIND_COMPARE);

    // then
    assertEquals(of(MoveKind.DIRECT_EASY), leq);
  }

  @Test public void allLessOrEqual_line() {
    // given
    Set<MoveKind> set = of(MoveKind.COMPLEXLY_IMPLIED_EASY);

    // when
    Set<MoveKind> leq = allLessOrEqual(MoveKind.class, set, KIND_COMPARE);

    // then
    assertEquals(of(MoveKind.COMPLEXLY_IMPLIED_EASY, MoveKind.SIMPLY_IMPLIED_EASY, MoveKind.DIRECT_EASY), leq);
  }

  @Test public void allLessOrEqual_twoLines() {
    // given
    Set<MoveKind> set = of(MoveKind.SIMPLY_IMPLIED_EASY, MoveKind.DIRECT_HARD);

    // when
    Set<MoveKind> leq = allLessOrEqual(MoveKind.class, set, KIND_COMPARE);

    // then
    assertEquals(of(MoveKind.SIMPLY_IMPLIED_EASY, MoveKind.DIRECT_HARD, MoveKind.DIRECT_EASY), leq);
  }

  private Set<MoveKind> of(MoveKind...kinds) {
    EnumSet<MoveKind> answer = EnumSet.noneOf(MoveKind.class);
    for (MoveKind k : kinds)
      answer.add(k);
    return answer;
  }
}
