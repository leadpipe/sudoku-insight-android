package us.blanshard.sudoku.appengine;

import static java.util.logging.Level.INFO;

import us.blanshard.sudoku.appengine.RpcMethod.MethodException;
import us.blanshard.sudoku.messages.Rpc;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class RpcServlet extends HttpServlet {

  private static final TypeToken<List<Rpc.Request>> BATCH_TOKEN = new TypeToken<List<Rpc.Request>>() {};
  private static final Logger logger = Logger.getLogger(RpcServlet.class.getName());

  @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    Object responseObject = null;
    List<Rpc.Request> batch = null;

    try {
      Reader reader = new InputStreamReader(req.getInputStream(), Charsets.UTF_8);
      JsonReader in = new JsonReader(reader);
      boolean singleRequest = false;
      if (in.peek() == JsonToken.BEGIN_ARRAY) {
        batch = RpcJson.GSON.fromJson(in, BATCH_TOKEN.getType());
        if (batch.isEmpty())
          throw new JsonIOException(new Rpc.InvalidRequestException("empty batch"));
        logger.info("Received RPC batch of size " + batch.size());
      } else {
        singleRequest = true;
        Rpc.Request rpcRequest = RpcJson.GSON.fromJson(in, Rpc.Request.class);
        batch = Lists.newArrayList(rpcRequest);
        logger.info("Received RPC method " + rpcRequest.method);
      }

      List<Rpc.Response<Object>> rpcResponses = Lists.newArrayListWithCapacity(batch.size());
      for (Rpc.Request rpcRequest : batch) {
        Rpc.Response<Object> rpcResponse = new Rpc.Response<Object>();
        logger.info("Calling RPC method " + rpcRequest.method);
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
        rpcResponse.id = rpcRequest.id;
        rpcResponses.add(rpcResponse);
      }

      responseObject = singleRequest ? rpcResponses.get(0) : rpcResponses;

    } catch (JsonIOException e) {
      logger.log(INFO, "JSON problem", e);
      try {
        throw e.getCause();
      } catch (Rpc.ProblemException cause) {
        responseObject = errorResponse(cause.toError());
      } catch (Throwable t) {
        responseObject = errorResponse(Rpc.parseError(e.getMessage()));
      }
    } catch (Throwable t) {
      logger.log(INFO, "JSON or I/O problem", t);
      responseObject = errorResponse(Rpc.parseError(t.getMessage()));
    }

    resp.setContentType("application/json");
    Writer out = new OutputStreamWriter(resp.getOutputStream(), Charsets.UTF_8);
    RpcJson.GSON.toJson(responseObject, out);
    out.flush();
  }

  private static Rpc.Response<Object> errorResponse(Rpc.Error error) {
    Rpc.Response<Object> answer = new Rpc.Response<Object>();
    answer.error = error;
    return answer;
  }
}
