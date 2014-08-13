package net.anthavio.httl.marshall;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestException;

import org.simpleframework.xml.core.Persister;

/**
 * Java bean -> Xml Request body marshaller
 * 
 * http://simple.sourceforge.net/
 * 
 * @author martin.vanek
 *
 */
public class SimpleXmlMarshaller implements HttlBodyMarshaller {

	private final Persister persister;

	public SimpleXmlMarshaller() {
		this.persister = new Persister();
	}

	public SimpleXmlMarshaller(Persister persister) {
		if (persister == null) {
			throw new IllegalArgumentException("null persister");
		}
		this.persister = persister;
	}

	public Persister getPersister() {
		return persister;
	}

	@Override
	public void marshall(HttlRequest request, OutputStream stream) throws IOException {
		if (!request.getMediaType().contains("xml")) {
			throw new HttlRequestException("Cannot mashall into " + request.getMediaType());
		}
		write(request.getBody().getPayload(), stream, request.getCharset());
	}

	public void write(Object payload, OutputStream stream, String charset) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
		try {
			persister.write(payload, writer);
		} catch (Exception x) {
			if (x instanceof RuntimeException) {
				throw (RuntimeException) x;
			} else if (x instanceof IOException) {
				throw (IOException) x;
			} else {
				throw new IllegalArgumentException("Marshalling failed", x);
			}
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [persister=" + persister + "]";
	}

}
