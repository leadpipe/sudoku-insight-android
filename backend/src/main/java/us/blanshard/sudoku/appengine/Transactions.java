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

import static java.util.logging.Level.WARNING;

import us.blanshard.sudoku.messages.Rpc;

import com.google.appengine.api.datastore.DatastoreTimeoutException;
import com.google.appengine.api.datastore.Transaction;

import java.util.ConcurrentModificationException;
import java.util.logging.Logger;

/**
 * Static methods helping with datastore transactions.
 */
public class Transactions {
  private static final Logger logger = Logger.getLogger(Transactions.class.getName());

  /**
   * Commits the given transaction, translating retriable runtime exceptions
   * into {@link RpcMethod.MethodException}s containing errors that direct
   * clients to retry.
   */
  public static void commit(Transaction tx) throws RpcMethod.MethodException {
    try {
      tx.commit();
    } catch (ConcurrentModificationException e) {
      throw makeRetriableError(e);
    } catch (DatastoreTimeoutException e) {
      throw makeRetriableError(e);
    }
  }

  private static RpcMethod.MethodException makeRetriableError(Exception e) {
    logger.log(WARNING, "Retriable error", e);
    return new RpcMethod.MethodException(Rpc.error(Rpc.RETRIABLE_ERROR, "Retriable error", null));
  }
}
