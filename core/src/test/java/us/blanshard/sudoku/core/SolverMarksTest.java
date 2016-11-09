/*
Copyright 2013 Luke Blanshard

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
package us.blanshard.sudoku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static us.blanshard.sudoku.core.NumSetTest.set;
import static us.blanshard.sudoku.core.UnitTest.loc;

import org.junit.Before;
import org.junit.Test;

public class SolverMarksTest {

  private final Grid.Builder builder = Grid.builder();
  private SolverMarks marks;

  @Before public void setUp() {
    builder.put(loc(1, 2), Numeral.of(4));
    builder.put(loc(4, 1), Numeral.of(7));
    builder.put(loc(6, 3), Numeral.of(6));
    builder.put(loc(4, 9), Numeral.of(2));
    builder.put(loc(3, 9), Numeral.of(4));
    builder.put(loc(4, 4), Numeral.of(1));
    builder.put(loc(4, 5), Numeral.of(9));

    marks = build();
  }

  private SolverMarks build() {
    SolverMarks.Builder b = SolverMarks.builder();
    assertTrue(b.assignAllRecursively(builder.build()));
    return b.build();
  }

  @Test public void get() {
    assertEquals(set(7), marks.get(loc(4, 1)));
    assertEquals(set(3, 5, 8), marks.get(loc(4, 2)));
    assertEquals(UnitSubset.singleton(Row.of(4), loc(4, 1)), marks.get(UnitNumeral.of(Row.of(4), Numeral.of(7))));
    assertEquals(UnitSubset.ofBits(Row.of(4), 0xe4), marks.get(UnitNumeral.of(Row.of(4), Numeral.of(4))));
  }
}
