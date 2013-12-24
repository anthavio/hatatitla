package net.anthavio.cache;

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

	private final long evictTtl; //seconds - entry will disapear after

	private final long expiryTtl; //seconds - must revalidate after - static value or server expiry 

	/**
	 * TTLs unit is parameter
	 * 
	 * @param value - to be cached, can be null
	 * @param evictTtl - cache entry eviction in seconds
	 * @param expiryTtl - cache entry become stale in seconds
	 */
	public CacheEntry(V value, long evictTtl, long expiryTtl) {
		//can be null
		this.value = value;

		// can be negative
		this.evictTtl = evictTtl;

		// can be negative
		this.expiryTtl = expiryTtl;

		if (this.expiryTtl > 0 && this.evictTtl < this.expiryTtl) {
			throw new IllegalArgumentException("evictTtl " + this.evictTtl + " must be > recentTtl " + this.expiryTtl);
		}
	}

	public boolean isExpired() {
		if (cached != null) {
			return cached.getTime() + (expiryTtl * 1000) < System.currentTimeMillis();
		} else {
			return true;
		}
	}

	public boolean isEvicted() {
		if (cached != null) {
			return cached.getTime() + (evictTtl * 1000) < System.currentTimeMillis();
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
	public long getEvictTtl() {
		return evictTtl;
	}

	public long getEvictTimestamp() {
		return cached.getTime() + (evictTtl * 1000);
	}

	/**
	 * @return seconds - after we must revalidate or update value
	 */
	public long getExpiryTtl() {
		return expiryTtl;
	}

	public long getExpiryTimestamp() {
		return cached.getTime() + (expiryTtl * 1000);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cached == null) ? 0 : cached.hashCode());
		result = prime * result + (int) (evictTtl ^ (evictTtl >>> 32));
		result = prime * result + (int) (expiryTtl ^ (expiryTtl >>> 32));
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
		if (evictTtl != other.evictTtl)
			return false;
		if (expiryTtl != other.expiryTtl)
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
			return "CacheEntry [cached=" + sdf.format(cached) + ", evictTtl=" + evictTtl + ", expiryTtl=" + expiryTtl
					+ ", value=" + value + "]";
		} else {
			return "CacheEntry [cached=, evictTtl=" + evictTtl + ", expiryTtl=" + expiryTtl + ", value=" + value + "]";
		}
	}

}
