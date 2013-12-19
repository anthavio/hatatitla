package net.anthavio.httl;

import static org.fest.assertions.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.anthavio.httl.Authentication;
import net.anthavio.httl.GetRequest;
import net.anthavio.httl.HttpClient4Config;
import net.anthavio.httl.HttpClient4Sender;
import net.anthavio.httl.HttpURLConfig;
import net.anthavio.httl.HttpURLSender;
import net.anthavio.httl.PostRequest;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.Authentication.Scheme;
import net.anthavio.httl.SenderRequest.Method;

import org.fest.assertions.api.Fail;
import org.testng.annotations.Test;


/**
 * 
 * @author martin.vanek
 *
 */
public class RequestTest {

	@Test
	public void requestUrl() {
		HttpURLSender sender = new HttpURLSender("www.somewhere.com/path");
		assertThat(sender.getConfig().getHostUrl().toString()).isEqualTo("http://www.somewhere.com"); //add http prefix and remove path suffix
		//SimpleHttpSender sender = null;

		GetRequest rGet = new GetRequest("/path");
		String[] paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path");
		assertThat(paq[1]).isNull();

		rGet.addParameter("p0", (Object) null);
		paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path?p0");
		assertThat(paq[1]).isEqualTo("p0");

		rGet.addParameter("p1", "");
		paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path?p0&p1=");
		assertThat(paq[1]).isEqualTo("p0&p1=");

		rGet.addParameter("p2", 1);
		paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path?p0&p1=&p2=1");
		assertThat(paq[1]).isEqualTo("p0&p1=&p2=1");

		rGet.addMartix("m0", null);//null is skipped by default
		paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path;m0?p0&p1=&p2=1");
		assertThat(paq[1]).isEqualTo("p0&p1=&p2=1");

		rGet.addMartix("m1", "");//blank is not skipped by default
		paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path;m0;m1=?p0&p1=&p2=1");
		assertThat(paq[1]).isEqualTo("p0&p1=&p2=1");

		rGet.addParameter(";m2", 2);
		paq = sender.getPathAndQuery(rGet);
		assertThat(paq[0]).isEqualTo("/path;m0;m1=;m2=2?p0&p1=&p2=1");
		assertThat(paq[1]).isEqualTo("p0&p1=&p2=1");

		//now with POST request

		PostRequest rPost = new PostRequest("/path");
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path");
		assertThat(paq[1]).isNull();

		rPost.addParameter("p0", (Object) null);
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path");
		assertThat(paq[1]).isEqualTo("p0");

		rPost.addParameter("p1", "");
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path");
		assertThat(paq[1]).isEqualTo("p0&p1=");

		rPost.addParameter("p2", 1);
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path");
		assertThat(paq[1]).isEqualTo("p0&p1=&p2=1");

		rPost.addMartix("m0", null);
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path;m0");
		assertThat(paq[1]).isEqualTo("p0&p1=&p2=1");

		rPost.addMartix("m1", ""); //add matrix parameter
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path;m0;m1=");
		assertThat(paq[1]).isEqualTo("p0&p1=&p2=1");

		rPost.addParameter(";m2", 2); //add matrix parameter
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path;m0;m1=;m2=2");
		assertThat(paq[1]).isEqualTo("p0&p1=&p2=1");

		//setting request body moves query parameters from body to path 
		rPost.setBody(new ByteArrayInputStream(new byte[0]), "application/json; charset=utf-8");
		paq = sender.getPathAndQuery(rPost);
		assertThat(paq[0]).isEqualTo("/path;m0;m1=;m2=2?p0&p1=&p2=1");
		assertThat(paq[1]).isEqualTo("p0&p1=&p2=1");
	}

