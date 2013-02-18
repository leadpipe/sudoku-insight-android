package us.blanshard.sudoku.appengine;

import static java.util.logging.Level.INFO;

import us.blanshard.sudoku.appengine.RpcMethod.MethodException;
import us.blanshard.sudoku.messages.Rpc;

import com.google.common.base.Charsets;
import com.google.gson.JsonIOException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class RpcServlet extends HttpServlet {

  private static final Logger logger = Logger.getLogger(RpcServlet.class.getName());

  @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    Rpc.Response<Object> rpcResponse = new Rpc.Response<Object>();
    Rpc.Request rpcRequest = null;

    try {
      Reader in = new InputStreamReader(req.getInputStream(), Charsets.UTF_8);
      rpcRequest = RpcJson.GSON.fromJson(in, Rpc.Request.class);
      logger.info("Received RPC method " + rpcRequest.method);

      try {
        @SuppressWarnings("unchecked")
        RpcMethod<Object, Object> rpcMethod =
            (RpcMethod<Object, Object>) RpcJson.METHODS.get(rpcRequest.method);
        rpcResponse.result = rpcMethod.call(rpcRequest.params);
      } catch (MethodException e) {
        logger.log(INFO, "Anticipated RPC error", e);
        rpcResponse.error = e.getError();
      } catch (Throwable t) {
        logger.log(INFO, "RPC method problem", t);
        rpcResponse.error = Rpc.internalError(t.getMessage());
      }

    } catch (JsonIOException e) {
      logger.log(INFO, "JSON problem", e);
      try {
        throw e.getCause();
      } catch (Rpc.ProblemException cause) {
        rpcResponse.error = cause.toError();
      } catch (Throwable t) {
        rpcResponse.error = Rpc.parseError(e.getMessage());
      }
    } catch (Throwable t) {
      logger.log(INFO, "JSON or I/O problem", t);
      rpcResponse.error = Rpc.parseError(t.getMessage());
    }

    if (rpcRequest != null)
      rpcResponse.id = rpcRequest.id;
    resp.setContentType("application/json");
    Writer out = new OutputStreamWriter(resp.getOutputStream(), Charsets.UTF_8);
    RpcJson.GSON.toJson(rpcResponse, out);
    out.flush();
  }
}
