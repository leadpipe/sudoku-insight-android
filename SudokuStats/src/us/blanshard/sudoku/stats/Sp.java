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
package us.blanshard.sudoku.stats;

import static java.lang.Math.max;

import us.blanshard.sudoku.core.Location;
import us.blanshard.sudoku.core.Unit;
import us.blanshard.sudoku.stats.Pattern.UnitCategory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;

import java.io.IOException;
import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * A set of classes for summarizing {@link Pattern}s.
 *
 * @author Luke Blanshard
 */
@Immutable
public abstract class Sp implements Comparable<Sp> {

  protected final Type type;

  Sp(Type type) {
    this.type = type;
  }

  public final Type getType() {
    return type;
  }

  public Appendable appendTo(Appendable a) throws IOException {
    a.append(type.getName()).append(':');
    appendGutsTo(a);
    return a;
  }

  public StringBuilder appendTo(StringBuilder sb) {
    try {
      appendTo((Appendable) sb);
      return sb;
    } catch (IOException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public static Sp fromPattern(Pattern pattern, int openCount) {
    switch (pattern.getType()) {
      case CONFLICT:
        return conflict((Pattern.Conflict) pattern);
      case BARRED_LOCATION:
        return barredLocation((Pattern.BarredLoc) pattern, openCount);
      case BARRED_NUMERAL:
        return barredNumeral((Pattern.BarredNum) pattern);
      case FORCED_LOCATION:
        return forcedLocation((Pattern.ForcedLoc) pattern);
      case FORCED_NUMERAL:
        return forcedNumeral((Pattern.ForcedNum) pattern, openCount);
      case OVERLAP:
        return overlap((Pattern.Overlap) pattern, openCount);
      case LOCKED_SET:
        return lockedSet((Pattern.LockedSet) pattern);
      case IMPLICATION:
        return implication2((Pattern.Implication) pattern, openCount);
      default:
        throw new IllegalArgumentException();
    }
  }

  public static Sp fromList(List<? extends Pattern> patterns, int openCount) {
    if (patterns.isEmpty()) return NONE;
    if (patterns.size() == 1) return fromPattern(patterns.get(0), openCount);
    return combination2(patterns, openCount);
  }

  public enum Type {
    CONFLICT(Pattern.Type.CONFLICT),
    BARRED_LOCATION(Pattern.Type.BARRED_LOCATION),
    BARRED_NUMERAL(Pattern.Type.BARRED_NUMERAL),
    FORCED_LOCATION(Pattern.Type.FORCED_LOCATION),
    FORCED_NUMERAL(Pattern.Type.FORCED_NUMERAL),
    OVERLAP(Pattern.Type.OVERLAP),
    LOCKED_SET(Pattern.Type.LOCKED_SET),
    IMPLICATION(Pattern.Type.IMPLICATION),
    IMPLICATION2(Pattern.Type.IMPLICATION),
    COMBINATION("comb"),
    COMBINATION2("comb"),
    NONE("none");

    private final String name;

    private Type(Pattern.Type patternType) {
      this.name = patternType.getName();
    }

    private Type(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  };

  @Override public final String toString() {
    StringBuilder sb = new StringBuilder();
    appendTo(sb);
    return sb.toString();
  }

  @Override public int compareTo(Sp that) {
    int answer = this.type.compareTo(that.type);
    if (answer == 0) answer = this.compareToGuts(that);
    return answer;
  }

  @Override public boolean equals(Object o) {
    if (o == null) return false;
    if (o == this) return true;
    if (o.getClass() != this.getClass()) return false;
    Sp that = (Sp) o;
    return this.type == that.type;
  }

  @Override public int hashCode() {
    return type.hashCode();
  }

  protected abstract Appendable appendGutsTo(Appendable a) throws IOException;
  protected abstract int compareToGuts(Sp that);

  /**
   * For simple patterns that rely entirely on the category of unit they pertain
   * to, a common base class.
   */
  public static abstract class UnitBased extends Sp {
    private final UnitCategory category;

    UnitBased(Type type, UnitCategory category) {
      super(type);
      this.category = category;
    }

    public UnitCategory getCategory() {
      return category;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      UnitBased that = (UnitBased) o;
      return this.category == that.category;
    }

    @Override public int hashCode() {
      return super.hashCode() ^ category.hashCode();
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return a.append(category.toString());
    }

    @Override protected int compareToGuts(Sp p) {
      UnitBased that = (UnitBased) p;
      return this.category.compareTo(that.category);
    }
  }

  /**
   * For patterns that require enumerating the numerals that affect a particular
   * location, this class counts those adjacent numerals in detail.
   */
  public static final class PeerMetrics implements Comparable<PeerMetrics> {
    /**
     * How many more assignments are in the fullest unit, compared to the
     * average number of assignments per unit overall, rounded down and clipped
     * at zero.
     */
    public final int deltaOverAverage;

    public PeerMetrics(Pattern.PeerMetrics that, int openCount) {
      int setInFullestUnit = 0;
      for (Unit.Type t : Unit.Type.values()) {
        int setInUnit = 0;
        int unitBit = Pattern.PeerMetrics.unitBit(t);
        for (int i = 0; i < 9; ++i) {
          byte cat = that.getLocationCategory(t, i);
          if ((cat & unitBit) != 0)
            ++setInUnit;
        }
        setInFullestUnit = max(setInFullestUnit, setInUnit);
      }
      int averageSetPerUnit = (Location.COUNT - openCount) / 9;
      this.deltaOverAverage = max(0, setInFullestUnit - averageSetPerUnit);
    }

    public Appendable appendTo(Appendable a) throws IOException {
      return a.append(String.valueOf(deltaOverAverage));
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      try {
        appendTo(sb);
      } catch (IOException impossible) {
        throw new AssertionError(impossible);
      }
      return sb.toString();
    }

    @Override public boolean equals(Object o) {
      if (!(o instanceof PeerMetrics)) return false;
      PeerMetrics that = (PeerMetrics) o;
      return this.deltaOverAverage == that.deltaOverAverage;
    }

    @Override public int hashCode() {
      return Objects.hashCode(deltaOverAverage);
    }

    @Override public int compareTo(PeerMetrics that) {
      return ComparisonChain.start()
          // Note reversing order:
          .compare(that.deltaOverAverage, this.deltaOverAverage)
          .result();
    }
  }

  /**
   * For patterns that rely on peer metrics, a common base class.
   */
  public static abstract class PeerMetricsBased extends Sp {
    private final PeerMetrics metrics;

    PeerMetricsBased(Type type, PeerMetrics metrics) {
      super(type);
      this.metrics = metrics;
    }

    public PeerMetrics getMetrics() {
      return metrics;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      PeerMetricsBased that = (PeerMetricsBased) o;
      return this.metrics.equals(that.metrics);
    }

    @Override public int hashCode() {
      return super.hashCode() ^ metrics.hashCode();
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return metrics.appendTo(a);
    }

    @Override protected int compareToGuts(Sp p) {
      PeerMetricsBased that = (PeerMetricsBased) p;
      return this.metrics.compareTo(that.metrics);
    }
  }

  /**
   * The patterns for an actual conflict on the board: one pattern instance per
   * unit category.
   */
  public static final class Conflict extends UnitBased {
    Conflict(UnitCategory category) {
      super(Type.CONFLICT, category);
    }
  }

  public static Conflict conflict(Pattern.Conflict conflict) {
    return new Conflict(conflict.getCategory());
  }

  /**
   * The patterns for a location with no possible assignments.
   */
  public static final class BarredLoc extends PeerMetricsBased {
    BarredLoc(PeerMetrics metrics) {
      super(Type.BARRED_LOCATION, metrics);
    }
  }

  public static BarredLoc barredLocation(Pattern.BarredLoc barredLocation, int openCount) {
    return new BarredLoc(new PeerMetrics(barredLocation.getMetrics(), openCount));
  }

  /**
   * The patterns for a numeral with no possible assignment locations left in a
   * unit.
   */
  public static final class BarredNum extends UnitBased {
    BarredNum(UnitCategory category) {
      super(Type.BARRED_NUMERAL, category);
    }
  }

  public static BarredNum barredNumeral(Pattern.BarredNum barredNumeral) {
    return new BarredNum(barredNumeral.getCategory());
  }

  /**
   * The patterns for a numeral with a single possible assignment location in a
   * given unit.
   */
  public static final class ForcedLoc extends UnitBased {
    ForcedLoc(UnitCategory category) {
      super(Type.FORCED_LOCATION, category);
    }
  }

  public static ForcedLoc forcedLocation(Pattern.ForcedLoc forcedLocation) {
    return new ForcedLoc(forcedLocation.getCategory());
  }

  /**
   * The patterns for a location with a single possible numeral assignment.
   */
  public static final class ForcedNum extends PeerMetricsBased {
    ForcedNum(PeerMetrics metrics) {
      super(Type.FORCED_NUMERAL, metrics);
    }
  }

  public static ForcedNum forcedNumeral(Pattern.ForcedNum forcedNumeral, int openCount) {
    return new ForcedNum(new PeerMetrics(forcedNumeral.getMetrics(), openCount));
  }

  /**
   * The patterns for a numeral whose only possible assignment locations in one
   * unit overlap with another unit, implicitly barring assignments from other
   * locations in the second unit.
   */
  public static final class Overlap extends UnitBased {
    Overlap(int openCount, UnitCategory category) {
      super(Type.OVERLAP, category);
    }
  }

  public static Overlap overlap(Pattern.Overlap overlap, int openCount) {
    return new Overlap(openCount, overlap.getCategory());
  }

  /**
   * The patterns for a set of locations within a unit whose only possible
   * assignments are to a set of numerals of the same size.
   */
  public static final class LockedSet extends UnitBased {
    private final int setSize;
    private final boolean isNaked;
    private final boolean isCompact;

    LockedSet(UnitCategory category, int setSize, boolean isNaked, boolean isCompact) {
      super(Type.LOCKED_SET, category);
      this.setSize = setSize;
      this.isNaked = isNaked;
      this.isCompact = isCompact;
    }

    public int getSetSize() {
      return setSize;
    }

    public boolean isNaked() {
      return isNaked;
    }

    public boolean isCompact() {
      return isCompact;
    }

    @Override public boolean equals(Object o) {
      if (super.equals(o)) {
        LockedSet that = (LockedSet) o;
        return this.setSize == that.setSize
            && this.isNaked == that.isNaked
            && this.isCompact == that.isCompact;
      }
      return false;
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), setSize, isNaked, isCompact);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return super.appendGutsTo(a)
          .append(':')
          .append(String.valueOf(setSize))
          .append(':')
          .append(isNaked ? 'n' : 'h')
          .append(':')
          .append(isCompact ? 'c' : 'd');
    }
  }

  public static LockedSet lockedSet(Pattern.LockedSet lockedSet) {
    return new LockedSet(
        lockedSet.getCategory(), lockedSet.getSetSize(), lockedSet.isNaked(), lockedSet.isCompact());
  }

  /**
   * A collection of antecedent patterns leading to a consequent pattern.
   */
  public static class Implication extends Sp {
    private final List<Sp> antecedents;
    private final Sp consequent;

    private Implication(List<Sp> antecedents, Sp consequent) {
      super(Type.IMPLICATION);
      this.antecedents = antecedents;
      this.consequent = consequent;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      Implication that = (Implication) o;
      return this.antecedents.equals(that.antecedents)
          && this.consequent.equals(that.consequent);
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), antecedents, consequent);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      Joiner.on('+').appendTo(a, antecedents).append('=');
      return consequent.appendTo(a);
    }

