package com.anthavio.client.http.async;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author martin.vanek
 *
 */
public class ExecutorServiceBuilder {

	private int corePoolSize = 1;
	private int maximumPoolSize = 10;
	private int maximumQueueSize = 0;
	private long keepAliveTime = 60L;
	private TimeUnit unit = TimeUnit.SECONDS;
	private ThreadFactory threadFactory = new NamedDeamonThreadFactory();
	private RejectedExecutionHandler rejectionHandler = new RejectingPolicy();

	public ExecutorService build() {
		BlockingQueue<Runnable> queue;
		if (maximumQueueSize == 0) {
			//this is not a queue actually - directly pushing requests to threads
			queue = new SynchronousQueue<Runnable>();
		} else {
			queue = new ArrayBlockingQueue<Runnable>(maximumQueueSize);
		}
		//LinkedBlockingQueue

		return new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, queue, threadFactory,
				rejectionHandler);
	}

	public int getCorePoolSize() {
		return corePoolSize;
	}

	public ExecutorServiceBuilder setCorePoolSize(int corePoolSize) {
		this.corePoolSize = corePoolSize;
		return this;
	}

	public int getMaximumPoolSize() {
		return maximumPoolSize;
	}

	public ExecutorServiceBuilder setMaximumPoolSize(int maximumPoolSize) {
		this.maximumPoolSize = maximumPoolSize;
		return this;
	}

	public long getKeepAliveTime() {
		return keepAliveTime;
	}

	public ExecutorServiceBuilder setKeepAliveTime(long keepAliveTime) {
		this.keepAliveTime = keepAliveTime;
		return this;
	}

	public TimeUnit getUnit() {
		return unit;
	}

	public ExecutorServiceBuilder setUnit(TimeUnit unit) {
		this.unit = unit;
		return this;
	}

	public ThreadFactory getThreadFactory() {
		return threadFactory;
	}

	public ExecutorServiceBuilder setThreadFactory(ThreadFactory threadFactory) {
		this.threadFactory = threadFactory;
		return this;
	}

	public RejectedExecutionHandler getRejectionHandler() {
		return rejectionHandler;
	}

	public ExecutorServiceBuilder setRejectionHandler(RejectedExecutionHandler rejectionHandler) {
		this.rejectionHandler = rejectionHandler;
		return this;
	}

	public int getMaximumQueueSize() {
		return maximumQueueSize;
	}

	public ExecutorServiceBuilder setMaximumQueueSize(int maximumQueueSize) {
		this.maximumQueueSize = maximumQueueSize;
		return this;
	}

	public class RejectingPolicy implements RejectedExecutionHandler {

		@Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
			throw new RejectedExecutionException("Cannot start " + r + "  active: " + executor.getActiveCount() + ", pool: "
					+ executor.getPoolSize() + " of " + executor.getMaximumPoolSize() + " , queue: "
					+ executor.getQueue().getClass().getSimpleName() + " " + executor.getQueue().size());

		}

	}
}
