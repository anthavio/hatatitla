package net.anthavio.httl.api;

import net.anthavio.httl.api.Headers;
import net.anthavio.httl.api.HttpMethod;
import net.anthavio.httl.api.Operation;
import net.anthavio.httl.api.Param;
import net.anthavio.httl.api.Reflector;

/**
 * 
 * @author martin.vanek
 *
 */
public class AdlTest {

	public static void main(String[] args) {
		// http://square.github.io/retrofit/
		// https://github.com/Netflix/feign

		GitHubApi api = Reflector.build(GitHubApi.class, null);

		String response = api.contributors("anthavio", "hatatitla");
	}

	@Headers("Content-type: application/json; charset=utf-8")
	public static interface GitHubApi {

		@Operation(method = HttpMethod.GET, path = "/somethig/{awful}")
		@Headers("Custom: {custom}")
		public String something(@Param("awful") String awful, @Param("custom") String custom);

		@Operation("GET /repos/{owner}/{repo}/contributors")
		public String contributors(@Param("owner") String owner, @Param("repo") String repo);

	}
}
