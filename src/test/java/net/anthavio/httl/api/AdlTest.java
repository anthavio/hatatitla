package net.anthavio.httl.api;

import net.anthavio.httl.HttlSender;
import net.anthavio.httl.HttlRequestInterceptor;
import net.anthavio.httl.ResponseExtractor;
import net.anthavio.httl.HttlResponseHandler;
import net.anthavio.httl.HttlResponseInterceptor;
import net.anthavio.httl.inout.RequestMarshaller;
import net.anthavio.httl.util.MockSenderConfig;

/**
 * 
 * @author martin.vanek
 *
 */
public class AdlTest {

	public static void main(String[] args) {
		// http://square.github.io/retrofit/
		// https://github.com/Netflix/feign

		HttlSender sender = new MockSenderConfig().build();
		GitHubApi api = HttlApiBuilder.build(GitHubApi.class, sender);

		RequestMarshaller marshaller;
		HttlRequestInterceptor requestInterceptor;
		HttlResponseInterceptor responseInterceptor;
		HttlResponseHandler reponseHandler;
		ResponseExtractor extractor;

		String response = api.something("anthavio", "zxzx", new int[] { 999, 333 });
		System.out.println(response);
	}

	@RestHeaders("Content-type: application/json; charset=utf-8")
	public static interface GitHubApi {

		@RestCall(method = HttpMethod.GET, value = "/something/{awful}")
		@RestHeaders("Custom: {custom}")
		public String something(@RestVar("awful") String awful, @RestVar("custom") String custom, @RestVar("number") int[] number);

		@RestCall("POST /entity/{id}")
		public String post(@RestVar("id") String id, @RestBody String body/*, @Body Integer another*/);

		@RestCall("GET /repos/{owner}/{repo}/contributors")
		public String contributors(@RestVar("owner") String owner, @RestVar("repo") String repo);

	}
}
