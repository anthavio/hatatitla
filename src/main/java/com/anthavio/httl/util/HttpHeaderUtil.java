package com.anthavio.httl.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Date;

import com.anthavio.cache.CacheEntry;
import com.anthavio.httl.HttpSender.Multival;
import com.anthavio.httl.SenderRequest;
import com.anthavio.httl.SenderResponse;
import com.anthavio.httl.cache.CachedResponse;

public class HttpHeaderUtil {

	public static CacheEntry<CachedResponse> buildCacheEntry(SenderRequest request, SenderResponse response) {

		Multival headers = response.getHeaders();

		long softTtl = 0; //seconds
		long maxAge = 0; //seconds
		boolean hasCacheControl = false;

		String headerValue = headers.getFirst("Cache-Control");
		if (headerValue != null) {
			hasCacheControl = true;
			String[] tokens = headerValue.split(",");
			for (int i = 0; i < tokens.length; i++) {
				String token = tokens[i].trim();
				if (token.equals("no-store") || token.equals("no-cache")) {
					//always check server for new version (with If-None-Match (ETag) or If-Modified-Since(Last-Modified/Date))
					//if ETag or Last-Modified is not present, we will not cache this reponse at all
					maxAge = 0;
					break;
				} else if (token.equals("must-revalidate") || token.equals("proxy-revalidate")) {
					//cache response until expire (max-age, Expires)
					//then check server for new version (with If-None-Match (ETag) or If-Modified-Since(Last-Modified/Date))
					//when max-age=0 or Expires=-1 then this is same as no-store/no-cache
					maxAge = 0;
				} else if (token.startsWith("max-age=")) {
					try {
						maxAge = Long.parseLong(token.substring(8));//seconds
						break;
					} catch (Exception e) {
						//ignore
					}
				}
			}
		}

		long serverDate = 0;
		headerValue = headers.getFirst("Date");
		if (headerValue != null) {
			serverDate = parseDateAsEpoch(headerValue);
		}

		long serverExpires = 0;
		headerValue = headers.getFirst("Expires");
		if (headerValue != null) {
			serverExpires = parseDateAsEpoch(headerValue);
		}

		long lastModified = 0;
		headerValue = headers.getFirst("Last-Modified");
		if (headerValue != null) {
			lastModified = parseDateAsEpoch(headerValue);
		}
		Date modified = lastModified > 0 ? new Date(lastModified) : null;

		String etag = headers.getFirst("ETag");

		// Cache-Control takes precedence over an Expires header, 
		// even if both exist and Expires is more restrictive.
		if (hasCacheControl) {
			softTtl = maxAge;
		} else if (serverDate > 0 && serverExpires >= serverDate) {
			// Default semantic for Expire header in HTTP specification is softExpire.
			softTtl = serverExpires - serverDate;
		}

		//if already expired and we don't have anything to check new version - don't cache at all
		if (softTtl <= 0 && Cutils.isEmpty(etag) && lastModified <= 0) {
			return null;
		}
		long hardTtl = softTtl > 0 ? softTtl : 10; //XXX default hardTtl is 10 seconds - should be parametrized

		CachedResponse cachedResponse = new CachedResponse(request, response);
		return new CacheEntry<CachedResponse>(cachedResponse, hardTtl, softTtl);

	}

	/**
	 * Shared helper to parse mediaType and charset from Content-Type header
	 */
	public static Object[] splitContentType(String contentType, Charset defaultCharset) {
		String[] splited = splitContentType(contentType, defaultCharset.name());
		return new Object[] { splited[0], Charset.forName(splited[1]) };
	}

	public static String[] splitContentType(String contentType, String defaultCharset) {
		if (Cutils.isBlank(contentType)) {
			return new String[] { "text/plain", defaultCharset };
		}
		int idxMediaEnd = contentType.indexOf(";");
		String mediaType;
		if (idxMediaEnd != -1) {
			mediaType = contentType.substring(0, idxMediaEnd);
		} else {
			mediaType = contentType;
		}
		String charset;
		int idxCharset = contentType.indexOf("charset=");
		if (idxCharset != -1) {
			charset = contentType.substring(idxCharset + 8);
		} else {
			charset = defaultCharset;
			//contentType = contentType + "; charset=" + charset;
		}
		return new String[] { mediaType, charset };
	}

	public static String getMediaType(String contentType) {
		int idxMediaEnd = contentType.indexOf(";");
		if (idxMediaEnd != -1) {
			return contentType.substring(0, idxMediaEnd);
		} else {
			return contentType;
		}
	}

