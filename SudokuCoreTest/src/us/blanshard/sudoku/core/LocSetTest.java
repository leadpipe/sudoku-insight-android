/*
Copyright 2012 Google Inc.

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
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import org.junit.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

public class LocSetTest {

  private static LocSet set(int... locs) {
    return new LocSet(Lists.transform(Ints.asList(locs), new Function<Integer, Location>() {
      @Override public Location apply(Integer i) {
        return Location.of(i);
      }
    }));
  }

  @Test public void shouldStartEmpty() {
    // given
    LocSet set = new LocSet();

    // then
    assertTrue(set.isEmpty());
    assertEquals(0, set.size());
  }

  @Test public void shouldContainGivenLocations() {
    // given
    LocSet set = set(0, 63, 64, 80);

    // then
    assertEquals(4, set.size());
    assertTrue(set.contains(Location.of(0)));
    assertTrue(set.contains(Location.of(63)));
    assertTrue(set.contains(Location.of(64)));
    assertTrue(set.contains(Location.of(80)));
  }

  @Test public void shouldNotContainOtherLocations() {
    // given
    LocSet set = set(0, 63, 64, 80);

    // then
    assertFalse(set.contains(Location.of(1)));
    assertFalse(set.contains(Location.of(40)));
    assertFalse(set.contains(Location.of(79)));
  }

  @Test public void shouldSubsumeUnionedCollections() {
    // given
    LocSet set = LocSet.union(Row.of(4), Block.of(5));

    // then
    assertTrue(set.containsAll(Row.of(4)));
    assertTrue(set.containsAll(Block.of(5)));
    assertFalse(set.containsAll(Column.of(6)));
    assertEquals(18 - 3, set.size());
  }

  @Test public void shouldBeSubsumedByIntersectedCollections() {
    // given
    LocSet set = LocSet.intersect(Row.of(4), Block.of(5));

    // then
    assertTrue(Row.of(4).containsAll(set));
    assertTrue(Block.of(5).containsAll(set));
    assertFalse(set.containsAll(Row.of(4)));
    assertFalse(Column.of(6).containsAll(set));
    assertEquals(3, set.size());
  }

  @Test public void shouldSubsumeSubtractedCollection() {
    // given
    LocSet set = LocSet.subtract(Row.of(4), Block.of(5));

    // then
    assertTrue(Row.of(4).containsAll(set));
    assertFalse(Block.of(5).containsAll(set));
    assertFalse(set.containsAll(Row.of(4)));
    assertTrue(LocSet.intersect(set, Block.of(5)).isEmpty());
    assertEquals(9 - 3, set.size());
  }

  @Test public void shouldContainAllLocations() {
    // given
    LocSet set = LocSet.all();

    // then
    assertEquals(Location.COUNT, set.size());
    assertTrue(set.containsAll(Location.ALL));
    assertTrue(Location.ALL.containsAll(set));
    assertTrue(set.equals(new LocSet(Location.ALL)));
  }

  @Test public void shouldCloneProperly() {
    // given
    LocSet set1 = LocSet.union(Row.of(4), Block.of(5));
    LocSet set2 = set1.clone();

    // when
    set2.add(Location.of(80));

    // then
    assertEquals(set1.size() + 1, set2.size());
    assertTrue(set2.containsAll(set1));
    assertFalse(set1.containsAll(set2));
    assertFalse(set1.equals(set2));
  }

  @Test public void shouldNotContainOtherObjects() {
    // given
    LocSet set = set(0, 40, 80);

    // then
    assertFalse(set.contains(new Integer(0)));
  }

  @Test public void shouldEqualItself() {
    // given
    LocSet set1 = new LocSet();
    LocSet set2 = set(63, 64);

    // then
    assertTrue(set1.equals(set1));
    assertTrue(set2.equals(set2));
  }

  @Test public void shouldEqualAnEquivalentSet() {
    // given
    LocSet set1 = set(0, 40, 80);
    Set<Location> set2 = Sets.newHashSet(Location.of(0), Location.of(40), Location.of(80));

    // then
    assertTrue(set1.equals(set2));
    assertTrue(set2.equals(set1));
    assertEquals(set1.hashCode(), set2.hashCode());
  }

  @Test public void shouldTellWhenModified() {
    // given
    LocSet set = new LocSet();

    // then
    assertTrue(set.add(Location.of(0)));
    assertFalse(set.add(Location.of(0)));
    assertTrue(set.remove(Location.of(0)));
    assertFalse(set.remove((Object) Location.of(0)));
    assertFalse(set.remove(new Integer(123)));
  }

  @Test public void shouldContainAll() {
    // given
    LocSet row1 = new LocSet(Row.of(1));
    LocSet row9 = new LocSet(Row.of(9));
    LocSet block1 = new LocSet(Block.of(1));
    LocSet block9 = new LocSet(Block.of(9));
    LocSet row1Block1 = LocSet.intersect(row1, block1);
    LocSet row9Block9 = LocSet.intersect(row9, block9);

    // then
    assertTrue(row1.containsAll(row1Block1));
    assertTrue(block1.containsAll(row1Block1));
    assertTrue(row9.containsAll(row9Block9));
    assertTrue(block9.containsAll(row9Block9));
    assertFalse(row1Block1.containsAll(row1));
    assertFalse(row9Block9.containsAll(row9));
  }

  @Test public void shouldTellWhenModifiedInBulk() {
    // given
    LocSet set = new LocSet();
    LocSet row1 = new LocSet(Row.of(1));
    LocSet row9 = new LocSet(Row.of(9));

    // then
    assertTrue(set.addAll(row1));
    assertFalse(set.retainAll(row1));
    assertTrue(set.addAll(row9));
    assertFalse(set.addAll(row1));
    assertFalse(set.addAll(row9));
    assertTrue(set.removeAll(row1));
    assertFalse(set.retainAll(row9));
    assertTrue(set.equals(row9));
  }

  @Test public void shouldBecomeEmptyWhenCleared() {
    // given
    LocSet set = LocSet.all();

    // when
    set.clear();

    // then
    assertTrue(set.isEmpty());
    assertFalse(set.contains(Location.of(0)));
  }

  @Test(expected = NoSuchElementException.class)
  public void shouldThrowWhenIteratedPastTheEnd() {
    new LocSet().iterator().next();
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowWhenIteratorRemoveCalledTwice() {
    Iterator<Location> it = LocSet.all().iterator();
    it.next();
    it.remove();
    it.remove();
  }
}
