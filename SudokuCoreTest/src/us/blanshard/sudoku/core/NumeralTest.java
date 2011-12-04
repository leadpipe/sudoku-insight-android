package us.blanshard.sudoku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class NumeralTest {

  @Test public void all() {
    assertEquals(9, Numeral.ALL.size());
    int index = 0;
    for (Numeral numeral : Numeral.ALL) {
      assertEquals(index, numeral.index);
      assertSame(numeral, Numeral.ofIndex(index));
      ++index;
      assertEquals(index, numeral.number);
      assertSame(numeral, Numeral.of(index));

      for (Numeral n2 : Numeral.ALL) {
        assertEquals(numeral == n2, numeral.equals(n2));
        assertEquals(numeral == n2, numeral.hashCode() == n2.hashCode());
      }
    }
    assertEquals(9, index);
  }

  @Test public void string() {
    for (Numeral numeral : Numeral.ALL)
      assertEquals(String.valueOf(numeral.number), numeral.toString());
  }

  @Test public void compare() {
    for (Numeral n1 : Numeral.ALL) {
      for (Numeral n2 : Numeral.ALL) {
        if (n1.index < n2.index) assertEquals(true, n1.compareTo(n2) < 0);
        else if (n1.index > n2.index) assertEquals(true, n1.compareTo(n2) > 0);
        else assertEquals(true, n1.compareTo(n2) == 0);
      }
    }
  }
}
