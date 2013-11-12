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

	private final Date created; //when entry was added

	private final long hardTtl; //seconds - entry will disapear after

	private final long softTtl; //seconds - we must revalidate after - static value or server expiry 

	/**
	 * Simplest possible: hardTTL is equal to softTTL in seconds
	public CacheEntry(T value, long ttlSeconds) {
		this(value, ttlSeconds, ttlSeconds);
	}
	*/

	/**
	 * Simplest possible -  hardTTL is equal to softTTL
	public CacheEntry(T value, long ttl, TimeUnit unit) {
		this(value, ttl, ttl, unit);
	}
	 */

	public CacheEntry(T value, CacheEntry<T> expired) {
		this(value, expired.getCreated(), expired.getHardTtl(), expired.getSoftTtl(), TimeUnit.SECONDS);
	}

	/**
	 * TTLs unit is seconds
	*/
	public CacheEntry(T value, long hardTtlSeconds, long softTtlSeconds) {
		this(value, new Date(), hardTtlSeconds, softTtlSeconds, TimeUnit.SECONDS);
	}

	/**
	 * TTLs unit is parameter
	 * 
	 * @param value - can be null
	 * @param hardTtl - eviction time
	 * @param softTtl - stale content time
	 * @param unit
	 */
	public CacheEntry(T value, Date since, long hardTtl, long softTtl, TimeUnit unit) {
		//can be null
		this.value = value;

		this.created = since;

		// can be negative
		this.hardTtl = unit.toSeconds(hardTtl);

		// can be negative
		this.softTtl = unit.toSeconds(softTtl);

		if (this.softTtl > 0 && this.hardTtl < this.softTtl) {
			throw new IllegalArgumentException("hardTtl " + this.hardTtl + " must be > softTtl " + this.softTtl);
		}
	}

	public boolean isSoftExpired() {
		return created.getTime() + (softTtl * 1000) < System.currentTimeMillis();
	}

	public boolean isHardExpired() {
		return created.getTime() + (hardTtl * 1000) < System.currentTimeMillis();
	}

	public T getValue() {
		return this.value;
	}

	public Date getCreated() {
		return this.created;
	}

	/**
	 * @return seconds - after entry will disapear
	 */
	public long getHardTtl() {
		return hardTtl;
	}

	public long getSoftExpire() {
		return created.getTime() + (softTtl * 1000);
	}

	/**
	 * @return seconds - after we must revalidate
	 */
	public long getSoftTtl() {
		return softTtl;
	}

	public long getHardExpire() {
		return created.getTime() + (hardTtl * 1000);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((created == null) ? 0 : created.hashCode());
		result = prime * result + (int) (hardTtl ^ (hardTtl >>> 32));
		result = prime * result + (int) (softTtl ^ (softTtl >>> 32));
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CacheEntry<?> other = (CacheEntry<?>) obj;
		if (created == null) {
			if (other.created != null)
				return false;
		} else if (!created.equals(other.created))
			return false;
		if (hardTtl != other.hardTtl)
			return false;
		if (softTtl != other.softTtl)
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}

	//only toString() formatting - never use for parsing
	protected static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	@Override
	public String toString() {
		String value = String.valueOf(this.value);
		if (value.length() > 100) {
			value = value.substring(0, 100) + "...";
		}
		return "CacheEntry [since=" + sdf.format(created) + ", hard=" + sdf.format(new Date(getHardExpire())) + ", soft="
				+ sdf.format(new Date(getSoftExpire())) + ", value=" + value + "]";
	}

}
