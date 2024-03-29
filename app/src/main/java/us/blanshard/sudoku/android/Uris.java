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

/**
 * Constants and methods related to URIs we manufacture in the app.
 *
 * @author Luke Blanshard
 */
public class Uris {
  public static final String SCHEME = "us.blanshard.sudoku";
  public static final String SCHEME_PREFIX = SCHEME + "://";
  public static final String LIST_URI_PREFIX = SCHEME_PREFIX + "list/";
  public static final String REPLAY_URI_PREFIX = SCHEME_PREFIX + "replay/";
}
