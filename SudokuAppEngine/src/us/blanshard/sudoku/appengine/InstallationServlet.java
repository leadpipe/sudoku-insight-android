package us.blanshard.sudoku.appengine;

import us.blanshard.sudoku.messages.InstallationInfo.UpdateRequest;
import us.blanshard.sudoku.messages.InstallationInfo.UpdateResponse;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.base.Charsets;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class InstallationServlet extends HttpServlet {

  private static final Logger logger = Logger.getLogger(InstallationServlet.class.getName());

  private static final String INSTALLATION_KIND = "Installation";

  // Property names for Installation entities:
  private static final String OPAQUE_ID = "opaqueId";
  private static final String INDEXED_ID = "indexedId";
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
    Reader in = new InputStreamReader(req.getInputStream(), Charsets.UTF_8);
    UpdateRequest body = gson.fromJson(in, UpdateRequest.class);

    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    Transaction tx = ds.beginTransaction();
    boolean update = true;
    Long opaqueId;
    try {
      Key key = KeyFactory.createKey(INSTALLATION_KIND, body.id);
      Entity entity;
      try {
        entity = ds.get(key);
        opaqueId = (Long) entity.getProperty(OPAQUE_ID);
      } catch (EntityNotFoundException e) {
        update = false;
        entity = new Entity(key);
        opaqueId = nextOpaqueId(ds);
        entity.setUnindexedProperty(OPAQUE_ID, opaqueId);
      }

      /*
      if (body.accountId == null)
        entity.removeProperty(ACCOUNT_ID);
      else
        entity.setProperty(ACCOUNT_ID, new User(body.accountId, "gmail.com"));
      if (body.name == null)
        entity.removeProperty(NAME);
      else
        entity.setUnindexedProperty(NAME, body.name);
      */

      // If sharing is acceptable, include this installation in the opaque ID
      // index.
      if (body.shareData)
        entity.setProperty(INDEXED_ID, opaqueId);
      else
        entity.removeProperty(INDEXED_ID);

      entity.setUnindexedProperty(MANUFACTURER, body.manufacturer);
      entity.setUnindexedProperty(MODEL, body.model);
      entity.setUnindexedProperty(STREAM_COUNT, body.streamCount);
      entity.setUnindexedProperty(STREAM, body.stream);

      ds.put(tx, entity);
      tx.commit();
    } finally {
      if (tx.isActive()) tx.rollback();
    }

    logger.info((update ? "Updated" : "Inserted") + " installation " + opaqueId);

    resp.setContentType("application/json");
    UpdateResponse answer = new UpdateResponse();
    answer.streamCount = body.streamCount;
    answer.stream = body.stream;
    Writer out = new OutputStreamWriter(resp.getOutputStream(), Charsets.UTF_8);
    out.write(gson.toJson(answer));
    out.flush();
  }

  private static synchronized long nextOpaqueId(DatastoreService ds) {
    if (opaqueIds == null || !opaqueIds.hasNext()) {
      opaqueIds = ds.allocateIds(INSTALLATION_KIND, 20).iterator();
    }
    return opaqueIds.next().getId();
  }
}
