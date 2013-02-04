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

/**
 * RPC messages for installation details.
 */
public class InstallationRpcs {
  /** The RPC method for update. */
  public static final String UPDATE_METHOD = "installation.update";

  public static class UpdateParams {
    /** The installation ID, a UUID string. */
    public String id;
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

  public static class UpdateResult {
    /** The number of puzzle streams being tracked by the back end. */
    public int streamCount;
    /** The stream number this installation should be using. */
    public int stream;
  }

  /** The RPC method for linking an account to an installation. */
  public static final String LINK_ACCOUNT_METHOD = "installation.link";

  public static class LinkAccountParams {
    /** The installation ID. */
    public String id;
    /** The account to link. */
    public String accountId;
    /** The user's name for the installation. */
    public String name;
  }

  public static class LinkAccountResult {
    /**
     * The name to use for the installation, in case it clashes with another
     * installation.
     */
    public String name;
  }
}
