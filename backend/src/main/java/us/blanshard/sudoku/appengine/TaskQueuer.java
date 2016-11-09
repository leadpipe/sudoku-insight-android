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

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.SEVERE;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskAlreadyExistsException;
import com.google.appengine.api.taskqueue.TaskOptions;

import java.util.logging.Logger;

/**
 * Static methods for queueing tasks.
 */
public class TaskQueuer {
  private static final Logger logger = Logger.getLogger(TaskQueuer.class.getName());

  private static final long PRIMARY_STATS_TASK_COUNTDOWN = SECONDS.toMillis(30);
  private static final long BACKUP_STATS_TASK_COUNTDOWN = HOURS.toMillis(1);

  public static void queuePuzzleStatsTask(String puzzle) {
    Queue queue = QueueFactory.getDefaultQueue();
    PuzzleStatsTask task = new PuzzleStatsTask(puzzle);
    try {
      queue.add(TaskOptions.Builder
          .withCountdownMillis(PRIMARY_STATS_TASK_COUNTDOWN)
          .payload(task)
          .taskName(task.getTaskName()));
      queue.deleteTask(task.getBackupTaskName());
    } catch (TaskAlreadyExistsException e) {
      logger.info("puzzle stats task already exists for " + puzzle);
      try {
        queue.add(TaskOptions.Builder
            .withCountdownMillis(BACKUP_STATS_TASK_COUNTDOWN)
            .payload(task)
            .taskName(task.getBackupTaskName()));
      } catch (TaskAlreadyExistsException ignored) {
      } catch (Exception e2) {
        logger.log(SEVERE, "Unable to queue backup stats task for " + puzzle, e2);
      }
    } catch (Exception e) {
      logger.log(SEVERE, "Unable to queue puzzle stats task for " + puzzle, e);
    }
  }

}
