package com.anthavio.httl.cache;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.anthavio.httl.util.Cutils;

/**
 * 
 * @author martin.vanek
 *
 */
public class CacheEntry<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	private final T value;

	private final Date sinceDate = new Date(); //when entry was added

	private final long hardTtl; //seconds - after entry will disapear

	private final long softTtl; //seconds - after we must revalidate - static value or server expiry 

	//http header values

	private Date serverDate; //for validation after expiration (Last-Modified)

	private String serverTag; //for validation after expiration (ETag)

	/**
	 * TTLs are in seconds
	 */
	public CacheEntry(T value, long hardTtl, long softTtl) {
		this(value, hardTtl, softTtl, null, null);
		//because contructor invocation must be first statement...
		if (softTtl <= 0) {
			throw new IllegalArgumentException("softTtl must be > 0 when doing static caching");
		}
	}

	/**
	 * Http headers based caching
	 */
	public CacheEntry(T value, long hardTtl, long softTtl, String serverTag, Date serverDate) {
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

		if (serverTag != null && Cutils.isBlank(serverTag)) {
			throw new IllegalArgumentException("serverTag can be null, but must not be blank");
		}
		this.serverTag = serverTag;

		if (serverDate != null && serverDate.getTime() > System.currentTimeMillis()) {
			throw new IllegalArgumentException("Server date is from future " + serverDate); //should we care?
		}
		this.serverDate = serverDate;
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

	public String getServerTag() {
		return serverTag;
	}

	public void setServerTag(String serverTag) {
		this.serverTag = serverTag;
	}

	public Date getServerDate() {
		return serverDate;
	}

	public void setServerDate(Date serverDate) {
		this.serverDate = serverDate;
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
				+ sdf.format(new Date(getSoftExpire())) + ", "
				+ (serverDate != null ? "serverDate=" + sdf.format(serverDate) + ", " : "")
				+ (serverTag != null ? "serverTag=" + serverTag : "") + "]";
	}

}
