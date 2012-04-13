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

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Arrays.asList;

import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.NumSet;
import us.blanshard.sudoku.core.Numeral;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.core.UnitSubset;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A set of classes that categorize insights by the pattern that must be
 * discerned in the Sudoku board to find them.  Patterns are constructed so that
 * each one may be assigned a difficulty rating.  There is a strict upper bound
 * on the number of distinct Pattern instances that can be created.  The
 * different instances of each type of pattern also form a lattice.
 *
 * @author Luke Blanshard
 */
public abstract class Pattern {

  private static final Joiner COMMA_JOINER = Joiner.on(',');

  private final Type type;
  private final Object[] vector;
  private final String id;

  Pattern(Type type, Object... vector) {
    checkArgument(type.getImplementingClass() == getClass());
    if (type.hasUnitCategory()) checkArgument(vector[0] instanceof UnitCategory);
    int intIndex = type.hasUnitCategory() ? 1 : 0;
    checkArgument(vector.length == intIndex + type.getNumIntegerDimensions());
    for (int i = intIndex; i < vector.length; ++i)
      checkArgument(vector[i] instanceof Integer);

    this.type = type;
    this.vector = vector;
    this.id = makeId(getClass(), vector);
  }

  public final Type getType() {
    return type;
  }

  public final Insight.Type getInsightType() {
    return type.getInsightType();
  }

  /** A pattern's vector is the parameters that define it, as described by its
      Type. */
  public final Object[] getVector() {
    return vector.clone();
  }

  @Override public final String toString() {
    return id;
  }

