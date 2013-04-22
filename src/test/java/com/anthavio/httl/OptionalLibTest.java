package com.anthavio.httl;

import static org.fest.assertions.api.Assertions.assertThat;

import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.Format;
import org.testng.annotations.Test;

import com.anthavio.httl.HttpSender.Multival;
import com.anthavio.httl.inout.GsonRequestMarshaller;
import com.anthavio.httl.inout.GsonResponseExtractor;
import com.anthavio.httl.inout.Jackson2RequestMarshaller;
import com.anthavio.httl.inout.Jackson2ResponseExtractor;
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
		SimpleXmlResponseExtractor<TestBodyRequest> extractor = new SimpleXmlResponseExtractor<TestBodyRequest>(
				TestBodyRequest.class, persister);

		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);
		System.out.println(xml);
		Multival headers = new Multival();
		//headers.add("Content-Type", "application/xml; charset=utf-8");
		TestBodyRequest extract = extractor.extract(new FakeResponse(1, headers, xml));
		//System.out.println(extract.getMessage());
		assertThat(request.getMessage()).isEqualTo(extract.getMessage());
	}

	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void jacksonXml() throws Exception {
		XmlMapper mapper = new XmlMapper();//extends ObjectMapper

		Jackson2RequestMarshaller marshaller = new Jackson2RequestMarshaller(mapper);
		Jackson2ResponseExtractor<TestBodyRequest> extractor = new Jackson2ResponseExtractor<TestBodyRequest>(
				TestBodyRequest.class, mapper);

		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);
		System.out.println(xml);
		Multival headers = new Multival();
		//headers.add("Content-Type", "application/xml; charset=utf-8");
		TestBodyRequest extract = extractor.extract(new FakeResponse(1, headers, xml));
		//System.out.println(extract.getMessage());
		assertThat(request.getMessage()).isEqualTo(extract.getMessage());
	}

	@Test
	public void gson() throws Exception {
		Gson gson = new Gson();
		GsonRequestMarshaller marshaller = new GsonRequestMarshaller(gson);
		GsonResponseExtractor<TestBodyRequest> extractor = new GsonResponseExtractor<TestBodyRequest>(
				TestBodyRequest.class, gson);

		TestBodyRequest request = new TestBodyRequest("Hello čobole");
		String xml = marshaller.marshall(request);
		System.out.println(xml);
		Multival headers = new Multival();
		//headers.add("Content-Type", "application/xml; charset=utf-8");
		TestBodyRequest extract = extractor.extract(new FakeResponse(1, headers, xml));
		//System.out.println(extract.getMessage());
		assertThat(request.getMessage()).isEqualTo(extract.getMessage());
	}
}
