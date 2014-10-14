package net.anthavio.cache;

/**
 * This is a basically convertor
 * 
 * Usually Cache physical storage requires simple String key but applications usually use some sort of request
 * 
 * @author martin.vanek
 *
 */
public interface CacheKeyProvider<R> {

	/**
	 * Build String key for keyObject
	 */
	public String provideKey(R request);

	public static CacheKeyProvider<String> STRING = new CacheKeyProvider<String>() {

		@Override
		public String provideKey(String request) {
			return request;
		}
	};
}
