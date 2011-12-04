package us.blanshard.sudoku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.junit.Test;

import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;

public class NumSetTest {

  public static NumSet set(int... nums) {
    short bits = 0;
    for (int n : nums)
      bits |= 1 << (n - 1);
    return NumSet.ofBits(bits);
  }

  @Test public void of() {
    assertEquals(set(), NumSet.of());
    assertEquals(set(4), NumSet.of(Numeral.of(4)));
    assertEquals(set(1, 8), NumSet.of(Numeral.of(1), Numeral.of(8)));
  }

  @Test public void not() {
    assertEquals(set(1,2,3,4,5,6,7,8,9), set().not());
    assertEquals(set(1,3,5,7,9), set(2,4,6,8).not());
  }

  @Test public void and() {
    assertEquals(set(4,5), set(3,4,5).and(set(4,5,6)));
    assertEquals(set(), set(3,4,5).and(set(6,7,8)));
  }

  @Test public void or() {
    assertEquals(set(1,2,3), set(1,2).or(set(1,3)));
  }

  @Test public void xor() {
    assertEquals(set(2,3), set(1,2).xor(set(1,3)));
  }

  @Test public void minus() {
    assertEquals(set(2,3), set(1,2,3,8).minus(set(1,8)));
  }

  @Test public void contains() {
    NumSet set = set(1, 3, 7, 8);
    assertEquals(true, set.contains(Numeral.of(3)));
    assertEquals(false, set.contains(Numeral.of(2)));
    assertEquals(false, set.contains(true));
  }

  @Test public void iterator() {
    NumSet set = set(2, 8, 9);
    Iterator<Numeral> it = set.iterator();
    assertTrue(it.hasNext());
    assertSame(Numeral.of(2), it.next());
    assertSame(Numeral.of(8), it.next());
    assertSame(Numeral.of(9), it.next());
    assertFalse(it.hasNext());
  }

  @Test public void size() {
    assertEquals(0, set().size());
    assertEquals(1, set(5).size());
    assertEquals(2, set(5, 4).size());
    assertEquals(7, set(1, 2, 3, 4, 7, 8, 9).size());
    assertEquals(9, set(1, 2, 3, 4, 5, 6, 7, 8, 9).size());
  }

  @Test public void equals() {
    Set<Numeral> hashSet = new HashSet<Numeral>();
    hashSet.addAll(Arrays.asList(Numeral.of(2), Numeral.of(4), Numeral.of(6)));
    NumSet set = set(2, 4, 6);
    assertEquals(set, hashSet);
    assertEquals(hashSet, set);
    assertEquals(set.hashCode(), hashSet.hashCode());
    assertEquals(false, set(1,2,3).equals(set(1,2)));
  }

  @Test public void string() {
    assertEquals("[3, 4, 8, 9]", set(3, 4, 8, 9).toString());
  }
}