  @Override public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Pattern)) return false;
    Pattern that = (Pattern) o;
    return this.id.equals(that.id);
  }

  @Override public final int hashCode() {
    return id.hashCode();
  }

  public static Pattern fromString(String patternString) {
    // Note: we currently instantiate all patterns statically, but make no
    // guarantee that will always be true.
    Pattern p = AllPatterns.INSTANCE.map.get(patternString);
    checkNotNull(p, patternString);
    return p;
  }

  private static String makeId(Class<? extends Pattern> subclass, Object[] vector) {
    StringBuilder sb = new StringBuilder(subclass.getSimpleName()).append(':');
    COMMA_JOINER.appendTo(sb, vector);
    return sb.toString();
  }

  static IllegalArgumentException outOfRange(Class<? extends Pattern> subclass, Object... vector) {
    return new IllegalArgumentException("Argument(s) out of range: " + makeId(subclass, vector));
  }

  static int countOpenLocs(Grid grid, UnitSubset locs) {
    int numOpenLocs = 0;
    for (Location loc : locs)
      if (!grid.containsKey(loc))
        ++numOpenLocs;
    return numOpenLocs;
  }

  static boolean isImplicit(Grid grid, Location loc, Numeral num) {
    for (Location peer : loc.peers)
      if (grid.get(peer) == num) return false;
    return true;
  }

  static int countImplicit(Grid grid, NumSet nums, UnitSubset locs, int max) {
    int numImplicit = 0;
    for (Location loc : locs)
      for (Numeral num : nums)
        if (isImplicit(grid, loc, num)) {
          ++numImplicit;
          if (numImplicit >= max) return max;
        }
    return numImplicit;
  }

  /**
   * All the types of patterns we recognize.  These are almost a one-to-one
   * correspondence to the {@linkplain Insight.Atom atomic insights}.
   */
  public enum Type {
    CONFLICT(Insight.Type.CONFLICT, Conflict.class, true, 0) {
      @Override protected Pattern pattern(UnitCategory category, int[] ints) {
        return conflict(category);
      }
    },
    BARRED_LOCATION(Insight.Type.BARRED_LOCATION, BarredLoc.class, false, 3) {
      @Override protected Pattern pattern(UnitCategory category, int[] ints) {
        return barredLocation(ints[0], ints[1], ints[2]);
      }
    },
    BARRED_NUMERAL(Insight.Type.BARRED_NUMERAL, BarredNum.class, true, 2) {
      @Override protected Pattern pattern(UnitCategory category, int[] ints) {
        return barredNumeral(category, ints[0], ints[1]);
      }
    },
    LAST_LOCATION(Insight.Type.FORCED_LOCATION, LastLoc.class, true, 0) {
      @Override protected Pattern pattern(UnitCategory category, int[] ints) {
        return lastLocation(category);
      }
    },
    FORCED_LOCATION(Insight.Type.FORCED_LOCATION, ForcedLoc.class, true, 2) {
      @Override protected Pattern pattern(UnitCategory category, int[] ints) {
        return forcedLocation(category, ints[0], ints[1]);
      }
    },
    FORCED_NUMERAL(Insight.Type.FORCED_NUMERAL, ForcedNum.class, false, 3) {
      @Override protected Pattern pattern(UnitCategory category, int[] ints) {
        return forcedNumeral(ints[0], ints[1], ints[2]);
      }
    },
    OVERLAP(Insight.Type.OVERLAP, Overlap.class, true, 2) {
      @Override protected Pattern pattern(UnitCategory category, int[] ints) {
        return overlap(category, ints[0], ints[1]);
      }
    },
    NAKED_SET(Insight.Type.LOCKED_SET, NakedSet.class, true, 3) {
      @Override protected Pattern pattern(UnitCategory category, int[] ints) {
        return nakedSet(category, ints[0], ints[1], ints[2]);
      }
    },
    HIDDEN_SET(Insight.Type.LOCKED_SET, HiddenSet.class, true, 3) {
      @Override protected Pattern pattern(UnitCategory category, int[] ints) {
        return hiddenSet(category, ints[0], ints[1], ints[2]);
      }
    };

    private final Insight.Type insightType;
    private final Class<? extends Pattern> impl;
    private final boolean hasUnitCategory;
    private final int numIntegerDimensions;
    private static final ImmutableMap<String, Type> map;

    static {
      map = Maps.uniqueIndex(asList(values()), new Function<Type, String>() {
          @Override public String apply(Type type) { return type.impl.getSimpleName(); }
        });
    }

    private Type(Insight.Type insightType, Class<? extends Pattern> impl,
                 boolean hasUnitCategory, int numIntegerDimensions) {
      this.insightType = insightType;
      this.impl = impl;
      this.hasUnitCategory = hasUnitCategory;
      this.numIntegerDimensions = numIntegerDimensions;
    }

    /** The type of insight this pattern describes. */
    public final Insight.Type getInsightType() {
      return insightType;
    }

    /** The Pattern subclass that embodies this type of pattern. */
    public final Class<? extends Pattern> getImplementingClass() {
      return impl;
    }

    /** Whether this pattern differs by unit category. */
    public final boolean hasUnitCategory() {
      return hasUnitCategory;
    }

    /** The number of integer dimensions this pattern has. */
    public final int getNumIntegerDimensions() {
      return numIntegerDimensions;
    }

    /** The total number of dimensions this pattern has. */
    public final int getNumDimensions() {
      return numIntegerDimensions + (hasUnitCategory ? 1 : 0);
    }

    /** The type for the given Pattern subtype name. */
    public static Type forName(String name) {
      Type answer = map.get(name);
      checkNotNull(answer, name);
      return answer;
    }

    /** Returns the pattern instance for the given parameters. */
    public final Pattern getPattern(@Nullable UnitCategory category, int... ints) {
      checkArgument((category != null) == hasUnitCategory);
      checkArgument(ints.length == numIntegerDimensions);
      return pattern(category, ints);
    }

    protected abstract Pattern pattern(@Nullable UnitCategory category, int[] ints);
  }

  /**
   * Many patterns refer to {@linkplain Unit units} on the Sudoku board; a given
   * pattern is generally easier to see for a block than for a row or column, so
   * we differentiate them with this enumeration.
   */
  public enum UnitCategory {
    BLOCK, LINE;

    public static UnitCategory forUnit(Unit unit) {
      return unit.getType() == Unit.Type.BLOCK ? BLOCK : LINE;
    }

    private static final UnitCategory[] VALUES = values();
    public static UnitCategory value(int ordinal) {
      return VALUES[ordinal];
    }
  }

  /**
   * The patterns for an actual conflict on the board: one pattern instance per
   * unit category.
   */
  public static final class Conflict extends Pattern {
    private final UnitCategory category;

    private Conflict(UnitCategory category) {
      super(Type.CONFLICT, category);
      this.category = category;
    }

    public UnitCategory getCategory() {
      return category;
    }

    static Collection<Conflict> all() {
      return ImmutableList.of(BLOCK, LINE);
    }

    static final Conflict BLOCK = new Conflict(UnitCategory.BLOCK);
    static final Conflict LINE = new Conflict(UnitCategory.LINE);
  }

  public static Conflict conflict(UnitCategory category) {
    return checkNotNull(category) == UnitCategory.BLOCK ? Conflict.BLOCK : Conflict.LINE;
  }

  public static Conflict conflict(Unit unit) {
    return conflict(UnitCategory.forUnit(unit));
  }

  /**
   * The patterns for a location with no possible assignments.  Each instance of
   * this pattern has 3 integers: the number of numerals implicitly excluded
   * from the location; the number of numerals assigned in the location's line
   * units but not in its block; and the number of those line-only numerals that
   * are unique to the line with fewer such assignments.  The first number, of
   * implicitly excluded numerals, is capped at 2.
   */
  public static final class BarredLoc extends Pattern {
    private final int numImplicit;
    private final int numInLinesOnly;
    private final int numInMinorLineOnly;

    private BarredLoc(int numImplicit, int numInLinesOnly, int numInMinorLineOnly) {
      super(Type.BARRED_LOCATION, numImplicit, numInLinesOnly, numInMinorLineOnly);
      this.numImplicit = numImplicit;
      this.numInLinesOnly = numInLinesOnly;
      this.numInMinorLineOnly = numInMinorLineOnly;
    }

    public int getNumImplicit() {
      return numImplicit;
    }

    public int getNumInLinesOnly() {
      return numInLinesOnly;
    }

    public int getNumInMinorLineOnly() {
      return numInMinorLineOnly;
    }

    static Collection<BarredLoc> all() {
      return INSTANCES;
    }

    static final BarredLoc[][][] ARRAY = new BarredLoc[3][][];
    static final List<BarredLoc> INSTANCES;
    static {
      ImmutableList.Builder<BarredLoc> builder = ImmutableList.builder();
      for (int numImplicit = 0; numImplicit < 3; ++numImplicit) {
        int minInLinesOnly = max(0, 1 - numImplicit);
        int maxInLinesOnly = 9 - numImplicit;
        ARRAY[numImplicit] = new BarredLoc[maxInLinesOnly + 1][];
        for (int numInLinesOnly = minInLinesOnly;
             numInLinesOnly <= maxInLinesOnly; ++numInLinesOnly) {
          int minInMinorLineOnly = max(0, numInLinesOnly - 6);
          int maxInMinorLineOnly = numInLinesOnly / 2;
          ARRAY[numImplicit][numInLinesOnly] = new BarredLoc[maxInMinorLineOnly + 1];
          for (int numInMinorLineOnly = minInMinorLineOnly;
               numInMinorLineOnly <= maxInMinorLineOnly; ++numInMinorLineOnly) {
            BarredLoc instance = new BarredLoc(numImplicit, numInLinesOnly, numInMinorLineOnly);
            builder.add(instance);
            ARRAY[numImplicit][numInLinesOnly][numInMinorLineOnly] = instance;
          }
        }
      }
      INSTANCES = builder.build();
    }
  }

  public static BarredLoc barredLocation(
      int numImplicit, int numInLinesOnly, int numInMinorLineOnly) {
    BarredLoc answer = null;
    try {
      answer = BarredLoc.ARRAY[numImplicit][numInLinesOnly][numInMinorLineOnly];
    } catch (RuntimeException ignored) {}
    if (answer == null)
      throw outOfRange(BarredLoc.class, numImplicit, numInLinesOnly, numInMinorLineOnly);
    return answer;
  }

  public static BarredLoc barredLocation(Grid grid, Location loc) {
    NumSet inBlock = NumSet.of();
    for (Location blockLoc : loc.block) {
      Numeral num = grid.get(blockLoc);
      if (num != null) inBlock = inBlock.with(num);
    }
    NumSet inRowButNotBlock = NumSet.of();
    for (Location rowLoc : loc.row.subtract(loc.block)) {
      Numeral num = grid.get(rowLoc);
      if (num != null && !inBlock.contains(num)) inRowButNotBlock = inRowButNotBlock.with(num);
    }
    NumSet inColButNotBlock = NumSet.of();
    for (Location colLoc : loc.column.subtract(loc.block)) {
      Numeral num = grid.get(colLoc);
      if (num != null && !inBlock.contains(num)) inColButNotBlock = inColButNotBlock.with(num);
    }
    NumSet inMinorLineOnly = inRowButNotBlock.size() < inColButNotBlock.size()
      ? inRowButNotBlock.minus(inColButNotBlock)
      : inColButNotBlock.minus(inRowButNotBlock);
    NumSet inLinesOnly = inRowButNotBlock.or(inColButNotBlock);
    NumSet all = inBlock.or(inLinesOnly);
    return barredLocation(min(2, 9 - all.size()), inLinesOnly.size(), inMinorLineOnly.size());
  }

  /**
   * The patterns for a numeral with no possible assignment locations left in a
   * unit.  Each instance of the pattern has a unit category, the number of open
   * locations in the unit, and the number of those open locations that are only
   * implicitly barred from being assigned the numeral.  The last number is
   * capped at 2.
   */
  public static final class BarredNum extends Pattern {
    private final UnitCategory category;
    private final int numOpenLocs;
    private final int numImplicit;

    private BarredNum(UnitCategory category, int numOpenLocs, int numImplicit) {
      super(Type.BARRED_NUMERAL, category, numOpenLocs, numImplicit);
      this.category = category;
      this.numOpenLocs = numOpenLocs;
      this.numImplicit = numImplicit;
    }

    public UnitCategory getCategory() {
      return category;
    }

    public int getNumOpenLocs() {
      return numOpenLocs;
    }

    public int getNumImplicit() {
      return numImplicit;
    }

    static Collection<BarredNum> all() {
      return INSTANCES;
    }

    static final BarredNum[][][] ARRAY = new BarredNum[2][10][];
    static final List<BarredNum> INSTANCES;
    static {
      ImmutableList.Builder<BarredNum> builder = ImmutableList.builder();
      for (int cat = 0; cat < 2; ++cat)
        for (int numOpenLocs = 1; numOpenLocs <= 9; ++numOpenLocs) {
          int maxImplicit = min(2, numOpenLocs);
          ARRAY[cat][numOpenLocs] = new BarredNum[maxImplicit + 1];
          for (int numImplicit = 0; numImplicit <= maxImplicit; ++numImplicit) {
            BarredNum instance = new BarredNum(UnitCategory.value(cat), numOpenLocs, numImplicit);
            builder.add(instance);
            ARRAY[cat][numOpenLocs][numImplicit] = instance;
          }
        }
      INSTANCES = builder.build();
    }
  }

  public static BarredNum barredNumeral(UnitCategory category, int numOpenLocs, int numImplicit) {
    BarredNum answer = null;
    try {
      answer = BarredNum.ARRAY[category.ordinal()][numOpenLocs][numImplicit];
    } catch (RuntimeException ignored) {}
    if (answer == null)
      throw outOfRange(BarredNum.class, category, numOpenLocs, numImplicit);
    return answer;
  }

  public static BarredNum barredNumeral(Grid grid, Unit unit, Numeral num) {
    int numOpenLocs = 0;
    int numImplicit = 0;
    for (Location loc : unit) {
      if (!grid.containsKey(loc)) {
        ++numOpenLocs;
        if (numImplicit < 2 && isImplicit(grid, loc, num))
          ++numImplicit;
      }
    }
    return barredNumeral(UnitCategory.forUnit(unit), numOpenLocs, numImplicit);
  }

  /**
   * The patterns for a unit with a single open location: one pattern instance
   * per unit category.
   */
  public static final class LastLoc extends Pattern {
    private final UnitCategory category;

    private LastLoc(UnitCategory category) {
      super(Type.LAST_LOCATION, category);
      this.category = category;
    }

    public UnitCategory getCategory() {
      return category;
    }

    static Collection<LastLoc> all() {
      return ImmutableList.of(BLOCK, LINE);
    }

    static final LastLoc BLOCK = new LastLoc(UnitCategory.BLOCK);
    static final LastLoc LINE = new LastLoc(UnitCategory.LINE);
  }

  public static LastLoc lastLocation(UnitCategory category) {
    return category == UnitCategory.BLOCK ? LastLoc.BLOCK : LastLoc.LINE;
  }

  public static LastLoc lastLocation(Unit unit) {
    return lastLocation(UnitCategory.forUnit(unit));
  }

  /**
   * The patterns for a numeral with a single possible assignment location in a
   * given unit (except when the location is the only open location in the unit,
   * see {@link LastLoc}).  Each instance of the pattern has a unit category,
   * the number of open locations in the unit, and the number of those open
   * locations that are only implicitly barred from being assigned the numeral.
   * The last number is capped at 2.
   */
  public static final class ForcedLoc extends Pattern {
    private final UnitCategory category;
    private final int numOpenLocs;
    private final int numImplicit;

    private ForcedLoc(UnitCategory category, int numOpenLocs, int numImplicit) {
      super(Type.FORCED_LOCATION, category, numOpenLocs, numImplicit);
      this.category = category;
      this.numOpenLocs = numOpenLocs;
      this.numImplicit = numImplicit;
    }

    public UnitCategory getCategory() {
      return category;
    }

    public int getNumOpenLocs() {
      return numOpenLocs;
    }

    public int getNumImplicit() {
      return numImplicit;
    }

    static Collection<ForcedLoc> all() {
      return INSTANCES;
    }

    static final ForcedLoc[][][] ARRAY = new ForcedLoc[2][10][];
    static final List<ForcedLoc> INSTANCES;
    static {
      ImmutableList.Builder<ForcedLoc> builder = ImmutableList.builder();
      for (int cat = 0; cat < 2; ++cat)
        // Note that there must be at least 2 open locations for this pattern.
        for (int numOpenLocs = 2; numOpenLocs <= 9; ++numOpenLocs) {
          // Note that numImplicit must be strictly less than numOpenLocs for this pattern.
          int maxImplicit = min(2, numOpenLocs - 1);
          ARRAY[cat][numOpenLocs] = new ForcedLoc[maxImplicit + 1];
          for (int numImplicit = 0; numImplicit <= maxImplicit; ++numImplicit) {
            ForcedLoc instance = new ForcedLoc(UnitCategory.value(cat), numOpenLocs, numImplicit);
            builder.add(instance);
            ARRAY[cat][numOpenLocs][numImplicit] = instance;
          }
        }
      INSTANCES = builder.build();
    }
  }

  public static ForcedLoc forcedLocation(UnitCategory category, int numOpenLocs, int numImplicit) {
    ForcedLoc answer = null;
    try {
      answer = ForcedLoc.ARRAY[category.ordinal()][numOpenLocs][numImplicit];
    } catch (RuntimeException ignored) {}
    if (answer == null)
      throw outOfRange(ForcedLoc.class, category, numOpenLocs, numImplicit);
    return answer;
  }

  public static ForcedLoc forcedLocation(Grid grid, Unit unit, Numeral num) {
    int numOpenLocs = 0;
    int numImplicit = -1;  // The forced location has no peers assigned the numeral.
    for (Location loc : unit) {
      if (!grid.containsKey(loc)) {
        ++numOpenLocs;
        if (numImplicit < 2 && isImplicit(grid, loc, num))
          ++numImplicit;
      }
    }
    return forcedLocation(UnitCategory.forUnit(unit), numOpenLocs, numImplicit);
  }

  /**
   * The patterns for a location with a single possible numeral assignment
   * (except when the location is the only open location in a unit, see {@link
   * LastLoc}).  Each instance of this pattern has 3 integers: the number of
   * numerals implicitly excluded from the location; the number of numerals
   * assigned in the location's line units but not in its block; and the number
   * of those line-only numerals that are unique to the line with fewer such
   * assignments.  The first number, of implicitly excluded numerals, is capped
   * at 2.
   */
  public static final class ForcedNum extends Pattern {
    private final int numImplicit;
    private final int numInLinesOnly;
    private final int numInMinorLineOnly;

    private ForcedNum(int numImplicit, int numInLinesOnly, int numInMinorLineOnly) {
      super(Type.FORCED_NUMERAL, numImplicit, numInLinesOnly, numInMinorLineOnly);
      this.numImplicit = numImplicit;
      this.numInLinesOnly = numInLinesOnly;
      this.numInMinorLineOnly = numInMinorLineOnly;
    }

    public int getNumImplicit() {
      return numImplicit;
    }

    public int getNumInLinesOnly() {
      return numInLinesOnly;
    }

    public int getNumInMinorLineOnly() {
      return numInMinorLineOnly;
    }

    static Collection<ForcedNum> all() {
      return INSTANCES;
    }

    static final ForcedNum[][][] ARRAY = new ForcedNum[3][][];
    static final List<ForcedNum> INSTANCES;
    static {
      ImmutableList.Builder<ForcedNum> builder = ImmutableList.builder();
      for (int numImplicit = 0; numImplicit < 3; ++numImplicit) {
        int maxInLinesOnly = 8 - numImplicit;
        ARRAY[numImplicit] = new ForcedNum[maxInLinesOnly + 1][];
        for (int numInLinesOnly = 0; numInLinesOnly <= maxInLinesOnly; ++numInLinesOnly) {
          int minInMinorLineOnly = max(0, numInLinesOnly - 6);
          int maxInMinorLineOnly = numInLinesOnly / 2;
          ARRAY[numImplicit][numInLinesOnly] = new ForcedNum[maxInMinorLineOnly + 1];
          for (int numInMinorLineOnly = minInMinorLineOnly;
               numInMinorLineOnly <= maxInMinorLineOnly; ++numInMinorLineOnly) {
            ForcedNum instance = new ForcedNum(numImplicit, numInLinesOnly, numInMinorLineOnly);
            builder.add(instance);
            ARRAY[numImplicit][numInLinesOnly][numInMinorLineOnly] = instance;
          }
        }
      }
      INSTANCES = builder.build();
    }
  }

  public static ForcedNum forcedNumeral(int numImplicit, int numInLinesOnly, int numInMinorLineOnly) {
    ForcedNum answer = null;
    try {
      answer = ForcedNum.ARRAY[numImplicit][numInLinesOnly][numInMinorLineOnly];
    } catch (RuntimeException ignored) {}
    if (answer == null)
      throw outOfRange(ForcedNum.class, numImplicit, numInLinesOnly, numInMinorLineOnly);
    return answer;
  }

  public static ForcedNum forcedNumeral(Grid grid, Location loc) {
    NumSet inBlock = NumSet.of();
    for (Location blockLoc : loc.block) {
      Numeral num = grid.get(blockLoc);
      if (num != null) inBlock = inBlock.with(num);
    }
    NumSet inRowButNotBlock = NumSet.of();
    for (Location rowLoc : loc.row.subtract(loc.block)) {
      Numeral num = grid.get(rowLoc);
      if (num != null && !inBlock.contains(num)) inRowButNotBlock = inRowButNotBlock.with(num);
    }
    NumSet inColButNotBlock = NumSet.of();
    for (Location colLoc : loc.column.subtract(loc.block)) {
      Numeral num = grid.get(colLoc);
      if (num != null && !inBlock.contains(num)) inColButNotBlock = inColButNotBlock.with(num);
    }
    NumSet inMinorLineOnly = inRowButNotBlock.size() < inColButNotBlock.size()
      ? inRowButNotBlock.minus(inColButNotBlock)
      : inColButNotBlock.minus(inRowButNotBlock);
    NumSet inLinesOnly = inRowButNotBlock.or(inColButNotBlock);
    NumSet all = inBlock.or(inLinesOnly);
    return forcedNumeral(min(2, 8 - all.size()), inLinesOnly.size(), inMinorLineOnly.size());
  }

  /**
   * The patterns for a numeral whose only possible assignment locations in one
   * unit overlap with another unit, implicitly barring assignments from other
   * locations in the second unit.  Each instance of the pattern has a unit
   * category (of the first unit), the number of open locations in that unit
   * beyond the overlapping locations, and the number of those open locations
   * that are only implicitly barred from being assigned the numeral.  The last
   * number is capped at 2.
   */
  public static final class Overlap extends Pattern {
    private final UnitCategory category;
    private final int numOpenLocs;
    private final int numImplicit;

    private Overlap(UnitCategory category, int numOpenLocs, int numImplicit) {
      super(Type.OVERLAP, category, numOpenLocs, numImplicit);
      this.category = category;
      this.numOpenLocs = numOpenLocs;
      this.numImplicit = numImplicit;
    }

    public UnitCategory getCategory() {
      return category;
    }

    public int getNumOpenLocs() {
      return numOpenLocs;
    }

    public int getNumImplicit() {
      return numImplicit;
    }

    static Collection<Overlap> all() {
      return INSTANCES;
    }

    static final Overlap[][][] ARRAY = new Overlap[2][7][];
    static final List<Overlap> INSTANCES;
    static {
      ImmutableList.Builder<Overlap> builder = ImmutableList.builder();
      for (int cat = 0; cat < 2; ++cat)
        for (int numOpenLocs = 0; numOpenLocs <= 6; ++numOpenLocs) {
          int maxImplicit = min(2, numOpenLocs);
          ARRAY[cat][numOpenLocs] = new Overlap[maxImplicit + 1];
          for (int numImplicit = 0; numImplicit <= maxImplicit; ++numImplicit) {
            Overlap instance = new Overlap(UnitCategory.value(cat), numOpenLocs, numImplicit);
            builder.add(instance);
            ARRAY[cat][numOpenLocs][numImplicit] = instance;
          }
        }
      INSTANCES = builder.build();
    }
  }

  public static Overlap overlap(UnitCategory category, int numOpenLocs, int numImplicit) {
    Overlap answer = null;
    try {
      answer = Overlap.ARRAY[category.ordinal()][numOpenLocs][numImplicit];
    } catch (RuntimeException ignored) {}
    if (answer == null)
      throw outOfRange(Overlap.class, category, numOpenLocs, numImplicit);
    return answer;
  }

  public static Overlap overlap(Grid grid, Unit unit1, Unit unit2, Numeral num) {
    int numOpenLocs = 0;
    int numImplicit = 0;
    for (Location loc : unit1.subtract(unit2)) {
      if (!grid.containsKey(loc)) {
        ++numOpenLocs;
        if (numImplicit < 2 && isImplicit(grid, loc, num))
          ++numImplicit;
      }
    }
    return overlap(UnitCategory.forUnit(unit1), numOpenLocs, numImplicit);
  }

  /**
   * The patterns for a set of locations within a unit whose only possible
   * assignments are to a set of numerals of the same size.  Each instance of
   * the pattern has the unit's category, the size of the set (between 2 and 4),
   * the number of open locations in that unit outside the set, and the number
   * of numerals not in the set that are only implicitly barred from the set's
   * locations.  The last number is capped at 2.
   */
  public static final class NakedSet extends Pattern {
    private final UnitCategory category;
    private final int setSize;
    private final int numOpenLocs;
    private final int numImplicit;

    private NakedSet(UnitCategory category, int setSize, int numOpenLocs, int numImplicit) {
      super(Type.NAKED_SET, category, setSize, numOpenLocs, numImplicit);
      this.category = category;
      this.setSize = setSize;
      this.numOpenLocs = numOpenLocs;
      this.numImplicit = numImplicit;
    }

    public UnitCategory getCategory() {
      return category;
    }

    public int getSetSize() {
      return setSize;
    }

    public int getNumOpenLocs() {
      return numOpenLocs;
    }

    public int getNumImplicit() {
      return numImplicit;
    }

    static Collection<NakedSet> all() {
      return INSTANCES;
    }

    static final NakedSet[][][][] ARRAY = new NakedSet[2][5][][];
    static final List<NakedSet> INSTANCES;
    static {
      ImmutableList.Builder<NakedSet> builder = ImmutableList.builder();
      for (int cat = 0; cat < 2; ++cat)
        for (int setSize = 2; setSize <= 4; ++setSize) {
          int maxOpenLocs = 9 - setSize;
          ARRAY[cat][setSize] = new NakedSet[maxOpenLocs + 1][];
          for (int numOpenLocs = 0; numOpenLocs <= maxOpenLocs; ++numOpenLocs) {
            int maxImplicit = 2;
            ARRAY[cat][setSize][numOpenLocs] = new NakedSet[maxImplicit + 1];
            for (int numImplicit = 0; numImplicit <= maxImplicit; ++numImplicit) {
              NakedSet instance = new NakedSet(UnitCategory.value(cat), setSize, numOpenLocs, numImplicit);
              builder.add(instance);
              ARRAY[cat][setSize][numOpenLocs][numImplicit] = instance;
            }
          }
        }
      INSTANCES = builder.build();
    }
  }

  public static NakedSet nakedSet(UnitCategory category, int setSize, int numOpenLocs, int numImplicit) {
    NakedSet answer = null;
    try {
      answer = NakedSet.ARRAY[category.ordinal()][setSize][numOpenLocs][numImplicit];
    } catch (RuntimeException ignored) {}
    if (answer == null)
      throw outOfRange(NakedSet.class, category, setSize, numOpenLocs, numImplicit);
    return answer;
  }

  public static NakedSet nakedSet(Grid grid, NumSet nums, UnitSubset locs) {
    int numOpenLocs = countOpenLocs(grid, locs.not());
    int numImplicit = countImplicit(grid, nums.not(), locs, 2);
    return nakedSet(UnitCategory.forUnit(locs.unit), nums.size(), numOpenLocs, numImplicit);
  }

  /**
   * The patterns for a set of numerals whose only possible assignments within a
   * unit are to a set of locations of the same size.  Each instance of the
   * pattern has the unit's category, the size of the set (between 2 and 4), the
   * number of open locations in that unit outside the set, and the number of
   * those open locations that are only implicitly barred from being assigned
   * the numerals.  The last number is capped at 2.
   */
  public static final class HiddenSet extends Pattern {
    private final UnitCategory category;
    private final int setSize;
    private final int numOpenLocs;
    private final int numImplicit;

    private HiddenSet(UnitCategory category, int setSize, int numOpenLocs, int numImplicit) {
      super(Type.HIDDEN_SET, category, setSize, numOpenLocs, numImplicit);
      this.category = category;
      this.setSize = setSize;
      this.numOpenLocs = numOpenLocs;
      this.numImplicit = numImplicit;
    }

    public UnitCategory getCategory() {
      return category;
    }

    public int getSetSize() {
      return setSize;
    }

    public int getNumOpenLocs() {
      return numOpenLocs;
    }

    public int getNumImplicit() {
      return numImplicit;
    }

    static Collection<HiddenSet> all() {
      return INSTANCES;
    }

    static final HiddenSet[][][][] ARRAY = new HiddenSet[2][5][][];
    static final List<HiddenSet> INSTANCES;
    static {
      ImmutableList.Builder<HiddenSet> builder = ImmutableList.builder();
      for (int cat = 0; cat < 2; ++cat)
        for (int setSize = 2; setSize <= 4; ++setSize) {
          int maxOpenLocs = 9 - setSize;
          ARRAY[cat][setSize] = new HiddenSet[maxOpenLocs + 1][];
          for (int numOpenLocs = 0; numOpenLocs <= maxOpenLocs; ++numOpenLocs) {
            int maxImplicit = 2;
            ARRAY[cat][setSize][numOpenLocs] = new HiddenSet[maxImplicit + 1];
            for (int numImplicit = 0; numImplicit <= maxImplicit; ++numImplicit) {
              HiddenSet instance = new HiddenSet(UnitCategory.value(cat), setSize, numOpenLocs, numImplicit);
              builder.add(instance);
              ARRAY[cat][setSize][numOpenLocs][numImplicit] = instance;
            }
          }
        }
      INSTANCES = builder.build();
    }
  }

  public static HiddenSet hiddenSet(UnitCategory category, int setSize, int numOpenLocs, int numImplicit) {
    HiddenSet answer = null;
    try {
      answer = HiddenSet.ARRAY[category.ordinal()][setSize][numOpenLocs][numImplicit];
    } catch (RuntimeException ignored) {}
    if (answer == null)
      throw outOfRange(HiddenSet.class, category, setSize, numOpenLocs, numImplicit);
    return answer;
  }

  public static HiddenSet hiddenSet(Grid grid, NumSet nums, UnitSubset locs) {
    int numOpenLocs = countOpenLocs(grid, locs.not());
    int numImplicit = countImplicit(grid, nums, locs.not(), 2);
    return hiddenSet(UnitCategory.forUnit(locs.unit), nums.size(), numOpenLocs, numImplicit);
  }

  private enum AllPatterns {
    INSTANCE;
    private final ImmutableMap<String, Pattern> map;
    @SuppressWarnings("unchecked")
    private AllPatterns() {
      this.map = Maps.uniqueIndex(
          Iterables.concat(
              Conflict.all(),
              BarredLoc.all(),
              BarredNum.all(),
              LastLoc.all(),
              ForcedLoc.all(),
              ForcedNum.all(),
              Overlap.all(),
              NakedSet.all(),
              HiddenSet.all()),
          toStringFunction());
    }
  }
}
