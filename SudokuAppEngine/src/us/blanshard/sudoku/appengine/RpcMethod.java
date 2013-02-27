/*
Copyright 2013 Luke Blanshard

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
package us.blanshard.sudoku.appengine;

import static com.google.common.base.Preconditions.checkNotNull;

import us.blanshard.sudoku.messages.Rpc;

import com.google.gson.reflect.TypeToken;

/**
 * @param <P> the type of the params object for this method
 * @param <R> the type of the result object for this method
 */
public abstract class RpcMethod<P, R> {
  /** How to call this method. */
  public abstract R call(P params) throws MethodException;

  public TypeToken<P> getParamsTypeToken() {
    return paramsTypeToken;
  }

  public static class MethodException extends Exception {
    private static final long serialVersionUID = 1L;
    private final Rpc.Error error;

    public MethodException(Rpc.Error error) {
      this("", null, error);
    }

    public MethodException(String message, Rpc.Error error) {
      this(message, null, error);
    }

    public MethodException(Throwable cause, Rpc.Error error) {
      this(cause.getMessage(), cause, error);
    }

    public MethodException(String message, Throwable cause, Rpc.Error error) {
      super(message, cause);
      this.error = checkNotNull(error);
    }

    public Rpc.Error getError() {
      return error;
    }
  }

  protected final TypeToken<P> paramsTypeToken;

  protected RpcMethod(TypeToken<P> paramsTypeToken) {
    this.paramsTypeToken = paramsTypeToken;
  }
}
