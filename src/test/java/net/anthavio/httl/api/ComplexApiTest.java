package net.anthavio.httl.api;

import java.util.Date;
import java.util.List;

import net.anthavio.httl.HttpSender.Multival;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderRequest.Method;
import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.inout.GsonExtractorFactory;
import net.anthavio.httl.util.MockSender;

import org.fest.assertions.api.Assertions;
import org.testng.annotations.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class ComplexApiTest {

	@Test
	public void testSomeApiOptions() {

		MockSender sender = new MockSender();
		// Set api-key header into every passing request

		// Build
		SomeApi api = ApiBuilder.with(sender).addHeader("api-key", "zxzxzx-zxzxzx-zxzxzx-zxzxzx").build(SomeApi.class);

		// Invoke
		SenderResponse response = api.options("trololo", "ISO-8859-4", new int[] { 3, 2, 1 });

		// Assert
		Assertions.assertThat(response.getHttpStatusCode()).isEqualTo(200);

		SenderRequest request = sender.getLastRequest();
		Assertions.assertThat(request.getMethod()).isEqualTo(Method.OPTIONS);
		Assertions.assertThat(request.getUrlPath()).isEqualTo("/something/trololo");
		Assertions.assertThat(request.getFirstHeader("api-key")).isEqualTo("zxzxzx-zxzxzx-zxzxzx-zxzxzx");
		Assertions.assertThat(request.getFirstHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
		Assertions.assertThat(request.getFirstHeader("Accept-Charset")).isEqualTo("ISO-8859-4"); //replaced global utf-8

		Multival parameters = request.getParameters();
		Assertions.assertThat(parameters.getFirst("numbers")).isEqualTo("3");
		Assertions.assertThat(parameters.getLast("numbers")).isEqualTo("1");
	}

	@Test
	public void testSomeApiPut() {
		MockSender sender = new MockSender();

		// Build
		SomeApi api = ApiBuilder.with(sender).addParam("api-key", "zxzxzx-zxzxzx-zxzxzx-zxzxzx").build(SomeApi.class);

		// Invoke
		String json = "{ \"name\" : \"Quido Guido\" }";
		String response = api.put("whatever", json);

		// Assert
		Assertions.assertThat(response).isEqualTo(json);

		SenderRequest request = sender.getLastRequest();
		Assertions.assertThat(request.getMethod()).isEqualTo(Method.PUT);
		Assertions.assertThat(request.getUrlPath()).isEqualTo("/store/whatever");
		Assertions.assertThat(request.getFirstHeader("api-key")).isNull();
		Assertions.assertThat(request.getFirstHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
		Assertions.assertThat(request.getFirstHeader("Accept-Charset")).isEqualTo("utf-8");

		Multival parameters = request.getParameters();
		Assertions.assertThat(parameters.getFirst("api-key")).isEqualTo("zxzxzx-zxzxzx-zxzxzx-zxzxzx");
	}

	@Test
	public void testSomeApiPostBody() {
		MockSender sender = new MockSender();
		// Build
		SomeApi api = ApiBuilder.build(SomeApi.class, sender);

		SomeBean input = new SomeBean("Quido Guido", new Date(), 999);
		// Invoke
		SomeBean asXml = api.postBody("application/xml", "application/xml", input);
		// Assert
		Assertions.assertThat(asXml).isEqualsToByComparingFields(input);

		// Invoke
		SomeBean asJson = api.postBody("application/json", "application/json", input);
		// Assert
		Assertions.assertThat(asJson).isEqualsToByComparingFields(input);
	}

	@Test
	public void testListReturn() {
		MockSender sender = new MockSender();
		String json = "[{\"login\":\"anthavio\",\"id\":647317,\"contributions\":119}]";
		sender.setStaticResponse(200, "application/json; charset=utf-8", json);

		//HttpURLSender sender = new HttpURLSender("https://api.github.com/");
		//HttpClient4Sender sender = new HttpClient4Sender("https://api.github.com/");

		sender.setResponseExtractorFactory(new GsonExtractorFactory(), "application/json");
		// Build
		SomeApi api = ApiBuilder.build(SomeApi.class, sender);
		// Invoke
		List<Contributor> contributors = api.contributors("anthavio", "hatatitla");
		// Assert
		Assertions.assertThat(contributors.size()).isPositive();
		Assertions.assertThat(contributors.get(0).login).isNotEmpty();
		Assertions.assertThat(contributors.get(0).contributions).isPositive();
	}

	@Headers({ //
	"Content-Type: application/json; charset=utf-8", //
			"Accept: application/json", //
			"Accept-Charset: utf-8",//
			"User-Agent: Hatatitla"//
	})
	static interface SomeApi {

		@Operation("OPTIONS /something/{awful}")
		@Headers("Accept-Charset: {acceptCharset}")
		public SenderResponse options(@Param("awful") String awful, @Param("acceptCharset") String accept,
				@Param("numbers") int[] numbers);

		@Operation("PUT /store/{else}")
		public String put(@Param("else") String elseParam, @Body String body);

		@Operation("POST /something")
		@Headers({ "Content-Type: {contentType}", "Accept: {accept}" })
		public SomeBean postBody(@Param("contentType") String contentType, @Param("accept") String accept,
				@Body SomeBean bean);

		@Operation("GET /repos/{owner}/{repo}/contributors")
		List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);

	}

	static class Contributor {
		String login;
		int contributions;
	}

	static class SomeBean {

		private String name;

		private Date date;

		private Integer number;

		public SomeBean() {
			//jaxb
		}

		public SomeBean(String name, Date date, Integer number) {
			this.name = name;
			this.date = date;
			this.number = number;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Date getDate() {
			return date;
		}

		public void setDate(Date date) {
			this.date = date;
		}

		public Integer getNumber() {
			return number;
		}

		public void setNumber(Integer number) {
			this.number = number;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((date == null) ? 0 : date.hashCode());
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((number == null) ? 0 : number.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SomeBean other = (SomeBean) obj;
			if (date == null) {
				if (other.date != null)
					return false;
			} else if (!date.equals(other.date))
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (number == null) {
				if (other.number != null)
					return false;
			} else if (!number.equals(other.number))
				return false;
			return true;
		}

	}
}
