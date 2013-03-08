package us.blanshard.sudoku.appengine;

import static com.google.appengine.api.datastore.Query.FilterOperator.GREATER_THAN;
import static us.blanshard.sudoku.appengine.RpcJson.GSON;

import us.blanshard.sudoku.game.GameJson;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Text;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class InstallationsServlet extends HttpServlet {

  private static final Pattern PATH_PARTS = Pattern.compile(
      "/(?:(\\d+)(?:/(?:(puzzle)(?:/([.\\d]+)?)?)?)?)?");

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String pathInfo = req.getPathInfo();
    Matcher matcher = PATH_PARTS.matcher(pathInfo == null ? "/" : pathInfo);
    if (!matcher.matches()) {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    if (matcher.group(1) == null)
      getPublicInstallations(req, resp);
    else if (matcher.group(2) == null)
      getPublicInstallation(Long.parseLong(matcher.group(1)), req, resp);
    else if (matcher.group(3) == null)
      getInstallationPuzzles(Long.parseLong(matcher.group(1)), req, resp);
    else
      getInstallationPuzzle(Long.parseLong(matcher.group(1)), matcher.group(3), req, resp);
  }

  static void getPublicInstallations(
      HttpServletRequest req, HttpServletResponse resp) throws IOException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Query query = new Query(Schema.Installation.KIND)
        .addProjection(new PropertyProjection(Schema.Installation.INDEXED_ID, Long.class));
    String token = req.getParameter("pageToken");
    if (token != null) {
      query.setFilter(GREATER_THAN.of(Schema.Installation.INDEXED_ID, Long.valueOf(token)));
    }
    String pageSizeParam = req.getParameter("maxResults");
    int maxResults = 100;
    if (pageSizeParam != null) {
      maxResults = Integer.parseInt(pageSizeParam);
      if (maxResults <= 0 || maxResults > 1000) {
        throw new IllegalArgumentException(pageSizeParam);
      }
    }
    Rest.InstallationIds answer = new Rest.InstallationIds();
    answer.ids = Lists.newArrayList();
    for (Entity entity : ds.prepare(query).asIterable()) {
      Long id = (Long) entity.getProperty(Schema.Installation.INDEXED_ID);
      answer.ids.add(id);
      if (answer.ids.size() >= maxResults) {
        answer.nextPageToken = String.valueOf(id);
        break;
      }
    }
    resp.setContentType("application/json");
    GSON.toJson(answer, resp.getWriter());
  }

  static void getPublicInstallation(long indexedId, HttpServletRequest req,
      HttpServletResponse resp) throws IOException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Query query = new Query(Schema.Installation.KIND)
        .setFilter(FilterOperator.EQUAL.of(Schema.Installation.INDEXED_ID, indexedId));
    Entity entity = ds.prepare(query).asSingleEntity();
    Rest.Installation answer = new Rest.Installation();
    answer.id = indexedId;
    if (entity.hasProperty(Schema.Installation.ANDROID_SDK))
      answer.androidSdk = ((Number) entity.getProperty(Schema.Installation.ANDROID_SDK)).intValue();
    answer.model = (String) entity.getProperty(Schema.Installation.MODEL);
    answer.manufacturer = (String) entity.getProperty(Schema.Installation.MANUFACTURER);
    resp.setContentType("application/json");
    GSON.toJson(answer, resp.getWriter());
  }

  static void getInstallationPuzzles(long indexedId, HttpServletRequest req,
      HttpServletResponse resp) throws IOException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Query query = new Query(Schema.InstallationPuzzle.KIND)
        .addProjection(new PropertyProjection(Schema.InstallationPuzzle.PUZZLE, String.class));
    String token = req.getParameter("pageToken");
    if (token != null) {
      query.setFilter(GREATER_THAN.of(Schema.InstallationPuzzle.PUZZLE, token));
    }
    String pageSizeParam = req.getParameter("maxResults");
    int maxResults = 100;
    if (pageSizeParam != null) {
      maxResults = Integer.parseInt(pageSizeParam);
      if (maxResults <= 0 || maxResults > 1000) {
        throw new IllegalArgumentException(pageSizeParam);
      }
    }
    Rest.InstallationPuzzles answer = new Rest.InstallationPuzzles();
    answer.puzzles = Lists.newArrayList();
    for (Entity entity : ds.prepare(query).asIterable()) {
      String puzzle = (String) entity.getProperty(Schema.InstallationPuzzle.PUZZLE);
      answer.puzzles.add(puzzle);
      if (answer.puzzles.size() >= maxResults) {
        answer.nextPageToken = puzzle;
        break;
      }
    }
    resp.setContentType("application/json");
    GSON.toJson(answer, resp.getWriter());
  }

  static void getInstallationPuzzle(long indexedId, String puzzle,
      HttpServletRequest req, HttpServletResponse resp) throws IOException {
    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Query query = new Query(Schema.Installation.KIND)
        .setFilter(FilterOperator.EQUAL.of(Schema.Installation.INDEXED_ID, indexedId));
    Entity entity = ds.prepare(query).asSingleEntity();
    Key key = KeyFactory.createKey(entity.getKey(), Schema.InstallationPuzzle.KIND, puzzle);
    try {
      entity = ds.get(key);
    } catch (EntityNotFoundException e) {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    Rest.InstallationPuzzle answer = new Rest.InstallationPuzzle();
    answer.installationId = indexedId;
    answer.puzzle = puzzle;
    answer.firstAttempt = toAttempt((EmbeddedEntity) entity.getProperty(Schema.InstallationPuzzle.FIRST_ATTEMPT));
    answer.laterAttempts = Lists.newArrayList();
    @SuppressWarnings("unchecked")
    Collection<EmbeddedEntity> later = (Collection<EmbeddedEntity>) entity.getProperty(Schema.InstallationPuzzle.LATER_ATTEMPTS);
    if (later != null)
      for (EmbeddedEntity e : later)
        answer.laterAttempts.add(toAttempt(e));

    resp.setContentType("application/json");
    GSON.toJson(answer, resp.getWriter());
  }

  private static Rest.Attempt toAttempt(EmbeddedEntity entity) {
    Rest.Attempt answer = new Rest.Attempt();
    answer.moves = GameJson.toHistory(GSON, ((Text) entity.getProperty(Schema.Attempt.MOVES)).getValue());
    answer.elapsedMs = ((Number) entity.getProperty(Schema.Attempt.ELAPSED_MS)).longValue();
    answer.stopTime = ((Date) entity.getProperty(Schema.Attempt.STOP_TIME)).getTime();
    answer.won = (Boolean) entity.getProperty(Schema.Attempt.WON);
    return answer;
  }
}
