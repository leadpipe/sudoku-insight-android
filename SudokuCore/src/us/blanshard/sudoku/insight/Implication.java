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

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Describes an insight implied by one or more other insights.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class Implication extends Insight {
  private final ImmutableList<Insight> antecedents;
  private final Insight consequent;

  public Implication(Collection<? extends Insight> antecedents, Insight consequent) {
    super(Type.IMPLICATION);
    checkArgument(!antecedents.isEmpty());
    this.antecedents = ImmutableList.copyOf(antecedents);
    this.consequent = checkNotNull(consequent);
  }

  public ImmutableList<Insight> getAntecedents() {
    return antecedents;
  }

  public Insight getConsequent() {
    return consequent;
  }

  @Override public boolean isError() {
    return consequent.isError();
  }

  @Override public boolean isAssignment() {
    return consequent.isAssignment();
  }

  @Override @Nullable public Assignment getImpliedAssignment() {
    return consequent.getImpliedAssignment();
  }

  @Override public boolean isElimination() {
    return consequent.isElimination();
  }

  @Override public List<Assignment> getEliminations() {
    return consequent.getEliminations();
  }

  @Override public Insight getNub() {
    return consequent.getNub();
  }

  @Override public Implication asImplication() {
    return this;
  }

  @Override public int getDepth() {
    return 1 + consequent.getDepth();
  }

  @Override public int getCount() {
    int count = consequent.getCount();
    for (Insight insight : antecedents)
      count += insight.getCount();
    return count;
  }

  @Override public String toShortString() {
    return getNub() + " \u2235 \u2026 [" + getDepth() + "]";
  }

  @Override public void apply(GridMarks.Builder builder) {
    for (int i = 0, c = antecedents.size(); i < c; ++i)
      antecedents.get(i).apply(builder);
    consequent.apply(builder);
  }

  @Override public boolean isImpliedBy(GridMarks gridMarks) {
    // Checks that all antecedents are implied by the given grid and marks; if
    // so, applies the antecedents to new builders and checks that the
    // consequent is implied by the resulting grid and marks.
    for (int i = 0, c = antecedents.size(); i < c; ++i)
      if (!antecedents.get(i).isImpliedBy(gridMarks)) return false;
    return consequent.isImpliedBy(gridMarks.toBuilder().apply(antecedents).build());
  }

  @Override public void apply(Marks.Builder builder) {
    for (int i = 0, c = antecedents.size(); i < c; ++i)
      antecedents.get(i).apply(builder);
    consequent.apply(builder);
  }

  @Override public boolean isImpliedBy(Marks marks) {
    // Checks that all antecedents are implied by the given marks; if
    // so, applies the antecedents to new builders and checks that the
    // consequent is implied by the resulting marks.
    for (int i = 0, c = antecedents.size(); i < c; ++i)
      if (!antecedents.get(i).isImpliedBy(marks)) return false;
    return consequent.isImpliedBy(marks.toBuilder().apply(antecedents).build());
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    for (int i = 0, c = antecedents.size(); i < c; ++i)
      if (antecedents.get(i).mightBeRevealedByElimination(elimination)) return true;
    return consequent.mightBeRevealedByElimination(elimination);
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Implication)) return false;
    Implication that = (Implication) o;
    return this.antecedents.equals(that.antecedents)
        && this.consequent.equals(that.consequent);
  }

  @Override public int hashCode() {
    return antecedents.hashCode() * 31 + consequent.hashCode();
  }

  @Override public String toString() {
    return consequent + " \u2235 " + antecedents;  // "because" symbol
  }
}
