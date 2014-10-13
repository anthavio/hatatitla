package net.anthavio.cache;

/**
 * 
 * @author martin.vanek
 *
 */
class ScheduledRequest<K, V> extends CacheLoadRequest<K, V> {

	private long lastRefresh; //only for RefreshMode.SCHEDULED

	public ScheduledRequest(CachingSettings<K> caching, LoadingSettings<K, V> loading) {
		super(caching, loading);
	}

	protected long getHardExpire() {
		return lastRefresh + (caching.getHardTtl() * 1000);
	}

	protected long getSoftExpire() {
		return lastRefresh + (caching.getSoftTtl() * 1000);
	}

	protected void setLastRefresh(long lastRefresh) {
		this.lastRefresh = lastRefresh;
	}

	public long getLastRefresh() {
		return lastRefresh;
	}

}
