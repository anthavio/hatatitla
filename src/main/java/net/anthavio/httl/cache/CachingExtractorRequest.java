package net.anthavio.httl.cache;

import java.util.concurrent.TimeUnit;

import net.anthavio.cache.CachingSettings;
import net.anthavio.cache.LoadingSettings;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.cache.Builders.CachingExtractorRequestBuilder;
import net.anthavio.httl.inout.ResponseBodyExtractor;


/**
 * 
 * @author martin.vanek
 *
 */
public class CachingExtractorRequest<V> extends CachingSenderRequest {

	public static CachingExtractorRequestBuilder Builder(CachingExtractor cextractor, SenderRequest request) {
		return new CachingExtractorRequestBuilder(cextractor, request);
	}

	private final ResponseBodyExtractor<V> extractor;

	private final Class<V> resultType;

	public CachingExtractorRequest(SenderRequest request, ResponseBodyExtractor<V> extractor, CachingSettings caching, LoadingSettings<V> loading) {
		super(request, caching, loading);

		if (extractor == null) {
			throw new IllegalArgumentException("null extractor");
		}
		this.extractor = extractor;
		this.resultType = null;
	}

	public CachingExtractorRequest(SenderRequest request, Class<V> resultType, CachingSettings caching, LoadingSettings<V> loading) {
		super(request, caching, loading);

		if (resultType == null) {
			throw new IllegalArgumentException("null resultType");
		}
		this.resultType = resultType;
		this.extractor = null;
	}

	public ResponseBodyExtractor<V> getExtractor() {
		return extractor;
	}

	public Class<V> getResultType() {
		return resultType;
	}

}
