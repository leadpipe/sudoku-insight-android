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

import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.messages.InstallationRpcs;
import us.blanshard.sudoku.messages.PuzzleRpcs;
import us.blanshard.sudoku.messages.Rpc;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Type;

import javax.annotation.Nullable;

/**
 * @author Luke Blanshard
 */
public class RpcJson {

  public static final Gson GSON;
  public static final ImmutableMap<String, RpcMethod<?, ?>> METHODS;

  static {
    GsonBuilder builder = new GsonBuilder();
    GameJson.register(builder);
    Rpc.register(builder, new Function<String, Type>() {
      @Override @Nullable public Type apply(@Nullable String method) {
        return METHODS.get(method).getParamsTypeToken().getType();
      }
    }, null);
    GSON = builder.create();
  }

  static {
    ImmutableMap.Builder<String, RpcMethod<?, ?>> builder = ImmutableMap.builder();
    METHODS = builder
        .put(InstallationRpcs.UPDATE_METHOD, new InstallationUpdateMethod())
        .put(PuzzleRpcs.ATTEMPT_UPDATE_METHOD, new AttemptUpdateMethod())
        .put(PuzzleRpcs.VOTE_UPDATE_METHOD, new VoteUpdateMethod())
        .put(PuzzleRpcs.PUZZLE_GET_METHOD, new PuzzleGetMethod())
        .build();
  }
}
