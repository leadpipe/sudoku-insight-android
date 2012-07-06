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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;

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
    super(Insight.Type.IMPLICATION);
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

  @Override public int appraise(Pattern.Appraiser appraiser) {
    return addAppraisals(sumAppraisals(appraiser, antecedents), consequent.appraise(appraiser));
  }

  @Override public Collection<Insight.Atom> getAtoms() {
    ImmutableList.Builder<Insight.Atom> builder = ImmutableList.builder();
    for (Insight i : antecedents)
      builder.addAll(i.getAtoms());
    builder.addAll(consequent.getAtoms());
    return builder.build();
  }

  @Override public Multiset<Pattern> getPatterns() {
    ImmutableMultiset.Builder<Pattern> builder = ImmutableMultiset.builder();
    for (Insight i : antecedents)
      builder.addAll(i.getPatterns());
    builder.addAll(consequent.getPatterns());
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
