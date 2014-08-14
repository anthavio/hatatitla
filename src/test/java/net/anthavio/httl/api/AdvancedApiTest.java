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
import net.anthavio.httl.marshall.MediaTypeMarshaller;
import net.anthavio.httl.marshall.SimpleXmlMarshaller;
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
public class AdvancedApiTest {

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
	public void testWithUrlParameter() {
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).build();

		// Given
		WithUrlParameter api = HttlApiBuilder.with(sender).addParam("api-key", "zxzxzx-zxzxzx-zxzxzx-zxzxzx")
				.build(WithUrlParameter.class);

		//When
		String json = "{ \"name\" : \"Kvído Vymětal\" }";
		String response = api.store("replaced", json);

		// Then
		Assertions.assertThat(response).isEqualTo(json);

		HttlRequest request = transport.getLastRequest();
		Assertions.assertThat(request.getMethod()).isEqualTo(Method.PUT);
		Assertions.assertThat(request.getPathAndQuery()).isEqualTo("/store/replaced?api-key=zxzxzx-zxzxzx-zxzxzx-zxzxzx");
		Assertions.assertThat(request.getFirstHeader("Content-Type")).isEqualTo("application/json; charset=utf-8");
		Assertions.assertThat(request.getFirstHeader("Accept")).isEqualTo("application/xml");

		Parameters parameters = request.getParameters();
		Assertions.assertThat(parameters.getFirst("api-key")).isEqualTo("zxzxzx-zxzxzx-zxzxzx-zxzxzx");
	}

	static interface WithUrlParameter {

		@RestCall("PUT /store/{else}")
		@RestHeaders({ "Content-Type: application/json", "Accept: application/xml" })
		public String store(@RestVar("else") String elseParam, @RestBody String body);
	}

	@Test
	public void testWithHeaderParamaters() {
		//Given
		Jackson2Marshaller jsonMarshaller = new Jackson2Marshaller(new ObjectMapper().configure(
				SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true));

		MediaTypeMarshaller marshaller = new MediaTypeMarshaller();
		marshaller.setMarshaller(jsonMarshaller, "application/json");
		marshaller.setMarshaller(new SimpleXmlMarshaller(), "application/xml");
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).setMarshaller(marshaller).build();

		WithHeaderParameters api = HttlApiBuilder.build(WithHeaderParameters.class, sender);
		TestBodyBean input = new TestBodyBean("Kvído Vymětal", new Date(), 999);

		// When
		TestBodyBean asXml = api.postBody("application/json", "application/xml", input);
		// Then
		Assertions.assertThat(transport.getLastRequest().getMediaType()).isEqualTo("application/json");
		Assertions.assertThat(transport.getLastRequest().getFirstHeader("Accept")).isEqualTo("application/xml");
		Assertions.assertThat(asXml).isEqualToComparingFieldByField(input);

		// When
		TestBodyBean asJson = api.postBody("application/xml", "application/json", input);
		// Then
		Assertions.assertThat(transport.getLastRequest().getMediaType()).isEqualTo("application/xml");
		Assertions.assertThat(transport.getLastRequest().getFirstHeader("Accept")).isEqualTo("application/json");
		Assertions.assertThat(asJson).isEqualToComparingFieldByField(input);
	}

	static interface WithHeaderParameters {

		@RestCall("POST /something")
		@RestHeaders({ "Content-Type: {content-type}", "Accept: {accept}" })
		public TestBodyBean postBody(@RestVar("content-type") String contentType, @RestVar("accept") String accept,
				@RestBody TestBodyBean bean);
	}

	@Test
	public void genericListReturn() {
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).setUnmarshaller(new GsonUnmarshaller()).build();
		String json = "[{\"login\":\"login-value\",\"id\":123456,\"contributions\":333}]";
		transport.setStaticResponse(200, "application/json; charset=utf-8", json);

		//HttpURLSender sender = new HttpURLSender("https://api.github.com/");
		//HttpClient4Sender sender = new HttpClient4Sender("https://api.github.com/");

		// Build
		SomeApi api = HttlApiBuilder.build(SomeApi.class, sender);
		// Invoke
		List<Contributor> contributors = api.contributors("anthavio", "hatatitla");
		// Assert
		Assertions.assertThat(contributors.size()).isEqualTo(1);
		Assertions.assertThat(contributors.get(0).login).isEqualTo("login-value");
		Assertions.assertThat(contributors.get(0).contributions).isEqualTo(333);
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

		@RestCall("GET /repos/{owner}/{repo}/contributors")
		List<Contributor> contributors(@RestVar("owner") String owner, @RestVar("repo") String repo);

	}

	static class Contributor {
		String login;
		int contributions;
	}

	static class TestBodyBean {

		private String name;

		private Date date;

		private Integer number;

		public TestBodyBean() {
			//jaxb
		}

		public TestBodyBean(String name, Date date, Integer number) {
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
			TestBodyBean other = (TestBodyBean) obj;
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
