package net.anthavio.httl.inout;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;

import org.simpleframework.xml.core.Persister;

/**
 * Java bean -> Xml Request body marshaller
 * 
 * http://simple.sourceforge.net/
 * 
 * @author martin.vanek
 *
 */
public class SimpleXmlRequestMarshaller implements RequestBodyMarshaller {

	private final Persister persister;

	public SimpleXmlRequestMarshaller() {
		this.persister = new Persister();
	}

	public SimpleXmlRequestMarshaller(Persister persister) {
		if (persister == null) {
			throw new IllegalArgumentException("null persister");
		}
		this.persister = persister;
	}

	public Persister getPersister() {
		return persister;
	}

	@Override
	public String marshall(Object requestBody) throws IOException {
		StringWriter swriter = new StringWriter();
		try {
			persister.write(requestBody, swriter);
		} catch (Exception x) {
			if (x instanceof RuntimeException) {
				throw (RuntimeException) x;
			} else if (x instanceof IOException) {
				throw (IOException) x;
			} else {
				throw new IllegalArgumentException("Marshalling failed", x);
			}
		}
		return swriter.toString();
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
		return "SimpleXmlRequestMarshaller [persister=" + persister + "]";
	}

}
