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

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

import javax.annotation.Nullable;

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
    ALL_TRAILS_ASSIGNMENT;

    private static final EnumSet<Type> ERRORS =
        EnumSet.of(CONFLICT, BARRED_LOCATION, BARRED_NUMERAL);

    private static final EnumSet<Type> MOLECULES =
        EnumSet.of(IMPLICATION, DISPROVED_ASSIGNMENT, ALL_TRAILS_ASSIGNMENT);

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

  private final Grid grid;
  private final Type type;

  protected Insight(Grid grid, Type type) {
    this.grid = grid;
    this.type = type;
  }

  public Grid getGrid() {
    return grid;
  }

  public Type getType() {
    return type;
  }

  /** Tells whether this insight implies an error in the board. */
  public boolean isError() {
    return type.isError();
  }

  /** The assignment this insight implies, or null. */
  @Nullable public Assignment getAssignment() {
    return null;
  }

  /** The assignments this insight disproves. */
  public Collection<Assignment> getEliminations() {
    return Collections.<Assignment>emptySet();
  }

  /** The constituent parts of this insight. */
  public abstract Collection<Atom> getAtoms();

  /**
   * The set of patterns embodied in this insight, the union of the patterns of
   * its atoms.
   */
  public abstract Multiset<Pattern> getPatterns();

  /**
   * An indivisible insight.
   */
  public abstract static class Atom extends Insight {
    private final Pattern pattern;

    protected Atom(Grid grid, Pattern pattern) {
      super(grid, pattern.getInsightType());
      this.pattern = pattern;
    }

    /**
     * Returns the pattern that must be perceived in the board to yield this
     * insight.
     */
    public Pattern getPattern() {
      return pattern;
    }

    @Override public Collection<Atom> getAtoms() {
      return Collections.singleton(this);
    }

    @Override public Multiset<Pattern> getPatterns() {
      return ImmutableMultiset.of(pattern);
    }
  }

  /**
   * An insight with multiple parts.
   */
  public abstract static class Molecule extends Insight {
    protected Molecule(Grid grid, Type type) {
      super(grid, type);
    }
  }
}
