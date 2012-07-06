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
package us.blanshard.sudoku.stats;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static us.blanshard.sudoku.insight.Analyzer.findErrors;
import static us.blanshard.sudoku.insight.Analyzer.findOverlapsAndSets;
import static us.blanshard.sudoku.insight.Analyzer.findSingletonLocations;
import static us.blanshard.sudoku.insight.Analyzer.findSingletonNumerals;

import us.blanshard.sudoku.core.Assignment;
import us.blanshard.sudoku.core.Grid;
import us.blanshard.sudoku.core.Marks;
import us.blanshard.sudoku.core.Solver;
import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.game.Move;
import us.blanshard.sudoku.game.Sudoku;
import us.blanshard.sudoku.insight.Analyzer;
import us.blanshard.sudoku.insight.Implication;
import us.blanshard.sudoku.insight.Insight;

import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Analyzes sudoku game histories to measure the time taken to make use of
 * different patterns and combinations of patterns.
 *
 * @author Luke Blanshard
 */
public class PatternMeasurer implements Runnable {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) exitWithUsage();

    for (String filename : args) {
      BufferedReader r;
      try {
        r = new BufferedReader(new FileReader(filename));
      } catch (IOException e) {
        System.err.printf("Unable to open file %s: %s%n", filename, e);
        continue;
      }
      String outname = filename.replaceFirst("(\\.[^.]*)?$", ".patterns$1");
      PrintWriter out;
      try {
        out = new PrintWriter(outname);
      } catch (IOException e) {
        System.err.printf("Unable to create file %s: %s%n", outname, e);
        continue;
      }
      System.err.printf("%s -> %s%n", filename, outname);
      for (String line; (line = r.readLine()) != null; ) {
        String[] fields = line.split("\\t");
        Grid puzzle = Grid.fromString(fields[0]);
        List<Move> history = GameJson.toHistory(fields[1]);
        new PatternMeasurer(puzzle, history, out).run();
        out.flush();
      }
      out.close();
    }
  }

  private static void exitWithUsage() {
    System.err.println("Usage: PatternMeasurer <filenames...>");
    System.exit(1);
  }

  private final Sudoku game;
  private final List<Move> history;
  private final PrintWriter out;
  private final Grid solution;
  private final Cache<Integer, StateState> stateStates;

  private PatternMeasurer(Grid puzzle, List<Move> history, PrintWriter out) {
    this.game = new Sudoku(puzzle, Sudoku.nullRegistry()).resume();
    this.history = history;
    this.out = out;
    this.solution = checkNotNull(Solver.solve(puzzle, new Random()).solution);
    this.stateStates = CacheBuilder.newBuilder().concurrencyLevel(1).build(
        new CacheLoader<Integer, StateState>() {
          @Override public StateState load(Integer id) {
            StateState answer = new StateState(id);
            if (id >= 0) {
              StateState base = stateStates.getUnchecked(-1);
              answer.insightArrivals.putAll(base.insightArrivals);
              answer.assignments.putAll(base.assignments);
              answer.errors.putAll(base.errors);
            }
            return answer;
          }
        });
  }

  @Override public void run() {
    stateStates.getUnchecked(-1).findInsights(0L);
    for (Move move : history) {
      stateStates.getUnchecked(move.id).applyMove(move);
    }
    for (StateState ss : stateStates.asMap().values()) {
      ss.closePendingErrors();
    }
  }

  /**
   * Returns the non-empty subsets of the given list, starting with the
   * singletons.
   */
  static class AllCombinations extends AbstractIterator<List<Insight>> {
    private final List<Insight> universe;
    private int[] indices;
    private int k;  // as in, n choose k

    public AllCombinations(List<Insight> universe) {
      this.universe = universe;
      this.indices = new int[universe.size()];
    }

    @Override protected List<Insight> computeNext() {
      if (!increment()) {
        if (k == indices.length)
          return endOfData();
        ++k;
        for (int i = 0; i < k; ++i)
          indices[i] = i;
      }
      return new AbstractList<Insight>() {
        @Override public int size() {
          return k;
        }

        @Override public Insight get(int i) {
          return universe.get(indices[i]);
        }
      };
    }

    private boolean increment() {
      for (int i = 0; i < k; ++i) {
        if (i < k - 1 && indices[i] < indices[i + 1] - 1
            || i == k - 1 && indices[i] < indices.length - 1) {
          ++indices[i];
          for (int j = 0; j < i; ++j)
            indices[j] = j;
          return true;
        }
      }
      return false;
    }
  }

  class StateState {
    final Sudoku.State state;
    final Map<Insight, Long> insightArrivals = Maps.newHashMap();
    final Multimap<Assignment, Insight> assignments = ArrayListMultimap.create();
    final Multimap<Insight, Insight> errors = ArrayListMultimap.create();
    // Used within findInsights:
    final Set<Insight> foundErrors = Sets.newHashSet();
    private Move lastMove;

    StateState(int id) {
      this.state = game.getState(id);
    }

    void findInsights(Long timestamp) {
      Grid work = state.getGrid();
      Marks.Builder builder = Marks.builder();
      boolean hasErrors = !builder.assignAll(work);

      foundErrors.clear();
      List<Insight> newInsights = Lists.newArrayList();
      Collector collector = new Collector(insightArrivals.keySet(), newInsights);
      addNewConsequentialInsights(
          work, builder.build(), hasErrors, ImmutableList.<Insight>of(), collector, collector.makeChild());

      // Handle errors cleared by this move.
      if (!errors.isEmpty()) {
        for (Iterator<Insight> it = errors.keySet().iterator(); it.hasNext(); ) {
          Insight nub = it.next();
          if (!foundErrors.contains(nub)) {
            for (Insight error : errors.get(nub))
              insightSeen(error, timestamp);
            it.remove();
          }
        }
      }

      // Record new insights.
      for (Insight insight : newInsights) {
        insightArrivals.put(insight, timestamp);
        if (insight.isError()) {
          Insight nub = insight;
          while (nub instanceof Implication)
            nub = ((Implication) nub).getConsequent();
          errors.put(nub, insight);
        } else {
          for (Assignment assignment : insight.getAssignments())
            assignments.put(assignment, insight);
        }
      }
    }

    void applyMove(Move move) {
      game.move(move);
      this.lastMove = move;
      for (Insight insight : errors.removeAll(move.getLocation())) {
        insightSeen(insight, move.timestamp);
      }
      if (move instanceof Move.Set) {
        Move.Set set = (Move.Set) lastMove;
        Assignment assignment = Assignment.of(set.loc, set.num);
        for (Insight insight : assignments.removeAll(assignment)) {
          insightSeen(insight, move.timestamp);
        }
      }
      findInsights(move.timestamp);
    }

    void closePendingErrors() {
      int index = history.indexOf(lastMove);
      Move move = (index + 1) < history.size() ? history.get(index + 1) : lastMove;
      for (Insight insight : errors.values()) {
        insightSeen(insight, move.timestamp);
      }
    }

    private class Collector implements Analyzer.Callback {
      private final Set<Insight> existing;
      private final List<Insight> seen;
      private @Nullable Set<Insight> all;

      Collector(Set<Insight> existing, List<Insight> seen) {
        this.existing = existing;
        this.seen = seen;
      }

      @Override public void phase(Analyzer.Phase phase) {}

      @Override public void take(Insight insight) {
        // Keep track of all errors found, out of band.
        if (insight.isError()) foundErrors.add(insight);

        if (!existing.contains(insight)) {
          if (all == null)
            all = Sets.newHashSet(existing);
          if (all.add(insight))
            seen.add(insight);
        }
      }

      public Set<Insight> getAllInsights() {
        return Objects.firstNonNull(all, existing);
      }

      public List<Insight> getNewInsights() {
        return seen;
      }

      public Collector makeChild() {
        return new Collector(getAllInsights(), Lists.<Insight>newArrayList());
      }
    }

    private void addNewConsequentialInsights(
        Grid work, Marks marks, boolean hasErrors, Collection<Insight> antecedents,
        Collector collector, Collector elimsCollector) {

      List<Insight> newInsights = collector.getNewInsights();
      int start = newInsights.size();

      if (hasErrors)
        findErrors(work, marks, collector);

      findSingletonLocations(work, marks, collector);
      findSingletonNumerals(work, marks, collector);

      checkArgument(elimsCollector.getNewInsights().isEmpty());
      findOverlapsAndSets(work, marks, elimsCollector);

      if (!elimsCollector.getNewInsights().isEmpty()) {
        addPostEliminationInsights(work, marks, collector, elimsCollector);
      }

      if (!antecedents.isEmpty() && newInsights.size() > start)
        for (ListIterator<Insight> it = newInsights.listIterator(start); it.hasNext(); )
          it.set(new Implication(antecedents, it.next()));
    }

    private void addPostEliminationInsights (
        Grid work, Marks marks, Collector collector, Collector elimsCollector) {

      List<Insight> elims = elimsCollector.getNewInsights();
      if (elims.size() > 10) {
        System.err.printf("Elimination-only insights: %d", elims.size());
      }

      for (Iterator<List<Insight>> it = new AllCombinations(elims); it.hasNext(); ) {
        eliminateAndAddInsights(work, marks, it.next(), collector, elimsCollector.makeChild());
      }

      if (elims.size() > 10) {
        System.err.printf("(done %d)%n", elims.size());
      }
    }

    private void eliminateAndAddInsights (
        Grid work, Marks marks, List<Insight> elims, Collector collector, Collector elimsCollector) {

      Marks.Builder builder = marks.toBuilder();
      boolean hasErrors = false;
      for (Insight elim : elims)
        for (Assignment assignment : elim.getEliminations())
          hasErrors |= !builder.eliminate(assignment);

      addNewConsequentialInsights(work, builder.build(), hasErrors, elims, collector, elimsCollector);
    }

    private void insightSeen(Insight insight, long at) {
      long arrival = insightArrivals.remove(insight);
      String patterns = Joiner.on('|').join(Ordering.natural().sortedCopy(
          Iterables.transform(insight.getPatterns(), Functions.toStringFunction())));
      out.printf("%s\t%d%n", patterns, at - arrival);
      out.flush();
    }
  }
}
