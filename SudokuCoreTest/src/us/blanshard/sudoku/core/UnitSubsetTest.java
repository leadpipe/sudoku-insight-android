/*
Copyright 2011 Google Inc.

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.Set;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class UnitSubsetTest {

  private static Unit unit = Block.of(5);

  private static UnitSubset set(int... nums) {
    return UnitSubset.ofBits(unit, NumSetTest.set(nums).bits);
  }

  @Test public void of() {
    assertEquals(set(), ImmutableSet.<Location>of());
    assertEquals(set(4), ImmutableSet.of(Location.of(5, 4)));
    assertSame(unit.get(4 - 1), Location.of(5, 4));
    assertEquals(set(1, 8), ImmutableSet.of(Location.of(4, 4), Location.of(6, 5)));
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
    UnitSubset set = set(1, 3, 7, 8);
    assertEquals(true, set.contains(unit.get(3 - 1)));
    assertEquals(false, set.contains(unit.get(2 - 1)));
    assertEquals(false, set.contains(Location.of(0)));
    assertEquals(false, set.contains(true));
  }

  @Test public void iterator() {
    UnitSubset set = set(2, 8, 9);
    Iterator<Location> it = set.iterator();
    assertTrue(it.hasNext());
    assertSame(unit.get(2 - 1), it.next());
    assertSame(unit.get(8 - 1), it.next());
    assertSame(unit.get(9 - 1), it.next());
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
    Set<Location> otherSet = ImmutableSet.of(unit.get(2 - 1), unit.get(4 - 1), unit.get(6 - 1));
    UnitSubset set = set(2, 4, 6);
    assertEquals(set, otherSet);
    assertEquals(otherSet, set);
    assertEquals(set.hashCode(), otherSet.hashCode());
    assertEquals(false, set(1,2,3).equals(set(1,2)));
  }

  @Test public void string() {
    assertEquals("[(4, 6), (5, 4), (6, 5), (6, 6)]", set(3, 4, 8, 9).toString());
  }
}
