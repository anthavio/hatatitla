package com.anthavio.cache;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 
 * @author martin.vanek
 *
 */
public class CacheEntry<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	//only formatting - never use for parsing
	protected static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	private final T value;

	private final Date sinceDate = new Date(); //when entry was added

	private final long hardTtl; //seconds - after entry will disapear

	private final long softTtl; //seconds - after we must revalidate - static value or server expiry 

	/**
	 * Simplest possible
	 */
	public CacheEntry(T value, long ttlSeconds) {
		this(value, ttlSeconds, ttlSeconds);
	}

	/**
	 * TTLs are in seconds
	 */
	public CacheEntry(T value, long hardTtl, long softTtl) {
		if (value == null) {
			throw new IllegalArgumentException("cached value is null");
		}
		this.value = value;

		if (hardTtl < 1) {
			throw new IllegalArgumentException("hardTtl " + hardTtl + " must be > 1");
		}
		this.hardTtl = hardTtl;

		//can be from past
		this.softTtl = softTtl;

		if (softTtl > 0 && hardTtl < softTtl) {
			throw new IllegalArgumentException("hardTtl " + hardTtl + " must be > softTtl " + softTtl);
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

	@Override
	public String toString() {
		return "CacheEntry [since=" + sdf.format(sinceDate) + ", hard=" + sdf.format(new Date(getHardExpire())) + ", soft="
				+ sdf.format(new Date(getSoftExpire())) + "]";
	}

}
