package net.anthavio.cache;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author martin.vanek
 *
 */
public class CacheEntry<V> implements Serializable {

	public static final CacheEntry EMPTY = new CacheEntry(null, -1, -1, TimeUnit.SECONDS);

	private static final long serialVersionUID = 1L;

	private final V value;

	private Date storedAt; //when entry was added

	private final long evictTtl; //seconds - entry will disapear after

	private final long staleTtl; //seconds - must revalidate after - static value or server expiry 

	/**
	 * TTLs unit is parameter
	 * 
	 * @param value - to be cached, can be null
	 * @param evictTtl - cache entry eviction in seconds
	 * @param staleTtl - cache entry become stale in seconds
	 * @param unit - unit of TTL values
	 */
	public CacheEntry(V value, long evictTtl, long staleTtl, TimeUnit unit) {
		//can be null
		this.value = value;

		// can be negative
		this.evictTtl = unit.toSeconds(evictTtl);

		// can be negative
		this.staleTtl = unit.toSeconds(staleTtl);

		if (this.staleTtl > 0 && this.evictTtl < this.staleTtl) {
			throw new IllegalArgumentException("evictTtl " + this.evictTtl + " must be > recentTtl " + this.staleTtl);
		}
	}

	public CacheEntry(V value, long evictSeconds, long staleSeconds) {
		this(value, evictSeconds, staleSeconds, TimeUnit.SECONDS);
	}

	public V getValue() {
		return this.value;
	}

	/**
	 * @return millis when entry was stored in cache
	 */
	public Date getStoredAt() {
		return this.storedAt;
	}

	/**
	 * Protected as only Cache itself updates it on successful store
	 * 
	 * @param cached millis when entry was stored in cache
	 */
	protected final void setStoredAt(Date cached) {
		this.storedAt = cached;
	}

	/**
	 * @return cache entry should be revalidated
	 */
	public boolean isStale() {
		if (storedAt != null) {
			return storedAt.getTime() + (staleTtl * 1000) < System.currentTimeMillis();
		} else {
			return true;
		}
	}

	/**
	 * @return seconds - after we must revalidate or update value
	 */
	public long getStaleTtl() {
		return staleTtl;
	}

	/**
	 * @return milliseconds when entry become stale
	 */
	public long getStaleAt() {
		return storedAt.getTime() + (staleTtl * 1000);
	}

	/**
	 * @return cache entry should be thrown away and loaded again
	 */
	public boolean isEvicted() {
		if (storedAt != null) {
			return storedAt.getTime() + (evictTtl * 1000) < System.currentTimeMillis();
		} else {
			return true;
		}
	}

	/**
	 * @return seconds - after entry will be evicted
	 */
	public long getEvictTtl() {
		return evictTtl;
	}

	/**
	 * @return milliseconds when entry become evicted
	 */
	public long getEvictAt() {
		return storedAt.getTime() + (evictTtl * 1000);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((storedAt == null) ? 0 : storedAt.hashCode());
		result = prime * result + (int) (evictTtl ^ (evictTtl >>> 32));
		result = prime * result + (int) (staleTtl ^ (staleTtl >>> 32));
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
		if (storedAt == null) {
			if (other.storedAt != null)
				return false;
		} else if (!storedAt.equals(other.storedAt))
			return false;
		if (evictTtl != other.evictTtl)
			return false;
		if (staleTtl != other.staleTtl)
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
		if (storedAt != null) {
			return "CacheEntry [cached=" + sdf.format(storedAt) + ", evictTtl=" + evictTtl + ", expiryTtl=" + staleTtl
					+ ", value=" + value + "]";
		} else {
			return "CacheEntry [cached=, evictTtl=" + evictTtl + ", expiryTtl=" + staleTtl + ", value=" + value + "]";
		}
	}

}
