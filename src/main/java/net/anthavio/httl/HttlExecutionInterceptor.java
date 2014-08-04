package net.anthavio.httl;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import net.anthavio.cache.Cache;
import net.anthavio.cache.CacheEntry;

/**
 * Comapring to HttlBuilderInterceptor, this interceptor cannot change HttlRequest, 
 * but can wrap it or return surrogate HttlResponse instead of execution
 * 
 * @author martin.vanek
 *
 */
public interface HttlExecutionInterceptor {

	public HttlResponse intercept(HttlRequest request, HttlExecutionChain chain) throws IOException;

	/**
	 * Example how ExecutionInterceptor can be used
	 */
	public static class CachingExcetionInterceptor implements HttlExecutionInterceptor {

		private int staleTtl = 1;

		private int evictTtl = 5;

		private Cache<HttlRequest, HttlResponse> cache;

		@Override
		public HttlResponse intercept(HttlRequest request, HttlExecutionChain chain) throws IOException {
			CacheEntry<HttlResponse> entry = cache.get(request);
			if (entry != null) {
				//TODO if entry.isStale();
				return entry.getValue();
			} else {
				HttlResponse response = chain.next(request);
				cache.set(request, new CacheEntry<HttlResponse>(response, evictTtl, staleTtl, TimeUnit.MINUTES));
				return response;
			}

		}

	}
}
