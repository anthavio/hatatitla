package com.anthavio.httl;

import static org.fest.assertions.api.Assertions.assertThat;

import org.codehaus.jackson.map.ObjectMapper;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.Format;
import org.testng.annotations.Test;

import com.anthavio.httl.inout.GsonExtractorFactory;
import com.anthavio.httl.inout.GsonRequestMarshaller;
import com.anthavio.httl.inout.GsonResponseExtractor;
import com.anthavio.httl.inout.Jackson1ExtractorFactory;
import com.anthavio.httl.inout.Jackson1RequestMarshaller;
import com.anthavio.httl.inout.Jackson1ResponseExtractor;
import com.anthavio.httl.inout.Jackson2ExtractorFactory;
import com.anthavio.httl.inout.Jackson2RequestMarshaller;
import com.anthavio.httl.inout.Jackson2ResponseExtractor;
import com.anthavio.httl.inout.JaxbExtractorFactory;
import com.anthavio.httl.inout.JaxbRequestMarshaller;
import com.anthavio.httl.inout.JaxbResponseExtractor;
import com.anthavio.httl.inout.SimpleXmlExtractorFactory;
import com.anthavio.httl.inout.SimpleXmlRequestMarshaller;
import com.anthavio.httl.inout.SimpleXmlResponseExtractor;
import com.anthavio.httl.util.FakeSender.FakeResponse;
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

		SimpleXmlRequestMarshaller marshaller = new SimpleXmlRequestMarshaller(persister);
		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);

		SenderResponse response = new FakeResponse(200, "application/xml; charset=utf-8", xml);

		SimpleXmlExtractorFactory factory = new SimpleXmlExtractorFactory(persister);
		SimpleXmlResponseExtractor<TestBodyRequest> extractor = factory.getExtractor(response, TestBodyRequest.class);

		TestBodyRequest extract = extractor.extract(response);
		//System.out.println(extract.getMessage());

		assertThat(request.getMessage()).isEqualTo(extract.getMessage());

		assertThat(factory.getCache().size()).isEqualTo(1);
		assertThat(marshaller.getPersister()).isEqualTo(extractor.getPersister());
	}

	@Test
	public void jackson2Xml() throws Exception {
		XmlMapper mapper = new XmlMapper();//extends ObjectMapper

		Jackson2RequestMarshaller marshaller = new Jackson2RequestMarshaller(mapper);
		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);

		SenderResponse response = new FakeResponse(200, "application/xml; charset=utf-8", xml);

		Jackson2ExtractorFactory factory = new Jackson2ExtractorFactory(mapper);
		Jackson2ResponseExtractor<TestBodyRequest> extractor = factory.getExtractor(response, TestBodyRequest.class);

		TestBodyRequest extract = extractor.extract(response);
		//System.out.println(extract.getMessage());

		assertThat(request.getMessage()).isEqualTo(extract.getMessage());

		assertThat(factory.getCache().size()).isEqualTo(1);
		assertThat(marshaller.getObjectMapper()).isEqualTo(extractor.getObjectMapper());
	}

	@Test
	public void jackson1Json() throws Exception {
		ObjectMapper mapper = new ObjectMapper();

		Jackson1RequestMarshaller marshaller = new Jackson1RequestMarshaller(mapper);
		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String json = marshaller.marshall(request);

		SenderResponse response = new FakeResponse(200, "application/xml; charset=utf-8", json);

		Jackson1ExtractorFactory factory = new Jackson1ExtractorFactory(mapper);
		Jackson1ResponseExtractor<TestBodyRequest> extractor = factory.getExtractor(response, TestBodyRequest.class);

		TestBodyRequest extract = extractor.extract(response);
		//System.out.println(extract.getMessage());

		assertThat(request.getMessage()).isEqualTo(extract.getMessage());

		assertThat(factory.getCache().size()).isEqualTo(1);
		assertThat(marshaller.getObjectMapper()).isEqualTo(extractor.getObjectMapper());
	}

	@Test
	public void gson() throws Exception {
		Gson gson = new Gson();

		GsonRequestMarshaller marshaller = new GsonRequestMarshaller(gson);
		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);

		SenderResponse response = new FakeResponse(200, "application/xml; charset=utf-8", xml);

		GsonExtractorFactory factory = new GsonExtractorFactory(gson);
		GsonResponseExtractor<TestBodyRequest> extractor = factory.getExtractor(response, TestBodyRequest.class);

		TestBodyRequest extract = extractor.extract(response);
		//System.out.println(extract.getMessage());

		assertThat(request.getMessage()).isEqualTo(extract.getMessage());

		assertThat(factory.getCache().size()).isEqualTo(1);
		assertThat(marshaller.getGson()).isEqualTo(extractor.getGson());
	}

	@Test
	public void jaxb() throws Exception {
		JaxbRequestMarshaller marshaller = new JaxbRequestMarshaller();
		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);

		SenderResponse response = new FakeResponse(200, "application/xml; charset=utf-8", xml);

		JaxbExtractorFactory factory = new JaxbExtractorFactory();
		JaxbResponseExtractor<TestBodyRequest> extractor = factory.getExtractor(response, TestBodyRequest.class);

		TestBodyRequest extract = extractor.extract(response);
		//System.out.println(extract.getMessage());

		assertThat(request.getMessage()).isEqualTo(extract.getMessage());
		assertThat(factory.getCache().size()).isEqualTo(1);
	}

}
