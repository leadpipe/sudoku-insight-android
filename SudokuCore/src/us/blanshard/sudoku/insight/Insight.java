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

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Marks;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

/**
 * A fact about a Sudoku board.  This may be a move implied by the current state
 * of the board, an impossible or illegal state, or one of several other
 * possible facts useful in solving Sudokus.  Insights may be compound, with one
 * fact implying others.
 *
 * @author Luke Blanshard
 */
public abstract class Insight {

  /**
   * All the types of insight we recognize.
   */
  public enum Type {
    CONFLICT,
    BARRED_LOCATION,
    BARRED_NUMERAL,
    FORCED_LOCATION,
    FORCED_NUMERAL,
    OVERLAP,
    LOCKED_SET,
    IMPLICATION,
    DISPROVED_ASSIGNMENT,
    ALL_TRAILS_ASSIGNMENTS;

    private static final EnumSet<Type> ERRORS =
        EnumSet.of(CONFLICT, BARRED_LOCATION, BARRED_NUMERAL);

    private static final EnumSet<Type> MOLECULES =
        EnumSet.of(IMPLICATION, DISPROVED_ASSIGNMENT, ALL_TRAILS_ASSIGNMENTS);

    public boolean isError() {
      return ERRORS.contains(this);
    }

    public boolean isAtom() {
      return !isMolecule();
    }

    public boolean isMolecule() {
      return MOLECULES.contains(this);
    }
  }

  protected final Type type;

  protected Insight(Type type) {
    this.type = type;
  }

  public Type getType() {
    return type;
  }

  /** Tells whether this insight implies an error in the board. */
  public boolean isError() {
    return type.isError();
  }

  /** The assignments this insight implies. */
  public Collection<Assignment> getAssignments() {
    return Collections.<Assignment>emptySet();
  }

  /** The assignments this insight disproves. */
  public Collection<Assignment> getEliminations() {
    return Collections.<Assignment>emptySet();
  }

  /**
   * Applies the assignments and eliminations of this insight to the given
   * builders, returns true if it doesn't uncover any errors.
   */
  public abstract boolean apply(Grid.Builder gridBuilder, Marks.Builder marksBuilder);

  /** Tells whether this insight is implied by the given grid and marks. */
  public abstract boolean isImpliedBy(GridMarks gridMarks);

  /** Tells whether this insight is related to the given elimination. */
  public abstract boolean mightBeRevealedByElimination(Assignment elimination);

  /** The constituent parts of this insight. */
  public abstract Collection<Atom> getAtoms();

  /**
   * An indivisible insight.
   */
  public abstract static class Atom extends Insight {
    protected Atom(Type type) {
      super(type);
    }

    @Override public Collection<Atom> getAtoms() {
      return Collections.singleton(this);
    }
  }

  /**
   * An insight with multiple parts.
   */
  public abstract static class Molecule extends Insight {
    protected Molecule(Type type) {
      super(type);
    }
  }
}
