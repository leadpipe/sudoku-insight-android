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

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.LocSet;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import javax.annotation.Nullable;

/**
 * A fact about a Sudoku board.  This may be a move implied by or already
 * present in the current state of the board, an impossible or illegal state, or
 * one of several other possible facts useful in solving Sudokus.  Insights may
 * be compound, with one fact implying others.
 *
 * @author Luke Blanshard
 */
public abstract class Insight {

  /**
   * All the types of insight we recognize.
   */
  public enum Type {
    // This order is relied upon by Marks.compare().
    EXPLICIT_ASSIGNMENT,
    FORCED_LOCATION,
    FORCED_NUMERAL,
    EXPLICIT_ELIMINATION,
    OVERLAP,
    LOCKED_SET,
    DISPROVED_ASSIGNMENT,
    CONFLICT,
    BARRED_LOCATION,
    BARRED_NUMERAL,
    IMPLICATION,
    ;

    private static final EnumSet<Type> ASSIGNMENTS =
        EnumSet.of(EXPLICIT_ASSIGNMENT, FORCED_LOCATION, FORCED_NUMERAL);
    private static final EnumSet<Type> ELIMINATIONS =
        EnumSet.of(EXPLICIT_ELIMINATION, OVERLAP, LOCKED_SET, DISPROVED_ASSIGNMENT);
    private static final EnumSet<Type> ERRORS =
        EnumSet.of(CONFLICT, BARRED_LOCATION, BARRED_NUMERAL);

    public boolean isAssignment() {
      return ASSIGNMENTS.contains(this);
    }

    public boolean isElimination() {
      return ELIMINATIONS.contains(this);
    }

    public boolean isError() {
      return ERRORS.contains(this);
    }
  }

  public final Type type;
  private final int hashCode;

  protected Insight(Type type, int hashCode) {
    this.type = type;
    this.hashCode = hashCode;
  }

  /** Tells whether this insight implies an assignment on the board. */
  public boolean isAssignment() {
    return type.isAssignment();
  }

  /** The assignment this insight implies, if any. */
  @Nullable public Assignment getAssignment() {
    return null;
  }

  /** Tells whether this insight implies possible assignments can be eliminated. */
  public boolean isElimination() {
    return type.isElimination();
  }

  /** The assignments this insight disproves. */
  public Collection<Assignment> getEliminations() {
    return Collections.emptyList();
  }

  /** Tells whether this insight implies an error in the board. */
  public boolean isError() {
    return type.isError();
  }

  /** Returns the "nub" of this insight, ie the ultimate consequent insight. */
  public Insight getNub() {
    return this;
  }

  /** Returns this object as an Implication if it is one, or null if not. */
  @Nullable public Implication asImplication() {
    return null;
  }

  /**
   * Returns the cost of this insight, in terms of the number of assignments
   * needed to prove it.
   */
  public int getCost() {
    return 0;  // Leaf insights are costless.
  }

  /**
   * Counts the number of different assignments that imply the current insight.
   */
  protected final int calcCost() {
    LocSet set = new LocSet();
    addAssignmentLocations(/*includeConsequent=*/false, set);
    return set.size();
  }

  /**
   * Adds the locations whose assignments imply this insight, and optionally
   * also include the location assigned by this insight (if any).
   */
  protected void addAssignmentLocations(boolean includeConsequent, LocSet set) {
    if (includeConsequent && type.isAssignment()) {
      //noinspection ConstantConditions
      set.add(getAssignment().location);
    }
  }

  /** Returns an abbreviated string form of this insight. */
  public String toShortString() {
    return toString();
  }

  /**
   * Returns the insights from the given marks that prove this insight; result
   * may be empty.  This method may throw if this insight is not in fact proven
   * by the given marks.
   */
  protected abstract ImmutableSet<Insight> getAntecedents(Marks marks);

  @Override public final int hashCode() {
    return hashCode;
  }

  @Override public abstract boolean equals(Object o);
}
