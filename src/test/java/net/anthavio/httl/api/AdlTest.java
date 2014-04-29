package net.anthavio.httl.api;

import net.anthavio.httl.RequestInterceptor;
import net.anthavio.httl.ResponseInterceptor;
import net.anthavio.httl.inout.RequestBodyMarshaller;
import net.anthavio.httl.inout.ResponseBodyExtractor;
import net.anthavio.httl.inout.ResponseErrorHandler;
import net.anthavio.httl.inout.ResponseHandler;
import net.anthavio.httl.util.MockSender;

/**
 * 
 * @author martin.vanek
 *
 */
public class AdlTest {

	public static void main(String[] args) {
		// http://square.github.io/retrofit/
		// https://github.com/Netflix/feign

		MockSender sender = new MockSender();
		GitHubApi api = Reflector.build(GitHubApi.class, sender);

		RequestBodyMarshaller marshaller;
		RequestInterceptor requestInterceptor;
		ResponseInterceptor responseInterceptor;
		ResponseHandler reponseHandler;
		ResponseErrorHandler errorHandler;
		ResponseBodyExtractor<String> extractor;

		String response = api.something("anthavio", "zxzx", new int[] { 999, 333 });
		System.out.println(response);
	}

	@Headers("Content-type: application/json; charset=utf-8")
	public static interface GitHubApi {

		@Operation(method = HttpMethod.GET, value = "/something/{awful}")
		@Headers("Custom: {custom}")
		public String something(@Param("awful") String awful, @Param("custom") String custom, @Param("number") int[] number);

		@Operation("POST /entity/{id}")
		public String post(@Param("id") String id, @Body String body/*, @Body Integer another*/);

		@Operation("GET /repos/{owner}/{repo}/contributors")
		public String contributors(@Param("owner") String owner, @Param("repo") String repo);

	}
}
