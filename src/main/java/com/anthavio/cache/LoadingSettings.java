package com.anthavio.cache;

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

	//private LoadMissingExceptionHandler missingLoadExceptionHandler;

	//private LoadExpiredExceptionHandler expiredLoadExceptionHandler;

	public LoadingSettings(CacheEntryLoader<V> loader, boolean missingAsyncLoad, boolean expiredAsyncLoad) {
		if (loader == null) {
			throw new IllegalArgumentException("Null loader");
		}
		this.loader = loader;
		this.missingLoadAsync = missingAsyncLoad;
		//this.missingLoadExceptionHandler = missingLoadExceptionHandler;
		this.expiredLoadAsync = expiredAsyncLoad;
		//this.expiredLoadExceptionHandler = expiredLoadExceptionHandler;
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
	/*
	public LoadExpiredExceptionHandler getExpiredLoadExceptionHandler() {
		return expiredLoadExceptionHandler;
	}
	
	public LoadMissingExceptionHandler getMissingLoadExceptionHandler() {
		return missingLoadExceptionHandler;
	}
	*/
}