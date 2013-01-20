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

  private static final String INSTALLATION_KIND = "Installation";

  // Property names for Installation entities:
  private static final String OPAQUE_ID = "opaqueId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String NAME = "name";
  private static final String MANUFACTURER = "manufacturer";
  private static final String MODEL = "model";
  private static final String STREAM_COUNT = "streamCount";
  private static final String STREAM = "stream";

  private static Iterator<Key> opaqueIds;

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
      Key key = KeyFactory.createKey(INSTALLATION_KIND, body.id);
      Entity entity;
      try {
        entity = ds.get(key);
      } catch (EntityNotFoundException e) {
        entity = new Entity(key);
        entity.setProperty(OPAQUE_ID, nextOpaqueId(ds));
      }
      if (body.accountId == null)
        entity.removeProperty(ACCOUNT_ID);
      else
        entity.setProperty(ACCOUNT_ID, new User(body.accountId, "gmail.com"));
      if (body.name == null)
        entity.removeProperty(NAME);
      else
        entity.setUnindexedProperty(NAME, body.name);

      // If sharing is acceptable, include this installation in the opaque ID
      // index.
      if (body.shareData)
        entity.setProperty(OPAQUE_ID, entity.getProperty(OPAQUE_ID));
      else
        entity.setUnindexedProperty(OPAQUE_ID, entity.getProperty(OPAQUE_ID));

      entity.setUnindexedProperty(MANUFACTURER, body.manufacturer);
      entity.setUnindexedProperty(MODEL, body.model);
      entity.setUnindexedProperty(STREAM_COUNT, body.streamCount);
      entity.setUnindexedProperty(STREAM, body.stream);

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

  private static synchronized Long nextOpaqueId(DatastoreService ds) {
    if (opaqueIds == null || !opaqueIds.hasNext()) {
      opaqueIds = ds.allocateIds(INSTALLATION_KIND, 20).iterator();
    }
    return opaqueIds.next().getId();
  }
}
