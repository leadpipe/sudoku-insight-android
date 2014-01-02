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
package us.blanshard.sudoku.android;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Queues;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;

/**
 * A thread-safe iterable containing weak references that are automatically
 * cleaned up on iteration.
 */
public class WeakCallbackCollection<E> implements Iterable<E> {

  private final Collection<WeakReference<E>> refs = Queues.newConcurrentLinkedQueue();

  public static <E> WeakCallbackCollection<E> create() {
    return new WeakCallbackCollection<E>();
  }

  public void add(E element) {
    refs.add(new WeakReference<E>(element));
  }

  @Override public Iterator<E> iterator() {
    final Iterator<WeakReference<E>> it = refs.iterator();
    return new AbstractIterator<E>() {
      @Override protected E computeNext() {
        while (it.hasNext()) {
          E next = it.next().get();
          if (next != null) return next;
          it.remove();
        }
        return endOfData();
      }
    };
  }
}
