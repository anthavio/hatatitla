package com.anthavio.httl.cache;

import java.util.concurrent.TimeUnit;

import com.anthavio.httl.SenderRequest;

/**
 * 
 * @author martin.vanek
 *
 */
public class CachingRequest {

	public enum RefreshMode {
		/**
		 * refresh is request initiated and done using synchronously using request thread
		 */
		REQUEST_SYNC,
		/**
		 * refresh is request initiated, but done using the background thread
		 */
		REQUEST_ASYN,
		/**
		 * refresh is scheduled and performed using the background thread
		 */
		SCHEDULED;
	}

	private final SenderRequest senderRequest;

	private final long hardTtl; //seconds

	private final long softTtl; //seconds

	private final RefreshMode refreshMode;

	private long lastRefresh;

	public CachingRequest(SenderRequest request, long hardTtl, TimeUnit unit) {
		this(request, hardTtl, hardTtl, unit, RefreshMode.REQUEST_SYNC); //hardTtl = softTtl
	}

	public CachingRequest(SenderRequest request, long hardTtl, TimeUnit unit, RefreshMode refreshMode) {
		this(request, hardTtl, hardTtl, unit, refreshMode);
	}

	public CachingRequest(SenderRequest request, long hardTtl, long softTtl, TimeUnit unit) {
		this(request, hardTtl, softTtl, unit, RefreshMode.REQUEST_SYNC);
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

	protected void setLastRefresh(long executed) {
		this.lastRefresh = executed;
	}

	public boolean isAsyncRefresh() {
		return refreshMode == RefreshMode.SCHEDULED || refreshMode == RefreshMode.REQUEST_ASYN;
	}

	@Override
	public String toString() {
		return "CachingRequest [" + super.toString() + ", hardTtl=" + hardTtl + ", softTtl=" + softTtl + ", refreshMode="
				+ refreshMode + "]";
	}

}
