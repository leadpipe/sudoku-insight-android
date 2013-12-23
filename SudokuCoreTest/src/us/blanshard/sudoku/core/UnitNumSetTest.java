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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import org.junit.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

public class UnitNumSetTest {

  private static UnitNumSet set(int... unitNums) {
    return new UnitNumSet(Lists.transform(Ints.asList(unitNums), new Function<Integer, UnitNumeral>() {
      @Override public UnitNumeral apply(Integer i) {
        return UnitNumeral.of(i);
      }
    }));
  }

  @Test public void shouldStartEmpty() {
    // given
    UnitNumSet set = new UnitNumSet();

    // then
    assertTrue(set.isEmpty());
    assertEquals(0, set.size());
  }

  @Test public void shouldContainGivenUnitNumerals() {
    // given
    UnitNumSet set = set(0, 63, 64, 127, 128, 191, 192, 242);

    // then
    assertEquals(8, set.size());
    assertTrue(set.contains(UnitNumeral.of(0)));
    assertTrue(set.contains(UnitNumeral.of(63)));
    assertTrue(set.contains(UnitNumeral.of(64)));
    assertTrue(set.contains(UnitNumeral.of(127)));
    assertTrue(set.contains(UnitNumeral.of(128)));
    assertTrue(set.contains(UnitNumeral.of(191)));
    assertTrue(set.contains(UnitNumeral.of(192)));
    assertTrue(set.contains(UnitNumeral.of(242)));
  }

  @Test public void shouldNotContainOtherUnitNumerals() {
    // given
    UnitNumSet set = set(0, 63, 64, 127, 128, 191, 192, 242);

    // then
    assertFalse(set.contains(UnitNumeral.of(1)));
    assertFalse(set.contains(UnitNumeral.of(40)));
    assertFalse(set.contains(UnitNumeral.of(79)));
    assertFalse(set.contains(UnitNumeral.of(241)));
  }

  @Test public void shouldSubsumeUnionedCollections() {
    // given
    Collection<UnitNumeral> allRowsFive = Sets.newHashSet(UnitNumSet.of(Row.all(), Numeral.of(5)));
    Collection<UnitNumeral> row1AllNums = Sets.newHashSet(UnitNumSet.of(Row.of(1), NumSet.ALL));
    UnitNumSet set = UnitNumSet.union(allRowsFive, row1AllNums);

    // then
    assertTrue(set.containsAll(allRowsFive));
    assertTrue(set.containsAll(row1AllNums));
    assertFalse(set.contains(UnitNumeral.of(Block.of(1), Numeral.of(1))));
    assertEquals(18 - 1, set.size());
  }

  @Test public void shouldBeSubsumedByIntersectedCollections() {
    // given
    Collection<UnitNumeral> allRowsFive = Sets.newHashSet(UnitNumSet.of(Row.all(), Numeral.of(5)));
    Collection<UnitNumeral> row1AllNums = Sets.newHashSet(UnitNumSet.of(Row.of(1), NumSet.ALL));
    UnitNumSet set = UnitNumSet.intersect(allRowsFive, row1AllNums);

    // then
    assertTrue(allRowsFive.containsAll(set));
    assertTrue(row1AllNums.containsAll(set));
    assertFalse(set.containsAll(allRowsFive));
    assertEquals(1, set.size());
  }

  @Test public void shouldSubsumeSubtractedCollection() {
    // given
    Collection<UnitNumeral> allRowsFive = Sets.newHashSet(UnitNumSet.of(Row.all(), Numeral.of(5)));
    Collection<UnitNumeral> row1AllNums = Sets.newHashSet(UnitNumSet.of(Row.of(1), NumSet.ALL));
    UnitNumSet set = UnitNumSet.subtract(allRowsFive, row1AllNums);

    // then
    assertTrue(allRowsFive.containsAll(set));
    assertFalse(row1AllNums.containsAll(set));
    assertFalse(set.containsAll(allRowsFive));
    assertEquals(9 - 1, set.size());
  }

