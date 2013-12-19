package net.anthavio.cache;

import java.io.Serializable;

/**
 * TODO shorter name
 * 
 * @author martin.vanek
 *
 * @param <V>
 */
public class LoadingSettings<V> implements Serializable {

	private static final long serialVersionUID = 1L;

	private CacheEntryLoader<V> loader;

	private boolean missingLoadAsync = false;

	private boolean expiredLoadAsync = false;

	public LoadingSettings(CacheEntryLoader<V> loader, boolean missingAsyncLoad, boolean expiredAsyncLoad) {
		if (loader == null) {
			throw new IllegalArgumentException("Null loader");
		}
		this.loader = loader;
		this.missingLoadAsync = missingAsyncLoad;
		this.expiredLoadAsync = expiredAsyncLoad;
	}

	public CacheEntryLoader<V> getLoader() {
		return loader;
	}

	public boolean isMissingLoadAsync() {
		return missingLoadAsync;
	}

	public boolean isExpiredLoadAsync() {
		return expiredLoadAsync;
	}

	@Override
	public String toString() {
		return "LoadingSettings [loader=" + loader + ", missingLoadAsync=" + missingLoadAsync + ", expiredLoadAsync="
				+ expiredLoadAsync + "]";
	}

}