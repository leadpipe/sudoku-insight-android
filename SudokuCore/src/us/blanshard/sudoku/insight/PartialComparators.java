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

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Useful operations on {@link PartialComparator}s.
 */
public class PartialComparators {

  /**
   * Returns a partial comparator that reverses a given partial comparator.
   */
  public static <T> PartialComparator<T> reverse(final PartialComparator<T> comp) {
    return new PartialComparator<T>() {
      @Override public Integer partialCompare(T a, T b) {
        return comp.partialCompare(b, a);
      }
    };
  }

  /**
   * Updates the given set with the given value, such that the set ends up with
   * the minima of the new value and the existing values, according to the given
   * partial comparator.  For example, if the new value is bottom according to
   * the comparator, then the set will end up with just that value.  Or if the
   * new value is not comparable with any of the existing values, then the new
   * value will simply be added.  Or if the new value is greater than one or
   * more of the existing values, then the existing set will not be changed.
   */
  public static <T> void updateMinima(Set<T> minima, T value, PartialComparator<T> comp) {
    for (Iterator<T> it = minima.iterator(); it.hasNext(); ) {
      Integer c = comp.partialCompare(value, it.next());
      if (c == null) continue;
      if (c > 0) return;
      if (c < 0) it.remove();
    }
    minima.add(value);
  }

  /**
   * Returns a set containing all enum values that are less than or equal to
   * some value in the given set, according to the given comparator.
   */
  public static <T extends Enum<T>> Set<T> allLessOrEqual(
      Class<T> classToken, Set<T> set, PartialComparator<T> comp) {
    Set<T> answer = EnumSet.allOf(classToken);
    for (Iterator<T> it = answer.iterator(); it.hasNext(); ) {
      T value = it.next();
      boolean leq = false;
      for (T bound : set) {
        Integer c = comp.partialCompare(value, bound);
        if (c == null || c > 0) continue;
        leq = true;
        break;
      }
      if (!leq) it.remove();
    }
    return answer;
  }
}
