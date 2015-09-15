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
package us.blanshard.sudoku.insight;

import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Assignment;

import javax.annotation.concurrent.Immutable;

/**
 * A pseudo-insight: an assignment without any justification.
 *
 * @author Luke Blanshard
 */
@Immutable
public class UnfoundedAssignment extends Insight {

  private final Assignment assignment;

  public UnfoundedAssignment(Assignment assignment) {
    super(Type.UNFOUNDED_ASSIGNMENT);
    this.assignment = checkNotNull(assignment);
  }

  @Override public Assignment getImpliedAssignment() {
    return assignment;
  }

  @Override public void apply(GridMarks.Builder builder) {
    builder.assign(assignment);
  }

  @Override public boolean isImpliedBy(GridMarks gridMarks) {
    return false;
  }

  @Override public void apply(Marks.Builder builder) {
    builder.assign(assignment);
  }

  @Override public boolean isImpliedBy(Marks marks) {
    return false;
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    return false;
  }

  @Override public int hashCode() {
    return assignment.hashCode();
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || o.getClass() != getClass()) return false;
    UnfoundedAssignment that = (UnfoundedAssignment) o;
    return this.assignment.equals(that.assignment);
  }

  @Override public String toString() {
    return assignment + "?";
  }
}
