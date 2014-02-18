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

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitNumeral;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

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
    UNFOUNDED_ASSIGNMENT;

    private static final EnumSet<Type> ERRORS =
        EnumSet.of(CONFLICT, BARRED_LOCATION, BARRED_NUMERAL);
    private static final EnumSet<Type> ASSIGNMENTS =
        EnumSet.of(FORCED_LOCATION, FORCED_NUMERAL, UNFOUNDED_ASSIGNMENT);
    private static final EnumSet<Type> ELIMINATIONS =
        EnumSet.of(OVERLAP, LOCKED_SET, DISPROVED_ASSIGNMENT);

    public boolean isError() {
      return ERRORS.contains(this);
    }

    public boolean isAssignment() {
      return ASSIGNMENTS.contains(this);
    }

    public boolean isElimination() {
      return ELIMINATIONS.contains(this);
    }
  }

  /**
   * A basic categorization of simple insights, combined for compound ones.
   */
  public enum Realm {
    BLOCK, LINE, LOCATION;
    public final int bit = 1 << ordinal();

    /** Tells whether this realm is included in the given bit vector. */
    public boolean isIn(int vector) {
      return (vector & bit) != 0;
    }

    public static Realm of(Unit unit) {
      return unit.getType() == Unit.Type.BLOCK ? BLOCK : LINE;
    }
  }

  public final Type type;

  protected Insight(Type type) {
    this.type = type;
  }

  /** Tells whether this insight implies an error in the board. */
  public boolean isError() {
    return type.isError();
  }

  /** Tells whether this insight implies an assignment on the board. */
  public boolean isAssignment() {
    return type.isAssignment();
  }

  /** The assignment this insight implies, if any. */
  @Nullable public Assignment getImpliedAssignment() {
    return null;
  }

  /** Tells whether this insight implies possible assignments can be eliminated. */
  public boolean isElimination() {
    return type.isElimination();
  }

  /** The assignments this insight disproves. */
  public List<Assignment> getEliminations() {
    return Collections.<Assignment>emptyList();
  }

  /** Returns the "nub" of this insight, ie the ultimate consequent insight. */
  public Insight getNub() {
    return this;
  }

  /** Returns this object as an Implication if it is one, or null if not. */
  @Nullable public Implication asImplication() {
    return null;
  }

  /** Returns the number of levels of implication this insight embodies. */
  public int getDepth() {
    return 0;
  }

  /** Returns the number of individual insights contained in this insight. */
  public int getCount() {
    return 1;
  }

  /**
   * Returns the set of realms that this insight inhabits, expressed as a bit
   * vector.
   */
  public abstract int getRealmVector();

  /** Returns an abbreviated string form of this insight. */
  public String toShortString() {
    return toString();
  }

  /**
   * Applies the assignments and eliminations of this insight to the given
   * builder.
   */
  public abstract void apply(GridMarks.Builder builder);

  /** Tells whether this insight is implied by the given grid and marks. */
  public abstract boolean isImpliedBy(GridMarks gridMarks);

  /** Tells whether this insight is related to the given elimination. */
  public abstract boolean mightBeRevealedByElimination(Assignment elimination);

  /**
   * Adds the "scan targets" needed to discover this insight to the given
   * collections. There is one target for each location, and one for each
   * unit-numeral pair. The location targets represent the constraint that there
   * can be at most one occurrence of the numeral assigned to a location among
   * all the units that the location belongs to. The unit-numeral targets
   * represent the constraint that there must be at least one occurrence of each
   * numeral within each unit.
   */
  public abstract void addScanTargets(Collection<Location> locs, Collection<UnitNumeral> unitNums);

  /**
   * Returns the total number of scan targets needed to discover this insight.
   */
  public abstract int getScanTargetCount();
}
