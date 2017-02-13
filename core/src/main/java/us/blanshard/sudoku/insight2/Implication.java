/*
Copyright 2017 Luke Blanshard

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
package us.blanshard.sudoku.insight2;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.LocSet;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Describes an insight implied by one or more other insights.
 *
 * @author Luke Blanshard
 */
@Immutable
public final class Implication extends Insight {
  private final ImmutableSet<Insight> antecedents;
  private final Insight consequent;
  private final int cost;

  public Implication(ImmutableSet<? extends Insight> antecedents, Insight consequent) {
    super(Type.IMPLICATION, Objects.hashCode(antecedents, consequent));
    checkArgument(!antecedents.isEmpty());
    this.antecedents = ImmutableSet.copyOf(antecedents);
    this.consequent = checkNotNull(consequent);
    this.cost = calcCost();
  }

  public ImmutableSet<Insight> getAntecedents() {
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

  @Override @Nullable public Assignment getAssignment() {
    return consequent.getAssignment();
  }

  @Override public boolean isElimination() {
    return consequent.isElimination();
  }

  @Override public Collection<Assignment> getEliminations() {
    return consequent.getEliminations();
  }

  @Override public Insight getNub() {
    return consequent.getNub();
  }

  @Override public Implication asImplication() {
    return this;
  }

  @Override public int getCost() {
    return cost;
  }

  @Override protected void addAssignmentLocations(boolean includeConsequent, LocSet set) {
    for (Insight a : antecedents) {
      a.addAssignmentLocations(true, set);
    }
    consequent.addAssignmentLocations(includeConsequent, set);
  }

  @Override public String toShortString() {
    return getNub() + " \u2235 \u2026 [" + getCost() + "]";
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Implication)) return false;
    Implication that = (Implication) o;
    return this.antecedents.equals(that.antecedents)
        && this.consequent.equals(that.consequent);
  }

  @Override public String toString() {
    return consequent + " \u2235 " + antecedents;  // "because" symbol
  }
}
