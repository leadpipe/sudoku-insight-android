/*
Copyright 2016 Luke Blanshard

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
package us.blanshard.sudoku.insight2;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.concurrent.Immutable;

import us.blanshard.sudoku.core.Assignment;

/**
 * An insight marking an elimination that the user has somehow indicated they
 * wish to apply.
 *
 * @author Luke Blanshard
 */
@Immutable
public class ExplicitElimination extends Insight {

  private final Assignment elimination;

  public ExplicitElimination(Assignment elimination) {
    super(Type.EXPLICIT_ELIMINATION, elimination.hashCode() ^ ExplicitElimination.class.hashCode());
    this.elimination = elimination;
  }

  @Override public Collection<Assignment> getEliminations() {
    return Collections.singleton(elimination);
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || o.getClass() != getClass()) return false;
    ExplicitElimination that = (ExplicitElimination) o;
    return this.elimination.equals(that.elimination);
  }

  @Override public String toString() {
    return elimination.toString();
  }

  @Override protected ImmutableSet<Insight> getAntecedents(Marks marks) {
    return ImmutableSet.of();
  }
}
