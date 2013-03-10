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
package us.blanshard.sudoku.messages;

import java.util.Calendar;

import javax.annotation.Nullable;

/**
 * RPC messages for installation details.
 */
public class InstallationRpcs {
  /** The RPC method for update. */
  public static final String UPDATE_METHOD = "installation.update";

  /**
   * Calculates an integer representing a month within a year, such that a later
   * month's number is greater than all earlier months' numbers.
   */
  public static int monthNumber(Calendar cal) {
    return cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH) + 1;
  }

  public static class UpdateParams {
    /** The installation ID, a UUID string. */
    public String id;
    /** Whether to make this installation's anonymous data public. */
    public boolean shareData;
    /** The Android SDK number this installation is running. */
    public int androidSdk;
    /** The version code of the Android app. */
    public int androidAppVersion;
    /** The manufacturer of the device where the installation lives. */
    public String manufacturer;
    /** The public model name of the device. */
    public String model;
    /** The number of puzzle streams according to this installation. */
    public int streamCount;
    /** The stream number that this installation generates puzzles from. */
    public int stream;
    /** The current month number as used for generating puzzles. */
    public int monthNumber;
    /** The account to link with this installation, if any. */
    @Nullable public AccountInfo account;
  }

  public static class AccountInfo {
    /** The account to link. */
    public String id;
    /** The user's name for the installation. */
    public String installationName;
    /** A JSON web token proving that this account belongs to this installation. */
    public String authToken;
  }

  public static class UpdateResult {
    /** The number of puzzle streams being tracked by the back end. */
    public int streamCount;
    /** The stream number this installation should be using. */
    public int stream;
    /**
     * An alternative name to use for the installation, in case the one sent
     * clashes with another installation in use by the same account.
     */
    @Nullable public String installationName;
  }
}
