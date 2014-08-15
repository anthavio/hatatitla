package net.anthavio.httl.api;

import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlResponseHandler;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.api.HttlCall.HttpMethod;
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

		HttlBodyMarshaller marshaller;
		HttlResponseHandler reponseHandler;
		HttlResponseExtractor extractor;

		String response = api.something("anthavio", "zxzx", new int[] { 999, 333 });
		System.out.println(response);
	}

	@HttlHeaders("Content-type: application/json; charset=utf-8")
	public static interface GitHubApi {

		@HttlCall(method = HttpMethod.GET, value = "/something/{awful}")
		@HttlHeaders("Custom: {custom}")
		public String something(@HttlVar("awful") String awful, @HttlVar("custom") String custom,
				@HttlVar("number") int[] number);

		@HttlCall("POST /entity/{id}")
		public String post(@HttlVar("id") String id, @HttlBody String body/*, @Body Integer another*/);

		@HttlCall("GET /repos/{owner}/{repo}/contributors")
		public String contributors(@HttlVar("owner") String owner, @HttlVar("repo") String repo);

	}
}
