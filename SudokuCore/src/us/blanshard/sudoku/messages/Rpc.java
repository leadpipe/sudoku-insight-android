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

import javax.annotation.Nullable;

/**
 * JSON-RPC request and response classes.
 * @see http://www.jsonrpc.org/specification
 */
public class Rpc {
  public static final String VERSION = "2.0";

  public static class Request<T> {
    public String jsonrpc = VERSION;
    public String method;
    @Nullable public T params;
    @Nullable public Integer id;
  }

  public static class Response<T> {
    public String jsonrpc = VERSION;
    @Nullable public T result;
    @Nullable public Error error;
    public Object id;  // JsonNull or Integer
  }

  public static class Error {
    public int code;
    public String message;
    @Nullable Object data;
  }

  public static Error error(int code, String message, @Nullable Object data) {
    Error answer = new Error();
    answer.code = code;
    answer.message = message;
    answer.data = data;
    return answer;
  }

  public static Error parseError(@Nullable Object data) {
    return error(-32700, "Parse error", data);
  }

  public static Error invalidRequest(@Nullable Object data) {
    return error(-32600, "Invalid Request", data);
  }

  public static Error methodNotFound(@Nullable Object data) {
    return error(-32601, "Method not found", data);
  }

  public static Error invalidParams(@Nullable Object data) {
    return error(-32602, "Invalid params", data);
  }

  public static Error internalError(@Nullable Object data) {
    return error(-32603, "Internal error", data);
  }
}