	@Test
	public void requestParams() {
		SenderRequest request = new GetRequest("/");
		assertThat(request.getMethod()).isEqualTo(Method.GET);
		assertThat(request.getUrlPath()).isEqualTo("/");
		assertThat(request.getParameters()).isEmpty();
		assertThat(request.getHeaders()).isEmpty();

		request = new PostRequest("/");
		assertThat(request.getMethod()).isEqualTo(Method.POST);
		assertThat(request.getUrlPath()).isEqualTo("/");
		assertThat(request.getParameters()).isEmpty();
		assertThat(request.getHeaders()).isEmpty();

		Map<String, List<String>> parameters = new HashMap<String, List<String>>();
		parameters.put("p1", Arrays.asList("y"));
		parameters.put("p2", Arrays.asList("a", "b", "c"));
		parameters.put("p3", Collections.EMPTY_LIST);
		parameters.put("p4", null);
		parameters.put("p5", Arrays.asList(""));

		Map<String, List<String>> headers = new HashMap<String, List<String>>();
		headers.put("h1", Arrays.asList("v1"));
		headers.put("h2", Arrays.asList("v21", "v22", "v23"));
		headers.put("h3", Collections.EMPTY_LIST);
		headers.put("h4", null);
		headers.put("h5", Arrays.asList(""));

		String path = "/path/to/somewhere?pname=pvalue";
		request = new PostRequest(path).setParameters(parameters).setHeaders(headers);
		assertThat(request.getMethod()).isEqualTo(Method.POST);
		assertThat(request.getUrlPath()).isEqualTo(path);

		//assertThat(request.getParameters()).isEqualTo(parameters);
		assertThat(request.getParameters().size()).isEqualTo(5);
		assertThat(request.getParameters().names().size()).isEqualTo(5);

		assertThat(request.getParameters().get("p1")).hasSize(1);
		assertThat(request.getParameters().getFirst("p1")).isEqualTo("y");
		assertThat(request.getParameters().getLast("p1")).isEqualTo("y");
		assertThat(request.getParameters().get("p2")).hasSize(3);
		assertThat(request.getParameters().getFirst("p2")).isEqualTo("a");
		assertThat(request.getParameters().getLast("p2")).isEqualTo("c");
		assertThat(request.getParameters().get("p3")).hasSize(0);
		assertThat(request.getParameters().getFirst("p3")).isNull();
		assertThat(request.getParameters().getLast("p3")).isNull();
		assertThat(request.getParameters().get("p4")).hasSize(1);
		assertThat(request.getParameters().getFirst("p4")).isNull();
		assertThat(request.getParameters().getLast("p4")).isNull();

		assertThat(request.getParameters().get("p5")).hasSize(1);
		assertThat(request.getParameters().getFirst("p5")).isEqualTo("");
		assertThat(request.getParameters().getLast("p5")).isEqualTo("");

		//assertThat(request.getHeaders()).isEqualTo(headers);
		assertThat(request.getHeaders().size()).isEqualTo(5);
		assertThat(request.getHeaders().names().size()).isEqualTo(5);

		assertThat(request.getHeaders().get("h1")).hasSize(1);
		assertThat(request.getHeaders().getFirst("h1")).isEqualTo("v1");
		assertThat(request.getHeaders().getLast("h1")).isEqualTo("v1");

		assertThat(request.getHeaders().get("h2")).hasSize(3);
		assertThat(request.getHeaders().getFirst("h2")).isEqualTo("v21");
		assertThat(request.getHeaders().getLast("h2")).isEqualTo("v23");

		assertThat(request.getHeaders().get("h3")).hasSize(0);
		assertThat(request.getHeaders().getFirst("h3")).isNull();
		assertThat(request.getHeaders().getLast("h3")).isNull();

		assertThat(request.getHeaders().get("h4")).hasSize(1);
		assertThat(request.getHeaders().getFirst("h4")).isNull();
		assertThat(request.getHeaders().getLast("h4")).isNull();

		assertThat(request.getHeaders().get("h5")).hasSize(1);
		assertThat(request.getHeaders().getFirst("h5")).isEqualTo("");
		assertThat(request.getHeaders().getLast("h5")).isEqualTo("");

		//parameteres allow duplicit names
		request.addParameter("param", "something");
		assertThat(request.getParameters().get("param")).hasSize(1);
		request.addParameter("param", "something");
		assertThat(request.getParameters().get("param")).hasSize(2);

		//header do not allow duplicit names
		request.setHeader("header", "something");
		assertThat(request.getHeaders().get("header")).hasSize(1);
		request.setHeader("header", "something");
		assertThat(request.getHeaders().get("header")).hasSize(1);
	}

	@Test
	public void simpleDefauts() {
		String url = "http://www.hostname.com:8080/path/to/somewhere";
		HttpURLSender sender = new HttpURLSender(url);
		assertThat(sender.getConfig().getHostUrl().toString()).isEqualTo("http://www.hostname.com:8080");// file(path) URL part is thrown away
		assertThat(sender.getConfig().getEncoding()).isEqualTo("utf-8");
		assertThat(sender.getConfig().getAuthentication()).isNull();

		//default authentication is BASIC and preepmtive
		Authentication authentication = new Authentication(Authentication.Scheme.BASIC, "user", "pass");
		assertThat(authentication.getScheme()).isEqualTo(Authentication.Scheme.BASIC);
		assertThat(authentication.getPreemptive()).isEqualTo(true);
	}

	@Test
	public void http4() {
		String url = "http://www.hostname.com:8080/path/to/somewhere";
		HttpClient4Config config = new HttpClient4Config(url);
		Authentication authentication = new Authentication(Scheme.DIGEST, "user", "pass", false);
		config.setAuthentication(authentication);
		HttpClient4Sender sender = new HttpClient4Sender(config);

		assertThat(sender.getConfig().getHostUrl().toString()).isEqualTo("http://www.hostname.com:8080"); // file(path) URL part is thrown away
		assertThat(sender.getConfig().getEncoding()).isEqualTo("utf-8");
		assertThat(sender.getConfig().getAuthentication().getPreemptive()).isFalse();
	}

	@Test
	public void senderBadParameters() {
		HttpURLSender sender;
		try {
			sender = new HttpURLSender((String) null);
			Fail.fail("Previous statemet must throw IllegalArgumentException");
		} catch (IllegalArgumentException iax) {
			//ok
		}
		try {
			sender = new HttpURLSender((HttpURLConfig) null);
			Fail.fail("Previous statemet must throw IllegalArgumentException");
		} catch (IllegalArgumentException iax) {
			//ok
		}
		try {
			sender = new HttpURLSender("");
			Fail.fail("Previous statemet must throw IllegalArgumentException");
		} catch (IllegalArgumentException iax) {
			//ok
		}
		try {
			sender = new HttpURLSender("http:///");
			Fail.fail("Previous statemet must throw IllegalArgumentException");
		} catch (IllegalArgumentException iax) {
			//ok
		}
	}

}