    @Override protected int compareToGuts(Sp p) {
      Implication that = (Implication) p;
      Ordering<Iterable<Sp>> ordering = Ordering.natural().lexicographical();
      return ComparisonChain.start()
          .compare(this.antecedents, that.antecedents, ordering)
          .compare(this.consequent, that.consequent)
          .result();
    }
  }

  public static Implication implication(Pattern.Implication implication, final int openCount) {
    return new Implication(
        Lists.transform(implication.getAntecedents(), new Function<Pattern, Sp>() {
          @Override public Sp apply(Pattern p) {
            return Sp.fromPattern(p, openCount);
          }
        }), Sp.fromPattern(implication.getConsequent(), openCount));
  }

  /**
   * A collection of antecedent types leading to a consequent type.
   */
  public static class Implication2 extends Sp {
    private final List<Type> antecedents;
    private final Type consequent;

    private Implication2(List<Type> antecedents, Type consequent) {
      super(Type.IMPLICATION2);
      this.antecedents = antecedents;
      this.consequent = consequent;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      Implication2 that = (Implication2) o;
      return this.antecedents.equals(that.antecedents)
          && this.consequent.equals(that.consequent);
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), antecedents, consequent);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      boolean one = false;
      for (Type t : antecedents) {
        if (one) a.append('+');
        else one = true;
        a.append(t.getName());
      }
      return a.append('=').append(consequent.getName());
    }

    @Override protected int compareToGuts(Sp p) {
      Implication2 that = (Implication2) p;
      Ordering<Iterable<Type>> ordering = Ordering.natural().lexicographical();
      return ComparisonChain.start()
          .compare(this.antecedents, that.antecedents, ordering)
          .compare(this.consequent, that.consequent)
          .result();
    }
  }

  public static Implication2 implication2(Pattern.Implication implication, final int openCount) {
    return new Implication2(
        Lists.transform(implication.getAntecedents(), new Function<Pattern, Type>() {
          @Override public Type apply(Pattern p) {
            return Sp.fromPattern(p, openCount).type;
          }
        }), Sp.fromPattern(implication.getConsequent(), openCount).type);
  }

  /**
   * A collection of patterns for the same assignment.
   */
  public static class Combination extends Sp {
    private final List<Sp> parts;

    private Combination(List<Sp> parts) {
      super(Type.COMBINATION);
      this.parts = parts;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      Combination that = (Combination) o;
      return this.parts.equals(that.parts);
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), parts);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return Joiner.on(';').appendTo(a, parts);
    }

    @Override protected int compareToGuts(Sp p) {
      Combination that = (Combination) p;
      Ordering<Iterable<Sp>> ordering = Ordering.natural().lexicographical();
      return ComparisonChain.start()
          .compare(this.parts, that.parts, ordering)
          .result();
    }
  }

  public static Combination combination(List<Pattern> parts, final int openCount) {
    return new Combination(
        Lists.transform(parts, new Function<Pattern, Sp>() {
          @Override public Sp apply(Pattern p) {
            return Sp.fromPattern(p, openCount);
          }
        }));
  }

  /**
   * A collection of patterns for the same assignment.
   */
  public static class Combination2 extends Sp {
    private final SortedMultiset<Type> parts;

    private Combination2(SortedMultiset<Type> parts) {
      super(Type.COMBINATION2);
      this.parts = parts;
    }

    @Override public boolean equals(Object o) {
      if (!super.equals(o)) return false;
      Combination2 that = (Combination2) o;
      return this.parts.equals(that.parts);
    }

    @Override public int hashCode() {
      return Objects.hashCode(super.hashCode(), parts);
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      return Joiner.on(';').appendTo(a, Iterables.transform(parts.entrySet(),
          new Function<Multiset.Entry<Type>, String>() {
        @Override public String apply(Multiset.Entry<Type> e) {
          if (e.getCount() == 1) return e.getElement().getName();
          return e.getElement().getName() + '*' + e.getCount();
        }
      }));
    }

    @Override protected int compareToGuts(Sp p) {
      Combination2 that = (Combination2) p;
      Ordering<Iterable<Type>> ordering = Ordering.natural().lexicographical();
      return ComparisonChain.start()
          .compare(this.parts, that.parts, ordering)
          .result();
    }
  }

  public static Combination2 combination2(List<? extends Pattern> parts, final int openCount) {
    return new Combination2(
        TreeMultiset.create(Lists.transform(parts, new Function<Pattern, Type>() {
          @Override public Type apply(Pattern p) {
            return Sp.fromPattern(p, openCount).type;
          }
        })));
  }

  /**
   * A special null Sp object used to collection information about
   * patterns that were not seen.
   */
  public static class None extends Sp {
    private None() {
      super(Type.NONE);
    }

    @Override public Appendable appendTo(Appendable a) throws IOException {
      a.append("none");
      return a;
    }

    @Override public boolean equals(Object o) {
      return o instanceof None;
    }

    @Override public int hashCode() {
      return getClass().hashCode();
    }

    @Override protected Appendable appendGutsTo(Appendable a) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override protected int compareToGuts(Sp that) {
      return 0;
    }
  }

  public static final None NONE = new None();
}
