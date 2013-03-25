package com.anthavio.client.http.cache;

import java.io.Serializable;
import java.util.Properties;

/**
 * 
 * @author martin.vanek
 *
 */
public interface CacheProvider {

	public void start(Properties properties);

	public void stop();

	public <V extends Serializable> RequestCache<V> buildResponseCache(String name);

}
