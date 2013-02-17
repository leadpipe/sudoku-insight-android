/*
Copyright 2013 Google Inc.

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
package us.blanshard.sudoku.messages;

import com.google.common.base.Function;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;

import javax.annotation.Nullable;

/**
 * JSON-RPC request and response classes.
 * @see http://www.jsonrpc.org/specification
 */
public class Rpc {
  public static final String VERSION = "2.0";

  public static class Request {
    public String jsonrpc = VERSION;
    public String method;  // Must precede params in the serialized form
    @Nullable public Object params;
    @Nullable public Integer id;  // We don't accept strings for IDs
  }

  public static class Response<T> {
    public String jsonrpc = VERSION;
    @Nullable public T result;
    @Nullable public Error error;
    @Nullable public Integer id;
  }

  public static class Error {
    public int code;
    public String message;
    @Nullable Object data;
  }

  // Standard error codes:
  public static final int INTERNAL_ERROR = -32603;
  public static final int INVALID_PARAMS = -32602;
  public static final int INVALID_REQUEST = -32600;
  public static final int METHOD_NOT_FOUND = -32601;
  public static final int PARSE_ERROR = -32700;

  // Extended error codes:
  public static final int AUTH_VERIFICATION_FAILED = -32000;

  public static Error error(int code, String message, @Nullable Object data) {
    Error answer = new Error();
    answer.code = code;
    answer.message = message;
    answer.data = data;
    return answer;
  }

  public static Error parseError(@Nullable Object data) {
    return error(PARSE_ERROR, "Parse error", data);
  }

  public static Error invalidRequest(@Nullable Object data) {
    return error(INVALID_REQUEST, "Invalid Request", data);
  }

  public static Error methodNotFound(@Nullable Object data) {
    return error(METHOD_NOT_FOUND, "Method not found", data);
  }

  public static Error invalidParams(@Nullable Object data) {
    return error(INVALID_PARAMS, "Invalid params", data);
  }

  public static Error internalError(@Nullable Object data) {
    return error(INTERNAL_ERROR, "Internal error", data);
  }

  public abstract static class ProblemException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    protected ProblemException(String message) {
      super(message);
    }

    public abstract Error toError();
  }

  private static class InvalidRequestException extends ProblemException {
    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
      super(message);
    }

    @Override public Error toError() {
      return invalidRequest(getMessage());
    }
  }

  private static class MethodNotFoundException extends ProblemException {
    private static final long serialVersionUID = 1L;

    public MethodNotFoundException(String message) {
      super(message);
    }

    @Override public Error toError() {
      return methodNotFound(getMessage());
    }
  }

  private static class InternalErrorException extends ProblemException {
    private static final long serialVersionUID = 1L;

    public InternalErrorException(String message) {
      super(message);
    }

    @Override public Error toError() {
      return internalError(getMessage());
    }
  }

  private static class InvalidParamsException extends ProblemException {
    private static final long serialVersionUID = 1L;

    public InvalidParamsException(String message) {
      super(message);
    }

    @Override public Error toError() {
      return invalidParams(getMessage());
    }
  }

  /**
   * Registers type adapters in the given builder so that {@link Request}
   * objects can be serialized and deserialized in a symmetric way. The
   * deserializer requires the "method" field of the request to come before the
   * "params" field, which requirement is not strictly conformant with JSON. The
   * serializer ensures that they are in that order.
   */
  public static GsonBuilder register(
      GsonBuilder builder, final Function<String, Type> methodToParamsType) {

    builder.registerTypeAdapterFactory(new TypeAdapterFactory() {

      @SuppressWarnings("unchecked")
      @Override public <T> TypeAdapter<T> create(final Gson gson, TypeToken<T> type) {
        if (type.getRawType() != Request.class) {
          return null;
        }
        TypeAdapter<Request> typeAdapter = new TypeAdapter<Request>() {
          @Override public void write(JsonWriter out, Request value) throws IOException {
            out.beginObject();
            out.name("jsonrpc").value(value.jsonrpc);
            out.name("method").value(value.method);
            if (value.params != null) {
              out.name("params");
              gson.toJson(value.params, value.params.getClass(), out);
            }
            if (value.id != null) {
              out.name("id").value(value.id);
            }
            out.endObject();
          }

          @Override public Request read(JsonReader in) throws IOException {
            Request answer = new Request();
            boolean versionFound = false;
            in.beginObject();
            while (in.hasNext()) {
              String name = in.nextName();
              if (name.equals("jsonrpc")) {
                final String version = in.nextString();
                if (version.equals(VERSION))
                  versionFound = true;
                else
                  throw new InvalidRequestException("Unrecognized JSON-RPC version " + version);
              } else if (name.equals("method")) {
                answer.method = in.nextString();
              } else if (name.equals("id")) {
                answer.id = in.nextInt();
              } else if (name.equals("params")) {
                if (answer.method == null)
                  throw new InvalidRequestException("Method must precede params");
                if (methodToParamsType == null)
                  throw new InternalErrorException("No mapping from method to params type provided");
                final Type paramsType = methodToParamsType.apply(answer.method);
                if (paramsType == null)
                  throw new MethodNotFoundException("No such method " + answer.method);
                try {
                  answer.params = gson.fromJson(in, paramsType);
                } catch (JsonSyntaxException e) {
                  throw new InvalidParamsException(e.getMessage());
                }
              } else {
                throw new InvalidRequestException("Unrecognized JSON-RPC request component " + name);
              }
            }
            in.endObject();
            if (!versionFound)
              throw new InvalidRequestException("No JSON-RPC version found");
            if (answer.method == null)
              throw new InvalidRequestException("No JSON-RPC method found");
            return answer;
          }
        };
        return (TypeAdapter<T>) typeAdapter.nullSafe();
      }
    });

    return builder;
  }
}
