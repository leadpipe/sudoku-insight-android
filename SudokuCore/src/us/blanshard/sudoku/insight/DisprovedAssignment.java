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
package us.blanshard.sudoku.insight;

import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Assignment;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Collections;

/**
 * Holds an assignment that leads to an error, with the intervening assignment insights.
 *
 * @author Luke Blanshard
 */
public class DisprovedAssignment extends Insight {
  private final Assignment assignment;
  private final ImmutableCollection<Insight> impliedAssignments;
  private final Insight resultingError;

  public DisprovedAssignment(
      Assignment assignment,
      Collection<? extends Insight> impliedAssignments,
      Insight resultingError) {
    super(Type.DISPROVED_ASSIGNMENT);
    this.assignment = checkNotNull(assignment);
    this.impliedAssignments = ImmutableList.copyOf(impliedAssignments);
    this.resultingError = checkNotNull(resultingError);
  }

  @Override public Collection<Assignment> getEliminations() {
    return Collections.singleton(assignment);
  }

  @Override public void apply(GridMarks.Builder builder) {
    builder.eliminate(assignment);
  }

  @Override public boolean isImpliedBy(GridMarks gridMarks) {
    // Applies the assignment, and all the implied assignments, checking that
    // each one and the resulting error is implied along the way.
    GridMarks.Builder builder = gridMarks.toBuilder();
    builder.assign(assignment);
    for (Insight insight : impliedAssignments) {
      if (!insight.isImpliedBy(builder.build())) return false;
      builder.apply(insight);
    }
    return resultingError.isImpliedBy(builder.build());
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    for (Insight insight : impliedAssignments)
      if (insight.mightBeRevealedByElimination(elimination))
        return true;
    return resultingError.mightBeRevealedByElimination(elimination);
  }

  public Assignment getDisprovedAssignment() {
    return assignment;
  }

  /**
   * Returns a series of assignment insights that would be implied by making the
   * assignment that is being disproved.
   */
  public ImmutableCollection<Insight> getImpliedAssignments() {
    return impliedAssignments;
  }

  /**
   * Returns the error insight that surfaces after the assignments are made.
   */
  public Insight getResultingError() {
    return resultingError;
  }
}
