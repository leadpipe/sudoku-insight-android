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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Location;

import com.google.common.base.Objects;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.concurrent.Immutable;

/**
 * Holds an assignment that leads to an error.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class DisprovedAssignment extends Insight {
  private final Assignment assignment;
  private final Insight resultingError;

  public DisprovedAssignment(
      Assignment assignment,
      Insight resultingError) {
    super(Type.DISPROVED_ASSIGNMENT);
    checkArgument(resultingError.isError());
    this.assignment = checkNotNull(assignment);
    this.resultingError = checkNotNull(resultingError);
  }

  public Assignment getDisprovedAssignment() {
    return assignment;
  }

  public UnfoundedAssignment getUnfoundedAssignment() {
    return new UnfoundedAssignment(assignment);
  }

  /**
   * Returns the error insight that surfaces after the assignment is made.
   */
  public Insight getResultingError() {
    return resultingError;
  }

  @Override public Collection<Assignment> getEliminations() {
    return Collections.singleton(assignment);
  }

  @Override public Insight getNub() {
    return new Implication(Collections.singleton(getUnfoundedAssignment()), resultingError.getNub());
  }

  @Override public int getDepth() {
    return 1 + resultingError.getDepth();
  }

  @Override public int getCount() {
    return 1 + resultingError.getCount();
  }
  @Override public String toShortString() {
    return getPrefix() + resultingError.toShortString();
  }

  @Override public void apply(GridMarks.Builder builder) {
    builder.eliminate(assignment);
  }

  @Override public boolean isImpliedBy(GridMarks gridMarks) {
    // Applies the assignment, then checks the resulting error is implied.
    return resultingError.isImpliedBy(gridMarks.toBuilder().assign(assignment).build());
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    return resultingError.mightBeRevealedByElimination(elimination);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DisprovedAssignment)) return false;
    DisprovedAssignment that = (DisprovedAssignment) o;
    return this.assignment.equals(that.assignment)
        && this.resultingError.equals(that.resultingError);
  }

  @Override public int hashCode() {
    return Objects.hashCode(assignment, resultingError);
  }

  @Override public String toString() {
    return getPrefix() + resultingError;
  }

  private String getPrefix() {
    String prefix = assignment.numeral.number + " \u219b "  // crossed-out right arrow
            + assignment.location + " \u2235 ";  // "because" symbol
    return prefix;
  }

  @Override public void addScanTargets(Collection<Location> locs, Collection<UnitNumeral> unitNums) {
    // This guy has nothing.
  }

  @Override public int getScanTargetCount() {
    return 0;
  }
}
