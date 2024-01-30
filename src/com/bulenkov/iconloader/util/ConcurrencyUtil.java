/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bulenkov.iconloader.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Konstantin Bulenkov
 */
public class ConcurrencyUtil {

  /**
   * Invokes and waits all tasks using threadPool, avoiding thread starvation on the way
   * (see <a href="http://gafter.blogspot.com/2006/11/thread-pool-puzzler.html">"A Thread Pool Puzzler"</a>).
   */
  public static <T> List<Future<T>> invokeAll(
    Collection<Callable<T>> tasks,
    ExecutorService executorService
  ) throws Throwable {
    if (executorService == null) {
      for (var task : tasks) {
        task.call();
      }

      return null;
    }

    List<Future<T>> futures = new ArrayList<>(tasks.size());
    var done = false;

    try {
      for (var t : tasks) {
        var future = executorService.submit(t);
        futures.add(future);
      }

      // force not started futures to execute using the current thread
      for (var f : futures) {
        ((Runnable) f).run();
      }

      for (var f : futures) {
        try {
          f.get();
        } catch (CancellationException ignore) {} catch (ExecutionException e) {
          var cause = e.getCause();

          if (cause != null) {
            throw cause;
          }
        }
      }

      done = true;
    } finally {
      if (!done) {
        for (var f : futures) {
          f.cancel(false);
        }
      }
    }

    return futures;
  }

  /**
   * @return defaultValue if there is no entry in the map (in that case defaultValue is placed into the map),
   * or corresponding value if entry already exists.
   */

  public static <K, V> V cacheOrGet(
    ConcurrentMap<K, V> map,
    final K key,
    final V defaultValue
  ) {
    var v = map.get(key);

    if (v != null) {
      return v;
    }

    var prev = map.putIfAbsent(key, defaultValue);
    return prev == null ? defaultValue : prev;
  }

  public static ThreadPoolExecutor newSingleThreadExecutor(
    final String threadFactoryName
  ) {
    return newSingleThreadExecutor(threadFactoryName, Thread.NORM_PRIORITY);
  }

  public static ThreadPoolExecutor newSingleThreadExecutor(
    final String threadFactoryName,
    final int threadPriority
  ) {
    return new ThreadPoolExecutor(
      1,
      1,
      0L,
      TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<>(),
      newNamedThreadFactory(threadFactoryName, true, threadPriority)
    );
  }

  public static ScheduledThreadPoolExecutor newSingleScheduledThreadExecutor(
    final String threadFactoryName
  ) {
    return newSingleScheduledThreadExecutor(
      threadFactoryName,
      Thread.NORM_PRIORITY
    );
  }

  public static ScheduledThreadPoolExecutor newSingleScheduledThreadExecutor(
    final String threadFactoryName,
    final int threadPriority
  ) {
    var executor = new ScheduledThreadPoolExecutor(
      1,
      newNamedThreadFactory(threadFactoryName, true, threadPriority)
    );
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    return executor;
  }

  public static ThreadFactory newNamedThreadFactory(
    final String threadName,
    final boolean isDaemon,
    final int threadPriority
  ) {
    return r -> {
      final var thread = new Thread(r, threadName);
      thread.setDaemon(isDaemon);
      thread.setPriority(threadPriority);
      return thread;
    };
  }

  public static ThreadFactory newNamedThreadFactory(final String threadName) {
    return r -> new Thread(r, threadName);
  }
}
