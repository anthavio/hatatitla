package com.anthavio.client.http.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.client.http.SenderRequest;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingRequest {

	public enum RefreshMode {
		DURING_REQUEST, //request initiated synchronous refresh
		ASYNC_REQUEST, //request initiated asynchronous thread refresh
		ASYNC_SCHEDULE; //refresh is request independently in the background thread
	}

	private final SenderRequest senderRequest;

	private final long hardTtl; //seconds

	private final long softTtl; //seconds

	private final RefreshMode refreshMode;

	private long lastRefresh;

	public CachingRequest(SenderRequest request, long hardTtl, TimeUnit unit) {
		this(request, hardTtl, hardTtl, unit, RefreshMode.DURING_REQUEST); //hardTtl = softTtl
	}

	public CachingRequest(SenderRequest request, long hardTtl, TimeUnit unit, RefreshMode refreshMode) {
		this(request, hardTtl, hardTtl, unit, refreshMode);
	}

	public CachingRequest(SenderRequest request, long hardTtl, long softTtl, TimeUnit unit) {
		this(request, hardTtl, softTtl, unit, RefreshMode.DURING_REQUEST);
	}

	public CachingRequest(SenderRequest request, long hardTtl, long softTtl, TimeUnit unit, RefreshMode refreshMode) {
		if (request == null) {
			throw new IllegalArgumentException("null request");
		}
		this.senderRequest = request;

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

		if (refreshMode == null) {
			throw new IllegalArgumentException("Refresh mode is null");
		}
		this.refreshMode = refreshMode;
	}

	public SenderRequest getSenderRequest() {
		return senderRequest;
	}

	public long getHardTtl() {
		return hardTtl;
	}

	public long getHardExpire() {
		return lastRefresh + (hardTtl * 1000);
	}

	public long getSoftTtl() {
		return softTtl;
	}

	public long getSoftExpire() {
		return lastRefresh + (softTtl * 1000);
	}

	public RefreshMode getRefreshMode() {
		return refreshMode;
	}

	public long getLastRefresh() {
		return lastRefresh;
	}

	public void setLastRefresh(long executed) {
		this.lastRefresh = executed;
	}

	public boolean isAsyncRefresh() {
		return refreshMode == RefreshMode.ASYNC_SCHEDULE || refreshMode == RefreshMode.ASYNC_REQUEST;
	}

	@Override
	public String toString() {
		return "CachingRequest [request=" + senderRequest + ", hardTtl=" + hardTtl + ", softTtl=" + softTtl + ", refreshMode="
				+ refreshMode + "]";
	}

}
