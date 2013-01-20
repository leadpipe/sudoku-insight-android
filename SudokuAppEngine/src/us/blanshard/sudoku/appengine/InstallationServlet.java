package us.blanshard.sudoku.appengine;

import us.blanshard.sudoku.messages.SetInstallation.Request;
import us.blanshard.sudoku.messages.SetInstallation.Response;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.users.User;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class InstallationServlet extends HttpServlet {

  private static Iterator<Key> numbers;

  @Override public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    resp.setContentType("text/plain");
    resp.getWriter().println("Hello, world (installation)");
  }

  @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    Gson gson = new Gson();
    Reader in = new InputStreamReader(req.getInputStream(), req.getCharacterEncoding());
    Request body = gson.fromJson(in, Request.class);

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Transaction tx = ds.beginTransaction();
    try {
      Key key = KeyFactory.createKey("Installation", body.id);
      Entity entity;
      try {
        entity = ds.get(key);
      } catch (EntityNotFoundException e) {
        entity = new Entity(key);
        entity.setProperty("number", nextNumber(ds));
      }
      if (body.accountId == null)
        entity.removeProperty("accountId");
      else
        entity.setProperty("accountId", new User(body.accountId, "gmail.com"));
      if (body.name == null)
        entity.removeProperty("name");
      else
        entity.setUnindexedProperty("name", body.name);
      entity.setUnindexedProperty("manufacturer", body.manufacturer);
      entity.setUnindexedProperty("model", body.model);
      entity.setUnindexedProperty("streamCount", body.streamCount);
      entity.setUnindexedProperty("stream", body.stream);

      tx.commit();
    } finally {
      if (tx.isActive()) tx.rollback();
    }

    resp.setContentType("application/json");
    Response answer = new Response();
    answer.streamCount = body.streamCount;
    answer.stream = body.stream;
    resp.getWriter().write(gson.toJson(answer));
  }

  private static synchronized Long nextNumber(DatastoreService ds) {
    if (numbers == null || !numbers.hasNext()) {
      numbers = ds.allocateIds("Installation", 20).iterator();
    }
    return numbers.next().getId();
  }
}
