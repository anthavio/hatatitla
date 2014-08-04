package net.anthavio.httl.marshall;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import net.anthavio.httl.HttlMarshaller;

import org.simpleframework.xml.core.Persister;

/**
 * Java bean -> Xml Request body marshaller
 * 
 * http://simple.sourceforge.net/
 * 
 * @author martin.vanek
 *
 */
public class SimpleXmlMarshaller implements HttlMarshaller {

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
	public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(stream, charset);
		try {
			persister.write(requestBody, writer);
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
