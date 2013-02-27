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

import static com.google.common.base.Preconditions.checkState;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.StrictMode;
import android.util.Log;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A retained fragment that manages async tasks on behalf of other fragments
 * that share its activity. The basic idea was lifted from AsyncTask; we don't
 * use that class anymore because it tends to cause problems when the
 * activity gets restarted.
 *
 * <p>
 * The key to using {@link Task} successfully is to ensure that all access to
 * the fragment is done via the argument to the various <code>onXxx</code>
 * methods, and not via an implicit reference because the task is an inner class
 * of the fragment. In fact, to avoid running afoul of {@link StrictMode}
 * checks, it's best to make your tasks true nested classes if they are declared
 * inside a fragment class.
 *
 * @author Luke Blanshard
 */
public class WorkerFragment extends Fragment {

  /**
   * A worker task's independence from its originating fragment/activity. A
   * dependent task is automatically canceled if the activity is restarted,
   * whereas a free task continues running and reports back to the new fragment
   * instance when it completes.  Tasks are dependent by default.
   */
  public enum Independence {
    DEPENDENT, FREE;
  }

  /** The priorities at which a worker task's thread can run. */
  public enum Priority {
    BACKGROUND(Process.THREAD_PRIORITY_BACKGROUND),
    FOREGROUND(Process.THREAD_PRIORITY_FOREGROUND);

    public final int mThreadPriority;

    private Priority(int threadPriority) {
      this.mThreadPriority = threadPriority;
    }
  }

  /**
   * The common base class for {@link Task} and {@link ActivityTask}.
   *
   * @param <A> the anchor type: a fragment or activity class
   * @param <I> the input type, provided to the background thread
   * @param <P> the progress type, sent to the UI thread during the background work
   * @param <O> the output type, returned from the background thread
   */
  public static abstract class BaseTask<A, I, P, O> {

    // Public API

    /** Initiates the background task, passing the given inputs. */
    public final void execute(final I... inputs) {
      checkState(mFuture == null, "Task already executed");
      Runnable r = new Runnable() {
        @Override public void run() {
          Process.setThreadPriority(mPriority.mThreadPriority);
          Foreground job;
          try {
            final O output = doInBackground(inputs);
            job = new Foreground() {
              @Override public void run() {
                onPostExecute(getAnchor(), output);
              }
            };
          } catch (final Throwable t) {
            Log.w(TAG, t);
            job = new Foreground() {
              @Override public void run() {
                onFailure(getAnchor(), t);
              }
            };
          }
          sHandler.obtainMessage(0, job).sendToTarget();
        }
      };
      onPreExecute(getAnchor());
      mFuture = sExecutor.submit(r);
      if (mIndependence == Independence.DEPENDENT)
        mWorker.mDependentTasks.add(this);
    }

    /**
     * Interrupts the background thread, if possible. Can only be run after
     * {@link #execute} has been called. If the task hasn't already been
     * started, this may prevent it from being started.
     */
    public final void cancel() {
      mFuture.cancel(true);
    }

    /** Tells whether this task was canceled. */
    public final boolean wasCanceled() {
      return mFuture != null && mFuture.isCancelled();
    }

    // Subclass API

    /**
     * This method gets called in a background thread; its output object is
     * posted to the UI thread and passed as an argument to
     * {@link onPostExecute}.
     */
    protected abstract O doInBackground(I... inputs);

    /**
     * Call this method from {@link #doInBackground} to tell your fragment via
     * {@link #onProgressUpdate} of progress made.
     */
    protected final void publishProgress(final P... values) {
      sHandler.obtainMessage(0, new Foreground() {
        @Override public void run() {
          onProgressUpdate(getAnchor(), values);
        }
      }).sendToTarget();
    }

    /** Called on UI thread before the background thread runs. */
    protected void onPreExecute(A anchor) {
    }

    /** Called on UI thread after {@link #publishProgress} is called in background thread. */
    protected void onProgressUpdate(A anchor, P... values) {
    }

    /** Called on UI thread after the background thread completes. */
    protected void onPostExecute(A anchor, O output) {
    }

    /**
     * Called on UI thread after the background thread blows up. Default
     * behavior is to throw on foreground thread, ie kill the app.
     */
    protected void onFailure(A anchor, Throwable t) {
      throw Throwables.propagate(t);
    }

    // Implementation

    final WorkerFragment mWorker;
    final Class<A> mAnchorClass;
    private final Priority mPriority;
    private final Independence mIndependence;
    private Future<?> mFuture;

    @SuppressWarnings("unchecked")
    BaseTask(A anchor, Priority priority, Independence independence) {
      this.mWorker = getTaskWorker(anchor);
      this.mAnchorClass = (Class<A>) anchor.getClass();
      this.mPriority = priority;
      this.mIndependence = independence;
    }

    abstract WorkerFragment getTaskWorker(A anchor);
    abstract A getAnchor();

    abstract class Foreground implements Runnable {
      final void runInForeground() {
        if (getAnchor() == null)
          mWorker.runWhenAttached(this);
        else
          run();
      }
    }
  }


