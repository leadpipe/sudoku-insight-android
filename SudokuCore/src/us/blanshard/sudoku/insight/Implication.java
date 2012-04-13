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

import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.core.Grid;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;

import java.util.Collection;

/**
 * Describes an atomic insight implied by one or more other insights.
 *
 * @author Luke Blanshard
 */
public class Implication extends Insight.Molecule {
  private final ImmutableCollection<Insight> antecedents;
  private final Insight.Atom consequent;

  Implication(Grid grid, Collection<? extends Insight> antecedents, Insight.Atom consequent) {
    super(grid, Insight.Type.IMPLICATION);
    this.antecedents = ImmutableList.copyOf(antecedents);
    this.consequent = checkNotNull(consequent);
  }

  public ImmutableCollection<Insight> getAntecedents() {
    return antecedents;
  }

  public Insight.Atom getConsequent() {
    return consequent;
  }

  @Override public Collection<Insight.Atom> getAtoms() {
    ImmutableList.Builder<Insight.Atom> builder = ImmutableList.builder();
    builder.add(consequent);
    for (Insight i : antecedents)
      builder.addAll(i.getAtoms());
    return builder.build();
  }

  @Override public Multiset<Pattern> getPatterns() {
    ImmutableMultiset.Builder<Pattern> builder = ImmutableMultiset.builder();
    builder.add(consequent.getPattern());
    for (Insight i : antecedents)
      builder.addAll(i.getPatterns());
    return builder.build();
  }
}
