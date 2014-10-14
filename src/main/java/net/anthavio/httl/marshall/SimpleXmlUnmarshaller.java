package net.anthavio.httl.marshall;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.util.Date;

import net.anthavio.httl.HttlBodyUnmarshaller.ConfigurableUnmarshaller;
import net.anthavio.httl.HttlResponse;

import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.transform.Transform;

/**
 * XML -> Java bean ResponseExtractor
 * 
 * http://simple.sourceforge.net/
 * 
 * @author martin.vanek
 *
 */
public class SimpleXmlUnmarshaller extends ConfigurableUnmarshaller {

	private final Persister persister;

	public SimpleXmlUnmarshaller() {
		this(new Persister());
		/*
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-d'T'HH:mm:ssZ");
		RegistryMatcher matcher = new RegistryMatcher();
		matcher.bind(Date.class, new DateFormatTransformer(format));
		Persister ser = new Persister(matcher);
		*/
	}

	public SimpleXmlUnmarshaller(Persister persister) {
		this(persister, "application/xml");
	}

	public SimpleXmlUnmarshaller(Persister persister, String mediaType) {
		this(persister, mediaType, 200, 299);
	}

	public SimpleXmlUnmarshaller(Persister persister, String mediaType, int minHttpStatus, int maxHttpStatus) {
		super(mediaType, minHttpStatus, maxHttpStatus);
		if (persister == null) {
			throw new IllegalArgumentException("Persister is null");
		}
		this.persister = persister;
	}

	public Persister getPersister() {
		return persister;
	}

	@Override
	public Object doUnmarshall(HttlResponse response, Type resultType) throws IOException {
		if (!(resultType instanceof Class<?>)) {
			throw new IllegalArgumentException("Simple XML Persister can only read Class " + resultType);
		}
		try {
			return persister.read((Class<?>) resultType, response.getReader());
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
		return getClass().getSimpleName() + " [persister=" + persister + "]";
	}
}

class DateFormatTransformer implements Transform<Date> {
	private DateFormat dateFormat;

	public DateFormatTransformer(DateFormat dateFormat) {
		this.dateFormat = dateFormat;
	}

	@Override
	public Date read(String value) throws Exception {
		return dateFormat.parse(value);
	}

	@Override
	public String write(Date value) throws Exception {
		return dateFormat.format(value);
	}

}