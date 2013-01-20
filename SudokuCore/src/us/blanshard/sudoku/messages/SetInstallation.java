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
 * The request/response for updating installation details.
 */
public class SetInstallation {
  public static class Request {
    /** The installation ID, a UUID string. */
    public String id;
    /** The optional account to use for linking installations. */
    @Nullable public String accountId;
    /** The user's name for the installation. */
    @Nullable public String name;
    /** Whether to make this installation's anonymous data public. */
    public boolean shareData;
    /** The manufacturer of the device where the installation lives. */
    public String manufacturer;
    /** The public model name of the device. */
    public String model;
    /** The number of puzzle streams according to this installation. */
    public int streamCount;
    /** The stream number that this installation generates puzzles from. */
    public int stream;
  }

  public static class Response {
    /**
     * The name to use for the installation, in case it clashes with another
     * installation.
     */
    @Nullable public String name;
    /** The number of puzzle streams being tracked by the back end. */
    public int streamCount;
    /** The stream number this installation should be using. */
    public int stream;
  }
}
