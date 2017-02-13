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

import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Assignment;

import javax.annotation.concurrent.Immutable;

/**
 * A degenerate insight that wraps an actual assignment already in the grid.
 *
 * @author Luke Blanshard
 */
@Immutable
public class ExplicitAssignment extends Insight {

  private final Assignment assignment;

  public ExplicitAssignment(Assignment assignment) {
    super(Type.EXPLICIT_ASSIGNMENT, assignment.hashCode() ^ ExplicitAssignment.class.hashCode());
    this.assignment = checkNotNull(assignment);
  }

  @Override public Assignment getAssignment() {
    return assignment;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || o.getClass() != getClass()) return false;
    ExplicitAssignment that = (ExplicitAssignment) o;
    return this.assignment.equals(that.assignment);
  }

  @Override public String toString() {
    return assignment.toString();
  }

  @Override protected ImmutableSet<Insight> getAntecedents(Marks marks) {
    return ImmutableSet.of();
  }
}
