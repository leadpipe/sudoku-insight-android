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
package us.blanshard.sudoku.appengine;

/**
 * Constants for database kind schemas.
 */
public class Schema {
  public static class Installation {
    public static final String KIND = "Installation";

    public static final String OPAQUE_ID = "opaqueId";
    public static final String INDEXED_ID = "indexedId";
    public static final String ACCOUNT_ID = "accountId";
    public static final String NAME = "name";
    public static final String MANUFACTURER = "manufacturer";
    public static final String MODEL = "model";
    public static final String STREAM_COUNT = "streamCount";
    public static final String STREAM = "stream";
  }
}
