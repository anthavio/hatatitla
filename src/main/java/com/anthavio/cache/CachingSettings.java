package com.anthavio.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.httl.util.Cutils;

/**
 * TODO shorter name
 * 
 * @author martin.vanek
 *
 */
public class CachingSettings {

	private final String userKey;
	
	private final long hardTtl; //seconds

	private final long softTtl; //seconds

	/**
	 * @param userKey - user provided cache key
	 * @param hardTtl - cache entry expiry time 
	 * @param softTtl - cache entry refresh time
	 * @param unit - time unit of ttl
	 * 
	 */
	public CachingSettings(String userKey, long hardTtl, long softTtl, TimeUnit unit) {
		if (Cutils.isBlank(userKey)) {
			throw new IllegalArgumentException("Cache key is blank");
		}
		this.userKey = userKey;
		
		hardTtl = unit.toSeconds(hardTtl);
		if (hardTtl <= 0) {
			throw new IllegalArgumentException("hardTtl " + hardTtl + " must be >= 1 second ");
		}
		this.hardTtl = hardTtl;

		softTtl = unit.toSeconds(softTtl);
		if (hardTtl < softTtl) {
			throw new IllegalArgumentException("hardTtl " + hardTtl + " must be >= softTtl " + softTtl);
		}
		this.softTtl = softTtl;
	}
	
	public String getUserKey() {
		return userKey;
	}

	public long getHardTtl() {
		return hardTtl;
	}

	protected long getHardExpire(long lastRefresh) {
		return lastRefresh + (hardTtl * 1000);
	}

	public long getSoftTtl() {
		return softTtl;
	}

	protected long getSoftExpire(long lastRefresh) {
		return lastRefresh + (softTtl * 1000);
	}

}
