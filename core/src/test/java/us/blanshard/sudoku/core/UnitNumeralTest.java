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
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class UnitNumeralTest {
  @Test public void of() {
    assertEquals(243, UnitNumeral.COUNT);
    for (int i = 0; i < UnitNumeral.COUNT; ++i) {
      UnitNumeral un = UnitNumeral.of(i);
      assertEquals(i, un.index);
      assertSame(un, UnitNumeral.of(un.unit, un.numeral));
    }
  }

  @Test public void all() {
    assertEquals(UnitNumeral.COUNT, UnitNumeral.all().size());
    for (int i = 0; i < UnitNumeral.COUNT; ++i)
      assertSame(UnitNumeral.of(i), UnitNumeral.all().get(i));
  }

  @Test public void string() {
    for (UnitNumeral un : UnitNumeral.all())
      assertEquals(un.unit + ":" + un.numeral, un.toString());
  }
}
