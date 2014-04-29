package net.anthavio.httl;

/**
 * 
 * @author martin.vanek
 *
 */
public interface Constants {

	/**
	 * HTTP request/response Content-Type header
	 * 
	 * Content-Type: text/html; charset=ISO-8859-4
	 */
	public static final String Content_Type = "Content-Type";

	/**
	 * HTTP request Accept header (Expected response Content-Type)
	 * 
	 * Accept: text/plain; q=0.5, text/html, text/x-dvi; q=0.8, text/x-c
	 * 
	 * text/html and text/x-c are the preferred media types, but if they do not exist, 
	 * then send the text/x-dvi entity, and if that does not exist, send the text/plain entity
	 */
	public static final String Accept = "Accept";

	/**
	 * HTTP request Accept-Charset header (Expected response Content-Type charset)
	 * 
	 * Accept-Charset: iso-8859-5, unicode-1-1;q=0.8
	 */
	public static final String Accept_Charset = "Accept-Charset";

	/**
	 * HTTP request Content-Encoding header
	 * 
	 * Content-Encoding: gzip
	 * 
	 */
	public static final String Content_Encoding = "Content-Encoding";

	/**
	 * HTTP request header Accept-Encoding (Expected response encoding)
	 *  
	 * Accept-Encoding: compress, gzip
	 * Accept-Encoding: compress;q=0.5, gzip;q=1.0
	 */
	public static final String Accept_Encoding = "Accept-Encoding";

	/**
	 * HTTP request header Content-Length 
	 * 
	 * Content-Length: 3495
	 */
	public static final String Content_Length = "Content-Length";

}
