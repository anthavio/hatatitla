package net.anthavio.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author martin.vanek
 *
 */
public class Scheduler<V> {

	private Logger logger = LoggerFactory.getLogger(getClass());

	private ReentrantLock reloadLock = new ReentrantLock(true); //Lock for the refreshing Map

	private Map<String, CacheLoadRequest<V>> reloading = new HashMap<String, CacheLoadRequest<V>>();

	private ExecutorService executor;

	private Map<String, ScheduledRequest<V>> scheduled = new HashMap<String, ScheduledRequest<V>>();

	private long schedulerInterval = 1; //SECONDS

	private SchedulerThread scheduler;

	private CacheBase<V> cache;

	public Scheduler(CacheBase<V> cache, ExecutorService executor) {
		if (cache == null) {
			throw new IllegalArgumentException("Null cache");
		}
		this.cache = cache;

		if (executor == null) {
			throw new IllegalArgumentException("Null executor");
		}
		this.executor = executor;
	}

	public void close() {
		logger.info("Scheduler close " + cache.getName());
		if (scheduler != null) {
			scheduler.keepGoing = false;
			scheduler.interrupt();
		}
	}

	/**
	 * @return scheduler thread sleep time  
	 */
	public long getSchedulerInterval() {
		return schedulerInterval;
	}

	public void setSchedulerInterval(long interval, TimeUnit unit) {
		this.schedulerInterval = unit.toSeconds(interval);
		if (this.schedulerInterval < 1) {
			throw new IllegalArgumentException("Scheduler interval " + schedulerInterval + " must be >= 1 second");
		}
	}

	/**
	 * @return underylying ExecutorService
	 */
	public ExecutorService getExecutor() {
		return executor;
	}

	/**
	 * @return requests scheduled to be refreshed automaticaly
	 */
	public Map<String, ScheduledRequest<V>> getScheduled() {
		return scheduled;
	}

	/**
	 * @return 
	 */
	public CacheLoadRequest<V> getScheduled(String userKey) {
		return scheduled.get(userKey);
	}

	/**
	 * Register new CacheLoadRequest (or replace existing)
	 */
	public void schedule(CacheLoadRequest<V> request) {
		if (this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous loading is not configured");
		}
		String userKey = request.getUserKey();
		synchronized (scheduled) {
			if (scheduled.get(userKey) != null) {
				scheduled.put(userKey, new ScheduledRequest<V>(request.getCaching(), request.getLoading()));
				if (logger.isDebugEnabled()) {
					logger.debug("ScheduledRequest replaced: " + request);
				}
			} else {
				scheduled.put(userKey, new ScheduledRequest<V>(request.getCaching(), request.getLoading()));
				if (logger.isDebugEnabled()) {
					logger.debug("ScheduledRequest created: " + request);
				}

				//delayed create & start of the scheduler thread 
				synchronized (this) {
					if (scheduler == null) {
						scheduler = new SchedulerThread(schedulerInterval, TimeUnit.SECONDS);
						scheduler.start();
					}
				}
			}
		}
	}

	public void startReload(CacheLoadRequest<V> request, CacheEntry<V> expiredEntry) {
		if (this.executor == null) {
			throw new IllegalStateException("Executor for asynchronous loading is not configured");
		}
		String userKey = request.getUserKey();
		reloadLock.lock();
		try {
			if (reloading.containsKey(userKey)) {
				logger.debug("Request load already running: " + request);
			} else {
				logger.debug("Request load starting: " + request);
				try {
					reloading.put(userKey, request);
					executor.execute(new ReloaderRunnable(request, expiredEntry)); //throws Exception...
				} catch (RejectedExecutionException rx) {
					logger.warn("Request load start rejected " + rx.getMessage());
					reloading.remove(userKey);
				} catch (Exception x) {
					logger.error("Request load start failed: " + request, x);
					reloading.remove(userKey);
				}
			}
		} finally {
			reloadLock.unlock();
		}
	}

	/**
	 * Runnable for {@link LoadMode#ASYNC}
	 * 
	 */
	private class ReloaderRunnable implements Runnable {

		private final CacheLoadRequest<V> request;

		private final CacheEntry<V> softExpiredEntry;

		public ReloaderRunnable(CacheLoadRequest<V> request, CacheEntry<V> softExpiredEntry) {
			if (request == null) {
				throw new IllegalArgumentException("request is null");
			}
			this.request = request;
			this.softExpiredEntry = softExpiredEntry;
		}

		@Override
		public void run() {
			try {
				cache.load(true, request, softExpiredEntry);
				if (logger.isDebugEnabled()) {
					logger.debug("Request load completed: " + request);
				}
			} catch (Exception x) {
				logger.warn("Request load failed: " + request, x);
			} finally {
				reloading.remove(request.getUserKey());
			}
		}
	}

	private class SchedulerThread extends Thread {

		private final long interval;

		private boolean keepGoing = true;

		public SchedulerThread(long interval, TimeUnit unit) {
			this.interval = unit.toMillis(interval);
			if (this.interval < 1000) {
				throw new IllegalArgumentException("Interval " + this.interval + " must be >= 1000 millis");
			}
			setDaemon(true);
			setName("scheduler-" + cache.getName());
		}

		@Override
		public void run() {
			logger.info(getName() + " started");
			while (keepGoing) {
				try {
					check();
				} catch (Exception x) {
					logger.warn(getName() + " check iteration failed", x);
				}
				try {
					Thread.sleep(interval);
				} catch (InterruptedException ix) {
					logger.info(getName() + " interrupted");
					if (!keepGoing) {
						break;
					}
				}
			}
			logger.info(getName() + " stopped");
		}

		private void check() {
			long now = System.currentTimeMillis();
			if (logger.isTraceEnabled()) {
				logger.trace(getName() + " check");
			}
			for (Entry<String, ScheduledRequest<V>> entry : scheduled.entrySet()) {
				try {
					ScheduledRequest<V> request = entry.getValue();
					if (request.getSoftExpire() <= now) {
						startReload(request, null);
					}
				} catch (Exception x) {
					logger.warn("Exception during refresh of " + entry.getValue(), x);
				}
			}
		}
	}
}
