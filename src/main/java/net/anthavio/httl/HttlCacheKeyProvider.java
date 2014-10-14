package net.anthavio.httl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import net.anthavio.cache.CacheKeyProvider;
import net.anthavio.httl.HttlBody.Type;
import net.anthavio.httl.util.Base64;

/**
 * 
 * @author martin.vanek
 *
 */
public class HttlCacheKeyProvider implements CacheKeyProvider<HttlRequest> {

	private final byte[] urlBytes;

	public HttlCacheKeyProvider(String url) {
		if (url == null || url.isEmpty()) {
			throw new IllegalArgumentException("Wrong url: " + url);
		}
		this.urlBytes = url.getBytes();
	}

	@Override
	public String provideKey(HttlRequest request) {
		HttlBody body = request.getBody();
		if (body != null && !(body.getType() == Type.BYTES || body.getType() == Type.STRING)) {
			throw new IllegalArgumentException("Only requests with buffered body can be cached " + request);
		}
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException nsax) {
			throw new IllegalStateException("SHA-1 MessageDigest failed", nsax);
		}
		digest.update(urlBytes);
		request.update(digest);
		return new String(Base64.encode(digest.digest()));
	}

}
