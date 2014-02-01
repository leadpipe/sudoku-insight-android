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

import java.util.Comparator;

/**
 * Like {@link Comparator} but for types or situations where a total order is not
 * appropriate.
 */
public interface PartialComparator<T> {
  /**
   * Returns null if a and b are not comparable, or an int less than, equal
   * to, or greater than zero as a is less than, equal to, or greater than b.
   */
  Integer partialCompare(T a, T b);
}