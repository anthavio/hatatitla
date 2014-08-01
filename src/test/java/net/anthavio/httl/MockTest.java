package net.anthavio.httl;

import java.io.ByteArrayInputStream;

import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.util.MockSenderConfig;
import net.anthavio.httl.util.MockResponse;
import net.anthavio.httl.util.MockTransport;

import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class MockTest {

	@Test
	public void testBinary() {
		MockTransport transport = new MockTransport();
		MockSenderConfig config = new MockSenderConfig(transport);
		HttlSender sender = config.build(); //copy request into response
		byte[] array = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 };
		ByteArrayInputStream stream = new ByteArrayInputStream(array);
		ExtractedResponse<byte[]> extract = sender.POST("/binary").body(stream, "application/octet-stream")
				.extract(byte[].class);

		//MockSender should return POST request body data as response body
		Assertions.assertThat(extract.getPayload()).isEqualTo(array);
		Assertions.assertThat(extract.getResponse()).isExactlyInstanceOf(MockResponse.class);
		Assertions.assertThat(extract.getResponse().getMediaType()).isEqualTo("application/octet-stream");
		Assertions.assertThat(extract.getResponse().getCharset().name()).isEqualTo("UTF-8");

		//MockSender stores last invoked Request/Response...
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/binary");
		Assertions.assertThat(transport.getExecutionCount()).isEqualTo(1);
		Assertions.assertThat(transport.getStaticResponse()).isNull();

		//Set static MockResponse
		transport.setStaticResponse(202, "some/rubbish", "More rubbish");

		extract = sender.POST("/binary2").body(stream, "application/octet-stream").extract(byte[].class);

		Assertions.assertThat(transport.getExecutionCount()).isEqualTo(2);
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/binary2");

		//Static MockResponse
		Assertions.assertThat(extract.getResponse().getHttpStatusCode()).isEqualTo(202);
		Assertions.assertThat(extract.getResponse().getMediaType()).isEqualTo("some/rubbish");

		sender.close();
	}

	@Test
	public void testString() {
		MockTransport transport = new MockTransport();
		MockSenderConfig config = new MockSenderConfig(transport);
		HttlSender sender = config.build();

		String body = "Hell is coming!";
		ExtractedResponse<String> extract = sender.POST("/text").body(body, "text/plain").param("zx", "xz")
				.extract(String.class);

		//MockSender should return POST request body data as response body
		Assertions.assertThat(extract.getPayload()).isEqualTo(body);
		Assertions.assertThat(extract.getResponse()).isExactlyInstanceOf(MockResponse.class);
		Assertions.assertThat(extract.getResponse().getMediaType()).isEqualTo("text/plain");
		Assertions.assertThat(extract.getResponse().getCharset().name()).isEqualTo("UTF-8");

		//MockSender stores last invoked Request/Response...
		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/text?zx=xz");
		Assertions.assertThat(transport.getExecutionCount()).isEqualTo(1);

		Assertions.assertThat(transport.getStaticResponse()).isNull();

		ExtractedResponse<String> extract2 = sender.GET("/text2").header("header1", "whatever").param("param1", "buzzoff")
				.extract(String.class);

		Assertions.assertThat(transport.getLastRequest().getPathAndQuery()).isEqualTo("/text2?param1=buzzoff");
		Assertions.assertThat(extract2.getPayload()).isEqualTo("MockResponse to GET /text2?param1=buzzoff");

		sender.close();
	}
}
