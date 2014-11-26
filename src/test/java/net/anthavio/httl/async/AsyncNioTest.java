package net.anthavio.httl.async;

import java.util.concurrent.Future;

import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.JokerServer;
import net.anthavio.httl.SenderConfigurer;
import net.anthavio.httl.TestBodyRequest;
import net.anthavio.httl.TestResponse;
import net.anthavio.httl.transport.ApacheAsyncConfig;
import net.anthavio.httl.transport.ApacheAsyncTransport;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class AsyncNioTest {

	static JokerServer server;

	@BeforeClass
	public static void before() {
		server = new JokerServer().start();
	}

	@AfterClass
	public static void after() {
		server.stop();
	}

	@Test
	public void test() throws Exception {
		ApacheAsyncConfig tconfig = new ApacheAsyncConfig("http://localhost:" + server.getPortHttp());
		ApacheAsyncTransport transport = new ApacheAsyncTransport(tconfig);
		SenderConfigurer sconfig = new SenderConfigurer(transport);
		HttlSender sender = new HttlSender(sconfig);
		TestBodyRequest payload = new TestBodyRequest("Reknete prdel! Mate na to!");
		/*
		ExtractedResponse<TestResponse> extract = sender.POST("/x").body(payload, "application/json", true)
				.header("Accept", "application/json").extract(TestResponse.class);
		*/
		Future<ExtractedResponse<TestResponse>> future = sender.POST("/x").body(payload, "application/json", true)
				.header("Accept", "application/json").start(TestResponse.class);
		System.out.println(future.get().getBody());
		sender.close();

		/*
		new SharedInputBuffer(0, null)
		new ContentInputStream(null)
		new ObjectMapper().readValue(null, null)
		*/
		/*
		ByteBuffer bb = null;
		CharsetDecoder decoder = Charset.defaultCharset().newDecoder();
		CharBuffer cb = new
		decoder.decode(bb, null, false)
		CharBuffer buffer = 
		*/
	}
}
