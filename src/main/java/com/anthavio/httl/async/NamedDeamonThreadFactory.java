package com.anthavio.httl.async;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author martin.vanek
 *
 */
class NamedDeamonThreadFactory implements ThreadFactory {
	//namePrefix counter
	static final AtomicInteger poolNumber = new AtomicInteger(1);

	final String namePrefix;
	final ThreadGroup group;
	final boolean deamon;
	//thread name counter
	final AtomicInteger threadNumber = new AtomicInteger(1);

	public NamedDeamonThreadFactory() {
		this("hc-" + poolNumber.getAndIncrement() + "-t-", true);
	}

	public NamedDeamonThreadFactory(String namePrefix) {
		this(namePrefix, true);
	}

	public NamedDeamonThreadFactory(String namePrefix, boolean deamon) {
		if (namePrefix == null || namePrefix.isEmpty()) {
			throw new IllegalArgumentException("Null or empty namePrefix");
		}
		this.namePrefix = namePrefix;
		SecurityManager s = System.getSecurityManager();
		this.group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
		this.deamon = deamon;
	}

	public Thread newThread(Runnable r) {
		Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement());
		t.setDaemon(deamon);
		if (t.getPriority() != Thread.NORM_PRIORITY)
			t.setPriority(Thread.NORM_PRIORITY);
		return t;
	}
}
