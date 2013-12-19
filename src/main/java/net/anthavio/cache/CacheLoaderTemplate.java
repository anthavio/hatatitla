package net.anthavio.cache;

/**
 * 
 * @author martin.vanek
 *
 */
public class CacheLoaderTemplate<V> {

	private final CacheBase<V> cache;

	public CacheLoaderTemplate(CacheBase<V> cache) {
		this.cache = cache;
	}
	

}
