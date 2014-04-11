package net.anthavio.httl;

import java.io.ByteArrayInputStream;

import net.anthavio.httl.inout.ResponseBodyExtractor.ExtractedBodyResponse;
import net.anthavio.httl.util.MockResponse;
import net.anthavio.httl.util.MockSender;

import org.fest.assertions.api.Assertions;
import org.testng.annotations.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class MockTest {

	@Test
	public void testBinary() {
		MockSender sender = new MockSender(); //copy request into response
		byte[] array = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0 };
		ByteArrayInputStream stream = new ByteArrayInputStream(array);
		ExtractedBodyResponse<byte[]> extract = sender.POST("/binary").body(stream, "application/octet-stream")
				.extract(byte[].class);

		//MockSender should return POST request body data as response body
		Assertions.assertThat(extract.getBody()).isEqualTo(array);
		Assertions.assertThat(extract.getResponse()).isExactlyInstanceOf(MockResponse.class);
		Assertions.assertThat(extract.getResponse().getMediaType()).isEqualTo("application/octet-stream");
		Assertions.assertThat(extract.getResponse().getCharset().name()).isEqualTo("UTF-8");

		//MockSender stores last invoked Request/Response...
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/binary");
		Assertions.assertThat(sender.getLastQuery()).isNull();
		Assertions.assertThat(sender.getExecutionCount()).isEqualTo(1);

		Assertions.assertThat(sender.getLastRequest().getUrlPath()).isEqualTo("/binary");
		Assertions.assertThat(sender.getStaticResponse()).isNull();

		//Set static MockResponse
		sender.setStaticResponse(100, "some/rubbish", "More rubbish");

		extract = sender.POST("/binary2").body(stream, "application/octet-stream").extract(byte[].class);

		Assertions.assertThat(sender.getExecutionCount()).isEqualTo(2);
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/binary2");

		//Static MockResponse
		Assertions.assertThat(extract.getResponse().getHttpStatusCode()).isEqualTo(100);
		Assertions.assertThat(extract.getResponse().getMediaType()).isEqualTo("some/rubbish");

		sender.close();
	}

	@Test
	public void testString() {
		MockSender sender = new MockSender();
		String body = "Hell is coming!";
		ExtractedBodyResponse<String> extract = sender.POST("/text").body(body, "text/plain").param("zx", "xz")
				.extract(String.class);

		//MockSender should return POST request body data as response body
		Assertions.assertThat(extract.getBody()).isEqualTo(body);
		Assertions.assertThat(extract.getResponse()).isExactlyInstanceOf(MockResponse.class);
		Assertions.assertThat(extract.getResponse().getMediaType()).isEqualTo("text/plain");
		Assertions.assertThat(extract.getResponse().getCharset().name()).isEqualTo("UTF-8");

		//MockSender stores last invoked Request/Response...
		Assertions.assertThat(sender.getLastPath()).isEqualTo("/text?zx=xz");
		Assertions.assertThat(sender.getLastQuery()).isEqualTo("zx=xz");
		Assertions.assertThat(sender.getExecutionCount()).isEqualTo(1);

		Assertions.assertThat(sender.getLastRequest().getUrlPath()).isEqualTo("/text");
		Assertions.assertThat(sender.getStaticResponse()).isNull();

		ExtractedBodyResponse<String> extract2 = sender.GET("/text2").header("header1", "whatever")
				.param("param1", "buzzoff").extract(String.class);

		Assertions.assertThat(sender.getLastRequest().getUrlPath()).isEqualTo("/text2");
		Assertions.assertThat(extract2.getBody()).isEqualTo("MockResponse to GET /text2?param1=buzzoff");

		sender.close();
	}
}
