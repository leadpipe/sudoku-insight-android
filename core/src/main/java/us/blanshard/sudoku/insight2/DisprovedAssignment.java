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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.LocSet;

import com.google.common.base.Objects;

import java.util.Collections;
import java.util.List;

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
  private final int cost;

  public DisprovedAssignment(
      Assignment assignment,
      Insight resultingError) {
    super(Type.DISPROVED_ASSIGNMENT, Objects.hashCode(assignment, resultingError));

    checkArgument(resultingError.isError());
    this.assignment = checkNotNull(assignment);
    this.resultingError = checkNotNull(resultingError);
    this.cost = calcCost();
  }

  public Assignment getDisprovedAssignment() {
    return assignment;
  }

  /**
   * Returns the error insight that surfaces after the assignment is made.
   */
  public Insight getResultingError() {
    return resultingError;
  }

  @Override public List<Assignment> getEliminations() {
    return Collections.singletonList(assignment);
  }

  @Override public int getCost() {
    return cost;
  }

  @Override protected void addAssignmentLocations(boolean includeConsequent, LocSet set) {
    set.add(assignment.location);
    resultingError.addAssignmentLocations(includeConsequent, set);
  }

  @Override public String toShortString() {
    return getPrefix() + resultingError.toShortString();
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DisprovedAssignment)) return false;
    DisprovedAssignment that = (DisprovedAssignment) o;
    return this.assignment.equals(that.assignment)
        && this.resultingError.equals(that.resultingError);
  }

  @Override public String toString() {
    return getPrefix() + resultingError;
  }

  private String getPrefix() {
    return assignment.numeral.number + " \u219b "  // crossed-out right arrow
            + assignment.location + " \u2235 ";
  }
}
