package us.blanshard.sudoku.appengine;

import static com.google.appengine.api.datastore.Query.FilterOperator.GREATER_THAN;
import static us.blanshard.sudoku.appengine.RpcJson.GSON;
import static us.blanshard.sudoku.appengine.Schema.Puzzle.SOURCES;
import static us.blanshard.sudoku.appengine.Schema.Puzzle.STATS_TIMESTAMP;

import us.blanshard.sudoku.appengine.Rest.Puzzle;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class PuzzlesServlet extends HttpServlet {

  private static final Pattern EXTRA_PATH_PARTS = Pattern.compile(
      "/([.\\d]+)?");

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String pathInfo = req.getPathInfo();
    Matcher matcher = EXTRA_PATH_PARTS.matcher(pathInfo == null ? "/" : pathInfo);
    if (!matcher.matches()) {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    if (matcher.group(1) == null)
      getPuzzles(req, resp);
    else
      getPuzzle(matcher.group(1), resp);
  }

  private void getPuzzles(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Query query = new Query(Schema.Puzzle.KIND);
    String token = req.getParameter("pageToken");
    if (token != null) {
      Key key = KeyFactory.createKey(Schema.Puzzle.KIND, token);
      query.setFilter(GREATER_THAN.of(Entity.KEY_RESERVED_PROPERTY, key));
    }
    String pageSizeParam = req.getParameter("maxResults");
    int maxResults = 100;
    if (pageSizeParam != null) {
      maxResults = Integer.parseInt(pageSizeParam);
      if (maxResults <= 0 || maxResults > 1000) {
        throw new IllegalArgumentException(pageSizeParam);
      }
    }
    Rest.Puzzles answer = new Rest.Puzzles();
    answer.puzzles = Lists.newArrayList();
    for (Entity entity : ds.prepare(query).asIterable()) {
      Puzzle puzzle = getPuzzleFromEntity(entity);
      answer.puzzles.add(puzzle);
      if (answer.puzzles.size() >= maxResults) {
        answer.nextPageToken = puzzle.puzzle;
        break;
      }
    }
    resp.setContentType("application/json");
    GSON.toJson(answer, resp.getWriter());
  }

  private void getPuzzle(String puzzle, HttpServletResponse resp) throws IOException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Key key = KeyFactory.createKey(Schema.Puzzle.KIND, puzzle);
    Entity entity;
    try {
      entity = ds.get(key);
    } catch (EntityNotFoundException e) {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    Rest.Puzzle answer = getPuzzleFromEntity(entity);

    resp.setContentType("application/json");
    GSON.toJson(answer, resp.getWriter());
  }

  private Rest.Puzzle getPuzzleFromEntity(Entity entity) {
    Rest.Puzzle answer = new Rest.Puzzle();
    answer.puzzle = entity.getKey().getName();
    answer.name = (String) entity.getProperty(Schema.Puzzle.NAME);
    if (entity.hasProperty(SOURCES)) {
      @SuppressWarnings("unchecked")
      Collection<String> sources = (Collection<String>) entity.getProperty(Schema.Puzzle.SOURCES);
      answer.sources = Lists.newArrayList(sources);
    }

    if (entity.hasProperty(Schema.Puzzle.NUM_ATTEMPTS))
      answer.numAttempts = ((Number) entity.getProperty(Schema.Puzzle.NUM_ATTEMPTS)).intValue();
    if (entity.hasProperty(Schema.Puzzle.NUM_SOLUTIONS))
      answer.numSolutions = ((Number) entity.getProperty(Schema.Puzzle.NUM_SOLUTIONS)).intValue();
    if (entity.hasProperty(Schema.Puzzle.NUM_UP_VOTES))
      answer.numUpVotes = ((Number) entity.getProperty(Schema.Puzzle.NUM_UP_VOTES)).intValue();
    if (entity.hasProperty(Schema.Puzzle.NUM_DOWN_VOTES))
      answer.numDownVotes = ((Number) entity.getProperty(Schema.Puzzle.NUM_DOWN_VOTES)).intValue();

    if (entity.hasProperty(Schema.Puzzle.ELAPSED_MS_STAT))
      answer.elapsedMsStat = getStat(entity, Schema.Puzzle.ELAPSED_MS_STAT);
    if (entity.hasProperty(Schema.Puzzle.NUM_MOVES_STAT))
      answer.numMovesStat = getStat(entity, Schema.Puzzle.NUM_MOVES_STAT);
    if (entity.hasProperty(Schema.Puzzle.NUM_TRAILS_STAT))
      answer.numTrailsStat = getStat(entity, Schema.Puzzle.NUM_TRAILS_STAT);

    if (entity.hasProperty(STATS_TIMESTAMP))
      answer.statsTimestamp = ((Number) entity.getProperty(STATS_TIMESTAMP)).longValue();

    return answer;
  }

  private static Rest.Stat getStat(Entity entity, String propertyName) {
    Rest.Stat stat = new Rest.Stat();
    EmbeddedEntity e = (EmbeddedEntity) entity.getProperty(propertyName);
    stat.count = ((Number) e.getProperty(Schema.Stat.COUNT)).intValue();
    stat.min = ((Number) e.getProperty(Schema.Stat.MIN)).doubleValue();
    stat.max = ((Number) e.getProperty(Schema.Stat.MAX)).doubleValue();
    stat.mean = ((Number) e.getProperty(Schema.Stat.MEAN)).doubleValue();
    stat.stdDev = ((Number) e.getProperty(Schema.Stat.STD_DEV)).doubleValue();
    stat.var = ((Number) e.getProperty(Schema.Stat.VAR)).doubleValue();
    if (e.hasProperty(Schema.Stat.MEDIAN)) {
      stat.median = ((Number) e.getProperty(Schema.Stat.MEDIAN)).doubleValue();
      stat.q1 = ((Number) e.getProperty(Schema.Stat.Q1)).doubleValue();
      stat.q3 = ((Number) e.getProperty(Schema.Stat.Q3)).doubleValue();
    }
    return stat;
  }
}
