package com.anthavio.cache;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author martin.vanek
 *
 */
public class CacheEntry<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	private final T value;

	private final Date sinceDate = new Date(); //when entry was added

	private final long hardTtl; //seconds - entry will disapear after

	private final long softTtl; //seconds - we must revalidate after - static value or server expiry 

	/**
	 * Simplest possible: hardTTL is equal to softTTL in seconds
	 */
	public CacheEntry(T value, long ttlSeconds) {
		this(value, ttlSeconds, ttlSeconds);
	}

	/**
	 * Simplest possible -  hardTTL is equal to softTTL
	 */
	public CacheEntry(T value, long ttl, TimeUnit unit) {
		this(value, ttl, ttl, unit);
	}

	/**
	 * TTLs unit is seconds
	 */
	public CacheEntry(T value, long hardTtlSeconds, long softTtlSeconds) {
		this(value, hardTtlSeconds, softTtlSeconds, TimeUnit.SECONDS);
	}

	/**
	 * TTLs unit is parameter
	 */
	public CacheEntry(T value, long hardTtl, long softTtl, TimeUnit unit) {
		if (value == null) {
			throw new IllegalArgumentException("cached value is null");
		}
		this.value = value;

		this.hardTtl = unit.toSeconds(hardTtl);
		if (this.hardTtl < 1) {
			throw new IllegalArgumentException("hardTtl " + this.hardTtl + " must be >= 1 second");
		}

		// softTtl can be from past
		this.softTtl = unit.toSeconds(softTtl);

		if (this.softTtl > 0 && this.hardTtl < this.softTtl) {
			throw new IllegalArgumentException("hardTtl " + this.hardTtl + " must be > softTtl " + this.softTtl);
		}
	}

	public boolean isSoftExpired() {
		return sinceDate.getTime() + (softTtl * 1000) < System.currentTimeMillis();
	}

	public boolean isHardExpired() {
		return sinceDate.getTime() + (hardTtl * 1000) < System.currentTimeMillis();
	}

	public T getValue() {
		return this.value;
	}

	public Date getSinceDate() {
		return this.sinceDate;
	}

	/**
	 * @return seconds - after entry will disapear
	 */
	public long getHardTtl() {
		return hardTtl;
	}

	public long getSoftExpire() {
		return sinceDate.getTime() + (softTtl * 1000);
	}

	/**
	 * @return seconds - after we must revalidate
	 */
	public long getSoftTtl() {
		return softTtl;
	}

	public long getHardExpire() {
		return sinceDate.getTime() + (hardTtl * 1000);
	}

	//only toString() formatting - never use for parsing
	protected static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	@Override
	public String toString() {
		return "CacheEntry [since=" + sdf.format(sinceDate) + ", hard=" + sdf.format(new Date(getHardExpire())) + ", soft="
				+ sdf.format(new Date(getSoftExpire())) + "]";
	}

}
