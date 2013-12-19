package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.InputStreamReader;

import net.anthavio.httl.SenderResponse;

import org.simpleframework.xml.core.Persister;


/**
 * XML -> Java bean ResponseExtractor
 * 
 * http://simple.sourceforge.net/
 * 
 * @author martin.vanek
 *
 */
public class SimpleXmlResponseExtractor<T> extends ResponseBodyExtractor<T> {

	private final Class<T> resultType;

	private final Persister persister;

	/**
	 * Externaly created ObjectMapper is provided
	 */
	public SimpleXmlResponseExtractor(Class<T> resultType, Persister persister) {
		if (resultType == null) {
			throw new IllegalArgumentException("resultType is null");
		}
		this.resultType = resultType;

		if (persister == null) {
			throw new IllegalArgumentException("persister is null");
		}
		this.persister = persister;
	}

	public Persister getPersister() {
		return persister;
	}

	@Override
	public T extract(SenderResponse response) throws IOException {
		Object object = null;
		try {
			return persister.read(resultType, new InputStreamReader(response.getStream(), response.getCharset()));
		} catch (ClassCastException ccx) {
			String message = "Cannot cast: " + object.getClass().getName() + " into: " + resultType.getName() + " value: "
					+ object;
			throw new IllegalArgumentException(message, ccx);
		} catch (Exception x) {
			if (x instanceof RuntimeException) {
				throw (RuntimeException) x;
			} else if (x instanceof IOException) {
				throw (IOException) x;
			} else {
				throw new IllegalArgumentException("Extraction failed: " + x.getMessage(), x);
			}
		}
	}

	@Override
	public String toString() {
		return "SimpleXmlResponseExtractor [resultType=" + resultType + ", persister=" + persister + "]";
	}

}