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
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Analyzes sudoku game histories to measure the time taken to make use of
 * different insights.
 *
 * <p> As of 2012-07-29, this doesn't really do anything.  I'm leaving it here
 * as a possible template for future efforts to come up with a better universe
 * of patterns for evaluating puzzles.
 *
 * @author Luke Blanshard
 */
public class InsightMeasurer implements Runnable {

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
        new InsightMeasurer(puzzle, history, out).run();
        out.flush();
      }
      out.close();
      System.err.printf("Done with %s%n", filename);
    }
  }

  private static void exitWithUsage() {
    System.err.println("Usage: InsightMeasurer <filenames...>");
    System.exit(1);
  }

  private final Sudoku game;
  private final List<Move> history;
  private final PrintWriter out;
  private final LoadingCache<Integer, StateState> stateStates;

  private InsightMeasurer(Grid puzzle, List<Move> history, PrintWriter out) {
    checkNotNull(Solver.solve(puzzle, new Random()).solution);
    this.game = new Sudoku(puzzle, Sudoku.nullRegistry()).resume();
    this.history = history;
    this.out = out;
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
      stateStates.getUnchecked(move.trailId).applyMove(move);
    }
    for (StateState ss : stateStates.asMap().values()) {
      ss.closePendingErrors();
    }
  }

  /**
   * A list that is a view into another list, and that provides {@linkplain
   * #step a way to step} itself through all subsets of the underlying list.
   * This stepping has the following properties: <ul>
   *
   * <li> It does all combinations of each size before moving on to the next
   * size.  That is, it starts out empty, then it does all the
   * singletons, then all the pairs, and so on.
   *
   * <li> It preserves the order of the underlying list.
   *
   * <li> While doing a particular size, it does all combinations of all prior
   * elements before starting to include each element in the mix.  Another way
   * to think about this is as lexicographical order of the reversed indices.
   * The assumption underlying this is that the insights being combined are in
   * order from easier to harder, and we want to exhaust the combinations of the
   * easier ones before including a harder one.  </ul>
   *
   * <p> It is possible to use this class as a subset view of another list
   * without stepping through combinations: the {@link #addIndex} method adds
   * the given index to the current view.
   *
   * <p> This class assumes that its underlying list does not change.
   */
  static class CombinationView extends AbstractList<Insight> {
    private final List<Insight> universe;
    private final int[] indices;
    private int k;  // as in, n choose k

    public CombinationView(List<Insight> universe) {
      this.universe = universe;
      this.indices = new int[universe.size()];
    }

    /**
     * Attempts to step to the next combination of elements from the universe,
     * returns true if it was possible.  To iterate all non-empty combinations,
     * call this at the top of the loop; to iterate all combinations including
     * empty, call this at the bottom of the loop.
     */
    public boolean step() {
      if (!increment()) {
        if (k == indices.length)
          return false;
        ++k;
        for (int i = 0; i < k; ++i)
          indices[i] = i;
      }
      ++modCount;
      return true;
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

    /**
     * Adds the given index to the current view of the underlying list.  The
     * given index must be larger than the current largest index included in the
     * view.
     */
    public void addIndex(int index) {
      if (index < 0 || index >= indices.length)
        throw new IndexOutOfBoundsException();
      checkArgument(k < indices.length && (k == 0 || index > indices[k - 1]));
      indices[k++] = index;
      ++modCount;
    }

    @Override public Insight get(int index) {
      if (index < 0 || index >= k)
        throw new IndexOutOfBoundsException();
      return universe.get(indices[index]);
    }

    @Override public int size() {
      return k;
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
      Grid grid = state.getGrid();
      Marks.Builder builder = Marks.builder();
      boolean hasErrors = !builder.assignAll(grid);

      foundErrors.clear();
      List<Insight> newInsights = Lists.newArrayList();
      addNewConsequentialInsights(
          grid, builder.build(), hasErrors, new Collector(insightArrivals.keySet(), newInsights));

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
        if (insightArrivals.containsKey(insight))
          continue;  // It must be an implication
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

      public List<Insight> getNewInsights() {
        return seen;
      }
    }

    private void addNewConsequentialInsights(
        Grid grid, Marks marks, boolean hasErrors, Collector collector) {

      if (hasErrors)
        findErrors(grid, marks, collector);

      findSingletonLocations(grid, marks, collector);
      findSingletonNumerals(grid, marks, collector);

      List<Insight> newInsights = collector.getNewInsights();
      int elimsStart = newInsights.size();
      findOverlapsAndSets(grid, marks, collector);

      if (newInsights.size() > elimsStart) {
        List<Insight> elimsSublist = newInsights.subList(elimsStart, newInsights.size());
        List<Insight> elims = ImmutableList.copyOf(elimsSublist);
        elimsSublist.clear();
        addPostEliminationInsights(grid, marks, elims, collector);
      }
    }

    private void addPostEliminationInsights (
        Grid grid, Marks marks, List<Insight> elims, Collector collector) {

      Grid.Builder gridBuilder = grid.toBuilder();
      Marks.Builder marksBuilder = marks.toBuilder();
      boolean ok = true;
      for (Insight elim : elims)
        ok &= elim.apply(gridBuilder, marksBuilder);

      List<Insight> newInsights = collector.getNewInsights();
      int start = newInsights.size();

      addNewConsequentialInsights(gridBuilder.build(), marksBuilder.build(), !ok, collector);

      for (ListIterator<Insight> it = newInsights.listIterator(start); it.hasNext(); ) {
        it.set(makeImplication(grid, marks, elims, it.next()));
      }
    }

    private Implication makeImplication(
        Grid grid, Marks marks, List<Insight> elims, Insight consequent) {

      // Whittle the universe of elimination insights down to those that might
      // be antecedents to the given consequent.
      CombinationView universe = new CombinationView(elims);
      for (ListIterator<Insight> it = elims.listIterator(); it.hasNext(); ) {
        if (mayBeAntecedentTo(it.next(), consequent))
          universe.addIndex(it.previousIndex());
      }

      if (universe.size() > 20) {
        System.err.printf("Checking all combinations of %d elims for %s (%s)%n",
                          universe.size(), consequent, consequent.getAtoms());
      }

      // Then find the first combination of eliminations from that universe that
      // imply the consequent and return it.
      for (CombinationView view = new CombinationView(universe); view.step(); ) {
        Grid.Builder gridBuilder = grid.toBuilder();
        Marks.Builder marksBuilder = marks.toBuilder();
        for (Insight elim : view)
          elim.apply(gridBuilder, marksBuilder);

        if (consequent.isImpliedBy(gridBuilder.build(), marksBuilder.build()))
          return new Implication(view, consequent);
      }

      // It should not be possible to reach here.
      throw new AssertionError("There's a logic error somewhere");
    }

    private boolean mayBeAntecedentTo(Insight elim, Insight consequent) {
      for (Assignment assignment : elim.getEliminations())
        if (consequent.mightBeRevealedByElimination(assignment))
          return true;
      return false;
    }

    private void insightSeen(Insight insight, long at) {
      long arrival = insightArrivals.remove(insight);
      String atoms = Joiner.on('|').join(Ordering.natural().sortedCopy(
          Iterables.transform(insight.getAtoms(), Functions.toStringFunction())));
      out.printf("%s\t%d%n", atoms, at - arrival);
      out.flush();
    }
  }
}
