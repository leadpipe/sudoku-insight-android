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

import static com.google.common.base.Preconditions.checkArgument;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Marks;

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;

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
  public abstract boolean isImpliedBy(Grid grid, Marks marks);

  /**
   * Returns a cumulative appraisal of this insight by summing the appraisals of
   * its constituent patterns.
   */
  public abstract int appraise(Pattern.Appraiser appraiser);

  /**
   * A helper method that checks its inputs are positive and pins its output to
   * Integer.MAX_VALUE if need be.
   */
  public static int addAppraisals(int a1, int a2) {
    checkArgument(a1 > 0);
    checkArgument(a2 > 0);
    int sum = a1 + a2;
    return sum < 0 ? Integer.MAX_VALUE : sum;
  }

  /** Returns the sum of the appraisals of the given insights. */
  public static int sumAppraisals(Pattern.Appraiser appraiser, Collection<? extends Insight> insights) {
    int answer = 0;
    for (Insight i : insights) {
      int appraisal = i.appraise(appraiser);
      answer = (answer == 0) ? appraisal : addAppraisals(answer, appraisal);
    }
    return answer;
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
    protected final Pattern pattern;

    protected Atom(Pattern pattern) {
      super(pattern.getInsightType());
      this.pattern = pattern;
    }

    /**
     * Returns the pattern that must be perceived in the board to yield this
     * insight.
     */
    public Pattern getPattern() {
      return pattern;
    }

    @Override public int appraise(Pattern.Appraiser appraiser) {
      return appraiser.appraise(pattern);
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
    protected Molecule(Type type) {
      super(type);
    }
  }
}
