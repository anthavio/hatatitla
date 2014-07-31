package net.anthavio.httl;

import static org.assertj.core.api.Assertions.assertThat;
import net.anthavio.httl.inout.GsonMarshaller;
import net.anthavio.httl.inout.GsonUnmarshaller;
import net.anthavio.httl.inout.Jackson1Marshaller;
import net.anthavio.httl.inout.Jackson1Unmarshaller;
import net.anthavio.httl.inout.Jackson2Marshaller;
import net.anthavio.httl.inout.Jackson2Unmarshaller;
import net.anthavio.httl.inout.JaxbMarshaller;
import net.anthavio.httl.inout.JaxbUnmarshaller;
import net.anthavio.httl.inout.SimpleXmlMarshaller;
import net.anthavio.httl.inout.SimpleXmlUnmarshaller;
import net.anthavio.httl.util.MockResponse;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.Format;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.gson.Gson;

/**
 * 
 * @author martin.vanek
 *
 */
public class OptionalLibTest {

	/**
	 * Simple Xml is quite popular on Android (because JAXB is not on Android)
	 */
	@Test
	public void simpleXml() throws Exception {
		Format format = new Format("<?xml version=\"1.0\" encoding= \"UTF-8\" ?>");
		Persister persister = new Persister(format);

		SimpleXmlMarshaller marshaller = new SimpleXmlMarshaller(persister);
		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);

		HttlResponse response = new MockResponse(null, 200, "application/xml; charset=utf-8", xml);

		SimpleXmlUnmarshaller extractor = new SimpleXmlUnmarshaller(persister);
		TestBodyRequest extract = extractor.extract(response, TestBodyRequest.class);
		//System.out.println(extract.getMessage());

		assertThat(request.getMessage()).isEqualTo(extract.getMessage());
		assertThat(marshaller.getPersister()).isEqualTo(extractor.getPersister());
	}

	@Test
	public void jackson2Xml() throws Exception {
		XmlMapper mapper = new XmlMapper();//extends ObjectMapper

		Jackson2Marshaller marshaller = new Jackson2Marshaller(mapper);
		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);

		HttlResponse response = new MockResponse(null, 200, "application/xml; charset=utf-8", xml);

		Jackson2Unmarshaller extractor = new Jackson2Unmarshaller(mapper);
		TestBodyRequest extract = extractor.extract(response, TestBodyRequest.class);
		//System.out.println(extract.getMessage());

		assertThat(request.getMessage()).isEqualTo(extract.getMessage());

		assertThat(marshaller.getObjectMapper()).isEqualTo(extractor.getObjectMapper());
	}

	@Test
	public void jackson1Json() throws Exception {
		ObjectMapper mapper = new ObjectMapper();

		Jackson1Marshaller marshaller = new Jackson1Marshaller(mapper);
		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String json = marshaller.marshall(request);

		HttlResponse response = new MockResponse(null, 200, "application/xml; charset=utf-8", json);

		Jackson1Unmarshaller extractor = new Jackson1Unmarshaller(mapper);

		TestBodyRequest extract = extractor.extract(response, TestBodyRequest.class);
		//System.out.println(extract.getMessage());

		assertThat(request.getMessage()).isEqualTo(extract.getMessage());

		assertThat(marshaller.getObjectMapper()).isEqualTo(extractor.getObjectMapper());
	}

	@Test
	public void gson() throws Exception {
		Gson gson = new Gson();

		GsonMarshaller marshaller = new GsonMarshaller(gson);
		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);

		HttlResponse response = new MockResponse(null, 200, "application/xml; charset=utf-8", xml);

		GsonUnmarshaller extractor = new GsonUnmarshaller(gson);

		TestBodyRequest extract = extractor.extract(response, TestBodyRequest.class);

		assertThat(request.getMessage()).isEqualTo(extract.getMessage());

		assertThat(marshaller.getGson()).isEqualTo(extractor.getGson());
	}

	@Test
	public void jaxb() throws Exception {
		JaxbMarshaller marshaller = new JaxbMarshaller();
		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);

		HttlResponse response = new MockResponse(null, 200, "application/xml; charset=utf-8", xml);

		JaxbUnmarshaller extractor = new JaxbUnmarshaller();

		TestBodyRequest extract = extractor.extract(response, TestBodyRequest.class);
		//System.out.println(extract.getMessage());

		assertThat(request.getMessage()).isEqualTo(extract.getMessage());
	}

}
