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
import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Marks;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * Describes an insight implied by one or more other insights.
 *
 * @author Luke Blanshard
 */
@Immutable
public class Implication extends Insight.Molecule {
  private final ImmutableCollection<Insight> antecedents;
  private final Insight consequent;

  public Implication(Collection<? extends Insight> antecedents, Insight consequent) {
    super(Type.IMPLICATION);
    checkArgument(!antecedents.isEmpty());
    this.antecedents = ImmutableList.copyOf(antecedents);
    this.consequent = checkNotNull(consequent);
  }

  @Override public boolean isError() {
    return consequent.isError();
  }

  @Override public Collection<Assignment> getAssignments() {
    return consequent.getAssignments();
  }

  @Override public Collection<Assignment> getEliminations() {
    return consequent.getEliminations();
  }

  public ImmutableCollection<Insight> getAntecedents() {
    return antecedents;
  }

  public Insight getConsequent() {
    return consequent;
  }

  @Override public boolean apply(Grid.Builder gridBuilder, Marks.Builder marksBuilder) {
    boolean ok = true;
    for (Insight insight : antecedents)
      ok &= insight.apply(gridBuilder, marksBuilder);
    ok &= consequent.apply(gridBuilder, marksBuilder);
    return ok;
  }

  @Override public boolean isImpliedBy(GridMarks gridMarks) {
    // Checks that all antecedents are implied by the given grid and marks; if
    // so, applies the antecedents to new builders and checks that the
    // consequent is implied by the resulting grid and marks.
    for (Insight insight : antecedents)
      if (!insight.isImpliedBy(gridMarks)) return false;
    return consequent.isImpliedBy(gridMarks.toBuilder().apply(antecedents).build());
  }

  @Override public boolean mightBeRevealedByElimination(Assignment elimination) {
    for (Insight insight : antecedents)
      if (insight.mightBeRevealedByElimination(elimination)) return true;
    return consequent.mightBeRevealedByElimination(elimination);
  }

  @Override public Collection<Insight.Atom> getAtoms() {
    ImmutableList.Builder<Insight.Atom> builder = ImmutableList.builder();
    for (Insight i : antecedents)
      builder.addAll(i.getAtoms());
    builder.addAll(consequent.getAtoms());
    return builder.build();
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Implication)) return false;
    Implication that = (Implication) o;
    return this.antecedents.equals(that.antecedents)
        && this.consequent.equals(that.consequent);
  }

  @Override public int hashCode() {
    return Objects.hashCode(antecedents, consequent);
  }
}
