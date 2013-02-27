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
package us.blanshard.sudoku.android;

import us.blanshard.sudoku.game.GameJson;
import us.blanshard.sudoku.messages.Rpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author Luke Blanshard
 */
public class Json {
  /** An instance with all of our type adapters registered. */
  public static final Gson GSON = GameJson.register(
      Rpc.register(new GsonBuilder(), null))
      .create();
}