	/**
	 * ISO-8859-1 is returned when contentType doesn't have charset part
	 */
	public static Charset getCharset(String contentType) {
		return getCharset(contentType, DEFAULT_CONTENT_CHARSET);
	}

	public static Charset getCharset(String contentType, Charset defaultCharset) {
		if (Cutils.isEmpty(contentType)) {
			return defaultCharset;
		}
		int idxCharset = contentType.indexOf("charset=");
		if (idxCharset != -1) {
			return Charset.forName(contentType.substring(idxCharset + 8));
		} else {
			return defaultCharset;
		}
	}

	/**
	 * Parse date in RFC1123 format, and return its value as epoch
	 */
	public static long parseDateAsEpoch(String string) {
		try {
			//Google uses Expires: -1
			//Parse date in RFC1123 format if this header contains one
			return HttpDateUtil.parseDate(string).getTime();
		} catch (ParseException px) {
			//log warning
			return 0;
		}
	}

	private static final Charset DEFAULT_CONTENT_CHARSET = Charset.forName("ISO-8859-1");

	/**
	 * Returns the charset specified in the Content-Type of this header,
	 * or the HTTP default (ISO-8859-1) if none can be found.
	 * For response...
	 
	public static Charset getCharset(String contentType) {
		//String contentType = headers.get("Content-Type");
		if (contentType != null) {
			String[] params = contentType.split(";");
			for (int i = 1; i < params.length; i++) {
				String[] pair = params[i].trim().split("=");
				if (pair.length == 2) {
					if (pair[0].equals("charset")) {
						try {
							return Charset.forName(pair[1]);
						} catch (UnsupportedCharsetException ucx) {
							//TODO log some warning
							return DEFAULT_CONTENT_CHARSET;
						}
					}
				}
			}
		}

		return DEFAULT_CONTENT_CHARSET;
	}
	*/
	/**
	 * Try to detect if content type is textual or binary
	 */
	public static boolean isTextContent(SenderResponse response) {
		boolean istext = false;
		String contentType = response.getFirstHeader("Content-Type");
		if (contentType != null) {
			String mediaType = getMediaType(contentType);
			istext = isTextContent(mediaType);
		}
		//application/octet-stream is default value when not found/detected
		return istext;
	}

	public static boolean isTextContent(String mediaType) {
		boolean istext = false;
		if (mediaType.startsWith("text")) { //text/...
			istext = true;
		} else if (mediaType.endsWith("json")) {
			//application/json
			istext = true;
		} else if (mediaType.endsWith("xml")) {
			//application/xml, application/atom+xml, application/rss+xml, ...
			istext = true;
		} else if (mediaType.endsWith("javascript")) {
			//application/javascript
			istext = true;
		} else if (mediaType.endsWith("ecmascript")) {
			istext = true;
			//application/ecmascript
		}
		return istext;
	}

	public static String readAsString(SenderResponse response) throws IOException {
		if (response instanceof CachedResponse) {
			return ((CachedResponse) response).getAsString();
		}
		if (response.getStream() == null) {
			return null; //304 NOT MODIFIED
		}

		int blength = getBufferLength(response);
		char[] buffer = new char[blength];
		Reader input = new InputStreamReader(response.getStream(), response.getCharset());
		StringWriter output = new StringWriter();
		int len = -1;
		try {
			while ((len = input.read(buffer)) != -1) {
				output.write(buffer, 0, len);
			}
		} finally {
			input.close();
		}
		return output.toString();
	}

	public static byte[] readAsBytes(SenderResponse response) throws IOException {
		if (response instanceof CachedResponse) {
			return ((CachedResponse) response).getAsBytes();
		}
		int blength = getBufferLength(response);
		byte[] buffer = new byte[blength];
		InputStream input = response.getStream();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		int len = -1;
		try {
			while ((len = input.read(buffer)) != -1) {
				output.write(buffer, 0, len);
			}
		} finally {
			input.close();
		}
		return output.toByteArray();
	}

	private static final int KILO = 1024;

	private static final int KILO64 = 64 * KILO;

	private static int getBufferLength(SenderResponse response) {
		int blength = 1024;
		String sclength = response.getFirstHeader("Content-Length");
		if (sclength != null) {
			int clength = Integer.parseInt(sclength);
			blength = clength / 100;
			if (blength < KILO) {
				blength = KILO;
			} else if (blength > KILO64) {
				blength = KILO64;
			}
		}
		//TODO enhance this method
		//String sctype = response.getFirstHeader("Content-Type"); //application/json
		//String strans = response.getFirstHeader("Transfer-Encoding"); //chunked

		return blength;
	}
}
