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

public class NumeralTest {

  @Test public void all() {
    assertEquals(9, Numeral.all().size());
    int index = 0;
    for (Numeral numeral : Numeral.all()) {
      assertEquals(index, numeral.index);
      assertSame(numeral, Numeral.ofIndex(index));
      ++index;
      assertEquals(index, numeral.number);
      assertSame(numeral, Numeral.of(index));

      for (Numeral n2 : Numeral.all()) {
        assertEquals(numeral == n2, numeral.equals(n2));
        assertEquals(numeral == n2, numeral.hashCode() == n2.hashCode());
      }
    }
    assertEquals(9, index);
  }

  @Test public void string() {
    for (Numeral numeral : Numeral.all())
      assertEquals(String.valueOf(numeral.number), numeral.toString());
  }

  @Test public void compare() {
    for (Numeral n1 : Numeral.all()) {
      for (Numeral n2 : Numeral.all()) {
        if (n1.index < n2.index) assertEquals(true, n1.compareTo(n2) < 0);
        else if (n1.index > n2.index) assertEquals(true, n1.compareTo(n2) > 0);
        else assertEquals(true, n1.compareTo(n2) == 0);
      }
    }
  }
}