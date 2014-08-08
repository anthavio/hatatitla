package net.anthavio.httl.api;

import java.util.Date;
import java.util.List;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequest.Method;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.HttlSender.Parameters;
import net.anthavio.httl.marshall.GsonUnmarshaller;
import net.anthavio.httl.marshall.Jackson2Marshaller;
import net.anthavio.httl.util.MockSenderConfig;
import net.anthavio.httl.util.MockTransport;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 
 * @author martin.vanek
 *
 */
public class ComplexApiTest {

	@Test
	public void testSomeApiOptions() {

		HttlSender sender = new MockSenderConfig().build();
		// Set api-key header into every passing request

		// Build
		SomeApi api = HttlApiBuilder.with(sender).addHeader("api-key", "zxzxzx-zxzxzx-zxzxzx-zxzxzx").build(SomeApi.class);

		// Invoke
		HttlResponse response = api.options("trololo", "ISO-8859-4", new int[] { 3, 2, 1 });

		// Assert
		Assertions.assertThat(response.getHttpStatusCode()).isEqualTo(200);

		HttlRequest request = response.getRequest();
		Assertions.assertThat(request.getMethod()).isEqualTo(Method.OPTIONS);
		Assertions.assertThat(request.getPathAndQuery()).isEqualTo("/something/trololo?numbers=3&numbers=2&numbers=1");
		Assertions.assertThat(request.getFirstHeader("api-key")).isEqualTo("zxzxzx-zxzxzx-zxzxzx-zxzxzx");
		Assertions.assertThat(request.getFirstHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
		Assertions.assertThat(request.getFirstHeader("Accept-Charset")).isEqualTo("ISO-8859-4"); //replaced global utf-8

		Parameters parameters = request.getParameters();
		Assertions.assertThat(parameters.getFirst("numbers")).isEqualTo("3");
		Assertions.assertThat(parameters.getLast("numbers")).isEqualTo("1");
	}

	@Test
	public void testSomeApiPut() {
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).build();

		// Build
		SomeApi api = HttlApiBuilder.with(sender).addParam("api-key", "zxzxzx-zxzxzx-zxzxzx-zxzxzx").build(SomeApi.class);

		// Invoke
		String json = "{ \"name\" : \"Kvído Vymětal\" }";
		String response = api.put("whatever", json);

		// Assert
		Assertions.assertThat(response).isEqualTo(json);

		HttlRequest request = transport.getLastRequest();
		Assertions.assertThat(request.getMethod()).isEqualTo(Method.PUT);
		Assertions.assertThat(request.getPathAndQuery()).isEqualTo("/store/whatever?api-key=zxzxzx-zxzxzx-zxzxzx-zxzxzx");
		Assertions.assertThat(request.getFirstHeader("api-key")).isNull();
		Assertions.assertThat(request.getFirstHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
		Assertions.assertThat(request.getFirstHeader("Accept-Charset")).isEqualTo("utf-8");

		Parameters parameters = request.getParameters();
		Assertions.assertThat(parameters.getFirst("api-key")).isEqualTo("zxzxzx-zxzxzx-zxzxzx-zxzxzx");
	}

	@Test
	public void testSomeApiPostBody() {
		//Given
		Jackson2Marshaller marshaller = new Jackson2Marshaller(new ObjectMapper().configure(
				SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true));
		HttlSender sender = new MockSenderConfig().setMarshaller(marshaller).build();

		SomeApi api = HttlApiBuilder.build(SomeApi.class, sender);
		SomeBodyBean input = new SomeBodyBean("Kvído Vymětal", new Date(), 999);

		// When
		SomeBodyBean asXml = api.postBody("application/xml", "application/xml", input);
		// Then
		Assertions.assertThat(asXml).isEqualToComparingFieldByField(input);

		// When
		SomeBodyBean asJson = api.postBody("application/json", "application/json", input);
		// Then
		Assertions.assertThat(asJson).isEqualToComparingFieldByField(input);
	}

	@Test
	public void genericListReturn() {
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).setUnmarshaller(new GsonUnmarshaller()).build();
		String json = "[{\"login\":\"anthavio\",\"id\":647317,\"contributions\":119}]";
		transport.setStaticResponse(200, "application/json; charset=utf-8", json);

		//HttpURLSender sender = new HttpURLSender("https://api.github.com/");
		//HttpClient4Sender sender = new HttpClient4Sender("https://api.github.com/");

		// Build
		SomeApi api = HttlApiBuilder.build(SomeApi.class, sender);
		// Invoke
		List<Contributor> contributors = api.contributors("anthavio", "hatatitla");
		// Assert
		Assertions.assertThat(contributors.size()).isPositive();
		Assertions.assertThat(contributors.get(0).login).isNotEmpty();
		Assertions.assertThat(contributors.get(0).contributions).isPositive();
	}

	@RestHeaders({ //
	"Content-Type: application/json; charset=utf-8", //
			"Accept: application/json", //
			"Accept-Charset: utf-8",//
			"User-Agent: Hatatitla"//
	})
	static interface SomeApi {

		@RestCall("OPTIONS /something/{awful}")
		@RestHeaders("Accept-Charset: {accept-charset}")
		public HttlResponse options(@RestVar("awful") String awful, @RestVar("accept-charset") String accept,
				@RestVar("numbers") int[] numbers);

		@RestCall("PUT /store/{else}")
		public String put(@RestVar("else") String elseParam, @RestBody String body);

		//@RestCall("GET /zzz")
		//public void complexParam(@RestArg("") ComplexParam param);

		@RestCall("POST /something")
		@RestHeaders({ "Content-Type: {content-type}", "Accept: {accept}" })
		public SomeBodyBean postBody(@RestVar("content-type") String contentType, @RestVar("accept") String accept,
				@RestBody SomeBodyBean bean);

		@RestCall("GET /repos/{owner}/{repo}/contributors")
		List<Contributor> contributors(@RestVar("owner") String owner, @RestVar("repo") String repo);

	}

	static class Contributor {
		String login;
		int contributions;
	}

	static class SomeBodyBean {

		private String name;

		private Date date;

		private Integer number;

		public SomeBodyBean() {
			//jaxb
		}

		public SomeBodyBean(String name, Date date, Integer number) {
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
			SomeBodyBean other = (SomeBodyBean) obj;
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