  @Test public void shouldContainAllUnitNumerals() {
    // given
    UnitNumSet set = UnitNumSet.all();

    // then
    assertEquals(UnitNumeral.COUNT, set.size());
    assertTrue(set.containsAll(UnitNumeral.all()));
    assertTrue(UnitNumeral.all().containsAll(set));
    assertTrue(set.equals(new UnitNumSet(UnitNumeral.all())));
    assertTrue(set.equals(Sets.newHashSet(UnitNumeral.all())));
  }

  @Test public void shouldCloneProperly() {
    // given
    UnitNumSet set1 = set(0, 63, 64, 127, 128, 191, 192, 242);
    UnitNumSet set2 = set1.clone();

    // when
    set2.add(UnitNumeral.of(80));

    // then
    assertEquals(set1.size() + 1, set2.size());
    assertTrue(set2.containsAll(set1));
    assertFalse(set1.containsAll(set2));
    assertFalse(set1.equals(set2));
  }

  @Test public void shouldNotContainOtherObjects() {
    // given
    UnitNumSet set = set(0, 63, 64, 127, 128, 191, 192, 242);

    // then
    assertFalse(set.contains(new Integer(0)));
  }

  @Test public void shouldEqualItself() {
    // given
    UnitNumSet set1 = new UnitNumSet();
    UnitNumSet set2 = set(63, 64);

    // then
    assertTrue(set1.equals(set1));
    assertTrue(set2.equals(set2));
  }

  @Test public void shouldEqualAnEquivalentSet() {
    // given
    UnitNumSet set1 = set(10, 80, 130, 200);
    Set<UnitNumeral> set2 = Sets.newHashSet(
        UnitNumeral.of(10), UnitNumeral.of(80), UnitNumeral.of(130), UnitNumeral.of(200));

    // then
    assertTrue(set1.equals(set2));
    assertTrue(set2.equals(set1));
    assertEquals(set1.hashCode(), set2.hashCode());
  }

  @Test public void shouldTellWhenModified() {
    // given
    UnitNumSet set = new UnitNumSet();

    // then
    assertTrue(set.add(UnitNumeral.of(0)));
    assertFalse(set.add(UnitNumeral.of(0)));
    assertTrue(set.remove(UnitNumeral.of(0)));
    assertFalse(set.remove((Object) UnitNumeral.of(0)));
    assertFalse(set.remove(new Integer(123)));
  }

  @Test public void shouldTellWhenModifiedInBulk() {
    // given
    Collection<UnitNumeral> allRowsFive = Sets.newHashSet(UnitNumSet.of(Row.all(), Numeral.of(5)));
    Collection<UnitNumeral> row1AllNums = Sets.newHashSet(UnitNumSet.of(Row.of(1), NumSet.ALL));
    UnitNumSet set = new UnitNumSet();

    // then
    assertTrue(set.addAll(allRowsFive));
    assertFalse(set.retainAll(allRowsFive));
    assertTrue(set.addAll(row1AllNums));
    assertFalse(set.addAll(allRowsFive));
    assertFalse(set.addAll(row1AllNums));
    assertTrue(set.removeAll(row1AllNums));
    assertFalse(set.retainAll(allRowsFive));
    assertTrue(set.add(UnitNumeral.of(Row.of(1), Numeral.of(5))));
    assertTrue(set.equals(allRowsFive));
  }

  @Test public void shouldBecomeEmptyWhenCleared() {
    // given
    UnitNumSet set = UnitNumSet.all();

    // when
    set.clear();

    // then
    assertTrue(set.isEmpty());
    assertFalse(set.contains(UnitNumeral.of(0)));
  }

  @Test(expected = NoSuchElementException.class)
  public void shouldThrowWhenIteratedPastTheEnd() {
    new UnitNumSet().iterator().next();
  }

  @Test(expected = IllegalStateException.class)
  public void shouldThrowWhenIteratorRemoveCalledTwice() {
    Iterator<UnitNumeral> it = UnitNumSet.all().iterator();
    it.next();
    it.remove();
    it.remove();
  }
}
