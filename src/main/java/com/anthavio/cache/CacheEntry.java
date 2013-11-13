package com.anthavio.cache;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 
 * @author martin.vanek
 *
 */
public class CacheEntry<V> implements Serializable {

	public static final CacheEntry EMPTY = new CacheEntry(null, -1, -1);

	private static final long serialVersionUID = 1L;

	private final V value;

	private Date cached; //when entry was added

	private final long hardTtl; //seconds - entry will disapear after

	private final long softTtl; //seconds - we must revalidate after - static value or server expiry 

	/**
	 * TTLs unit is parameter
	 * 
	 * @param value - to be cached, can be null
	 * @param hardTtl - cache eviction time in seconds
	 * @param softTtl - stale content time in seconds
	 */
	public CacheEntry(V value, long hardTtl, long softTtl) {
		//can be null
		this.value = value;

		// can be negative
		this.hardTtl = hardTtl;

		// can be negative
		this.softTtl = softTtl;

		if (this.softTtl > 0 && this.hardTtl < this.softTtl) {
			throw new IllegalArgumentException("hardTtl " + this.hardTtl + " must be > softTtl " + this.softTtl);
		}
	}

	public boolean isSoftExpired() {
		if (cached != null) {
			return cached.getTime() + (softTtl * 1000) < System.currentTimeMillis();
		} else {
			return true;
		}
	}

	public boolean isHardExpired() {
		if (cached != null) {
			return cached.getTime() + (hardTtl * 1000) < System.currentTimeMillis();
		} else {
			return true;
		}
	}

	public V getValue() {
		return this.value;
	}

	public Date getCached() {
		return this.cached;
	}

	protected final void setCached(Date cached) {
		this.cached = cached;
	}

	/**
	 * @return seconds - after entry will disapear
	 */
	public long getHardTtl() {
		return hardTtl;
	}

	public long getSoftExpire() {
		return cached.getTime() + (softTtl * 1000);
	}

	/**
	 * @return seconds - after we must revalidate
	 */
	public long getSoftTtl() {
		return softTtl;
	}

	public long getHardExpire() {
		return cached.getTime() + (hardTtl * 1000);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cached == null) ? 0 : cached.hashCode());
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
		if (cached == null) {
			if (other.cached != null)
				return false;
		} else if (!cached.equals(other.cached))
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
		if (cached != null) {
			return "CacheEntry [cached=" + sdf.format(cached) + ", hardTtl=" + hardTtl + ", softTtl=" + softTtl + ", value="
					+ value + "]";
		} else {
			return "CacheEntry [cached=, hardTtl=" + hardTtl + ", softTtl=" + softTtl + ", value=" + value + "]";
		}
	}

}