  /**
   * Subclass this and implement {@link #doInBackground} to run a background task
   * from a fragment.
   *
   * @param <F> the Fragment subclass running the task
   * @param <I> the input type, provided to the background thread
   * @param <P> the progress type, sent to the UI thread during the background work
   * @param <O> the output type, returned from the background thread
   */
  public static abstract class Task<F extends Fragment, I, P, O> extends BaseTask<F, I, P, O> {

    // Public API

    /**
     * Creates the task to report back to the given fragment. Subclasses must
     * not be true inner classes of their fragments; tasks do not hold
     * references to their fragments, but rather reconstruct them when it's time
     * to call the UI methods.
     */
    public Task(F fragment) {
      this(fragment, Priority.BACKGROUND, Independence.DEPENDENT);
    }

    /**
     * Creates the task to report back to the given fragment, with the option of
     * controlling the priority the background thread runs at and whether the
     * task should keep running if the fragment goes away. Subclasses must not
     * be true inner classes of their fragments; tasks do not hold references to
     * their fragments, but rather reconstruct them when it's time to call the
     * UI methods.
     */
    public Task(F fragment, Priority priority, Independence independence) {
      super(fragment, priority, independence);
      this.mFragmentId = fragment.getId();
    }

    // Implementation

    private final int mFragmentId;

    @Override final WorkerFragment getTaskWorker(F fragment) {
      return getWorker(fragment);
    }

    @Override final F getAnchor() {
      return mWorker.getFragment(mAnchorClass, mFragmentId);
    }
  }


  /**
   * Subclass this and implement {@link #doInBackground} to run a background task
   * from an activity.
   *
   * @param <A> the Activity subclass running the task
   * @param <I> the input type, provided to the background thread
   * @param <P> the progress type, sent to the UI thread during the background work
   * @param <O> the output type, returned from the background thread
   */
  public static abstract class ActivityTask<A extends Activity, I, P, O> extends BaseTask<A, I, P, O> {

    // Public API

    /**
     * Creates the task to report back to the given activity. Subclasses must
     * not be true inner classes of their fragments; tasks do not hold
     * references to their activities, but rather reconstruct them when it's
     * time to call the UI methods.
     */
    public ActivityTask(A activity) {
      this(activity, Priority.BACKGROUND, Independence.DEPENDENT);
    }

    /**
     * Creates the task to report back to the given activity, with the option of
     * controlling the priority the background thread runs at and whether the
     * task should keep running if the activity goes away. Subclasses must not
     * be true inner classes of their fragments; tasks do not hold references to
     * their fragments, but rather reconstruct them when it's time to call the
     * UI methods.
     */
    public ActivityTask(A activity, Priority priority, Independence independence) {
      super(activity, priority, independence);
    }

    // Implementation

    @Override final WorkerFragment getTaskWorker(A activity) {
      return getWorker(activity);
    }

    @Override final A getAnchor() {
      return mAnchorClass.cast(mWorker.getActivity());
    }
  }


  // Implementation of WorkerFragment

  /** The fragment tag for the worker fragment. */
  private static final String TAG = WorkerFragment.class.getSimpleName();

  /**
   * The executor for the background portion of worker tasks; only runs one at a
   * time.
   */
  private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor(
      new ThreadFactoryBuilder().setNameFormat("WorkerFragment #%d").build());

  /** Our UI-thread handler looks for a Foreground object in its message and runs it. */
  private static final Handler sHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      ((Task<?,?,?,?>.Foreground) msg.obj).runInForeground();
    }
  };

  /** Tasks that must be canceled when the activity goes away. */
  private Queue<BaseTask<?,?,?,?>> mDependentTasks = Lists.newLinkedList();

  /** Task outputs or progress notifications that came when the activity was detached. */
  private Queue<BaseTask<?,?,?,?>.Foreground> mPendingForegroundJobs = Lists.newLinkedList();

  /** Finds the worker fragment for the given fragment, creating it if required. */
  static WorkerFragment getWorker(Fragment fragment) {
    return getWorker(fragment.getFragmentManager());
  }

  /** Finds the worker fragment for the given activity, creating it if required. */
  static WorkerFragment getWorker(Activity activity) {
    return getWorker(activity.getFragmentManager());
  }

  private static WorkerFragment getWorker(FragmentManager fm) {
    WorkerFragment worker = (WorkerFragment) fm.findFragmentByTag(TAG);
    if (worker == null) {
      worker = new WorkerFragment();
      fm.beginTransaction().add(worker, TAG).commit();
    }
    return worker;
  }

  /** Finds the fragment with the given ID, or null. */
  <F extends Fragment> F getFragment(Class<F> fragmentClass, int fragmentId) {
    FragmentManager fragmentManager = getFragmentManager();
    if (fragmentManager == null) return null;
    Fragment f = fragmentManager.findFragmentById(fragmentId);
    return fragmentClass.cast(f);
  }

  void runWhenAttached(Task<?,?,?,?>.Foreground foregroundJob) {
    mPendingForegroundJobs.add(foregroundJob);
  }

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setRetainInstance(true);
  }

  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    int count = mPendingForegroundJobs.size();
    // We do it this way because runInForeground can append to the queue.
    while (count-- > 0)
      mPendingForegroundJobs.remove().runInForeground();
  }

  @Override public void onDetach() {
    while (!mDependentTasks.isEmpty())
      mDependentTasks.remove().cancel();
    super.onDetach();
  }
}
