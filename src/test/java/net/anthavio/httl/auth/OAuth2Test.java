package net.anthavio.httl.auth;

import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

import net.anthavio.httl.HttlBuilder;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequest.Method;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.util.HttlUtil;
import net.anthavio.httl.util.MockTransport;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class OAuth2Test {

	String clientId = "app-12345";
	String clientSecret = "secret-12345";
	String state = "state-12345";
	String scope = "scope1 scope2 scope3";
	String redirectUri = "http://localhost:8888/oauth/callback";

	@Test
	public void getAuthorizationUrl() throws Exception {

		// When
		OAuth2Builder builder = new OAuth2Builder().setAuthorizationUrl("https://login.example.com/oauth/authorize")
				.setTokenEndpointUrl("https://login.example.com/oauth/token").setRedirectUri(redirectUri).setClientId(clientId)
				.setClientSecret(clientSecret);

		OAuth2 oauth2 = builder.build();
		URL authorizeUrl = new URL(oauth2.getAuthorizationUrl(scope, state));
		List<NameValuePair> parameters = URLEncodedUtils.parse(authorizeUrl.getQuery(), Charset.forName("utf-8"));

		// Then
		ParameterAssert.assertThat(parameters).contains("client_id", clientId);
		ParameterAssert.assertThat(parameters).contains("response_type", "code");
		ParameterAssert.assertThat(parameters).contains("redirect_uri", redirectUri);

		ParameterAssert.assertThat(parameters).contains("state", state);
		ParameterAssert.assertThat(parameters).contains("scope", scope);

		// And - custom params

		builder.setAuthParam("custom_param_name", "custom_param_value");
		builder.setAuthAccessType("offline");
		builder.setAuthResponseType("token");

		// When
		oauth2 = builder.build();
		authorizeUrl = new URL(oauth2.getAuthorizationUrl(scope, state));
		parameters = URLEncodedUtils.parse(authorizeUrl.getQuery(), Charset.forName("utf-8"));

		// Then
		ParameterAssert.assertThat(parameters).contains("custom_param_name", "custom_param_value");
		ParameterAssert.assertThat(parameters).contains("response_type", "token");
		ParameterAssert.assertThat(parameters).contains("access_type", "offline");
		//ParameterAssert.assertThat(params).contains("client_secret", clientSecret);
	}

	@Test
	public void accessToken() throws Exception {
		MockTransport transport = HttlBuilder.mock("https://login.example.com").build();
		ObjectMapper mapper = new ObjectMapper();
		OAuthTokenResponse mockedTokenResponse = new OAuthTokenResponse("mocked_access_token");
		StringWriter sw = new StringWriter();
		mapper.writeValue(sw, mockedTokenResponse);
		transport.setStaticResponse(200, "application/json", sw.toString());

		HttlSender tokenSender = HttlBuilder.sender(transport).build();

		OAuth2Builder builder = new OAuth2Builder().setTokenEndpoint(tokenSender, "/oauth/token")
				.setAuthorizationUrl("/oauth/authorize").setRedirectUri(redirectUri).setClientId(clientId)
				.setClientSecret(clientSecret);

		// When
		OAuth2 oauth = builder.build();
		OAuthTokenResponse tokenResponse = oauth.access("made-up-code").get();

		HttlRequest request = transport.getLastRequest();
		String body = (String) request.getBody().getPayload();
		List<NameValuePair> parameters = URLEncodedUtils.parse(body, Charset.forName("utf-8"));

		// Then
		String[] splitted = HttlUtil.splitUrlPath(request.getUrl().toString());
		Assertions.assertThat(splitted[0]).isEqualTo("https://login.example.com");
		Assertions.assertThat(splitted[1]).startsWith("/oauth/token");

		Assertions.assertThat(request.getMethod()).isEqualTo(Method.POST);
		Assertions.assertThat(request.getParameters()).isEmpty();
		ParameterAssert.assertThat(parameters).contains("client_id", clientId);
		ParameterAssert.assertThat(parameters).contains("client_secret", clientSecret);
		ParameterAssert.assertThat(parameters).contains("grant_type", "authorization_code");
		ParameterAssert.assertThat(parameters).contains("redirect_uri", redirectUri);
		ParameterAssert.assertThat(parameters).contains("code", "made-up-code");

		Assertions.assertThat(tokenResponse.getAccess_token()).isEqualTo(mockedTokenResponse.getAccess_token());

		//And

		builder.setTokenHttpMethod(Method.GET);
		builder.setTokenParam("custom_param_name", "custom_param_value");
		builder.setTokenHeader("custom_header_name", "custom_header_value");

		oauth = builder.build();
		tokenResponse = oauth.access("made-up-code").get();

		// Then
		request = transport.getLastRequest();
		Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET);
		Assertions.assertThat(request.getHeader("custom_header_name").get(0)).isEqualTo("custom_header_value");
		Assertions.assertThat(request.getBody()).isNull();

		parameters = URLEncodedUtils.parse(request.getUrl().getQuery(), Charset.forName("utf-8"));
		ParameterAssert.assertThat(parameters).contains("client_id", clientId);
		ParameterAssert.assertThat(parameters).contains("client_secret", clientSecret);
		ParameterAssert.assertThat(parameters).contains("grant_type", "authorization_code");
		ParameterAssert.assertThat(parameters).contains("redirect_uri", redirectUri);
		ParameterAssert.assertThat(parameters).contains("code", "made-up-code");
		ParameterAssert.assertThat(parameters).contains("custom_param_name", "custom_param_value");
	}

	@Test
	public void refreshToken() throws Exception {
		MockTransport transport = HttlBuilder.mock("https://login.example.com").build();
		ObjectMapper mapper = new ObjectMapper();
		OAuthTokenResponse mockedTokenResponse = new OAuthTokenResponse("mocked_access_token");
		StringWriter sw = new StringWriter();
		mapper.writeValue(sw, mockedTokenResponse);
		transport.setStaticResponse(200, "application/json", sw.toString());

		HttlSender tokenSender = HttlBuilder.sender(transport).build();

		OAuth2Builder builder = new OAuth2Builder().setTokenEndpoint(tokenSender, "/oauth/token")
				.setAuthorizationUrl("/oauth/authorize").setRedirectUri(redirectUri).setClientId(clientId)
				.setClientSecret(clientSecret);

		OAuth2 oauth = builder.build();
		// When
		OAuthTokenResponse tokenResponse = oauth.refresh("made-up-offline-token").get();
		// Then
		Assertions.assertThat(tokenResponse.getAccess_token()).isEqualTo(mockedTokenResponse.getAccess_token());

	}

	public static class ParameterAssert extends AbstractAssert<ParameterAssert, List<NameValuePair>> {

		protected ParameterAssert(List<NameValuePair> actual) {
			super(actual, ParameterAssert.class);
		}

		public static ParameterAssert assertThat(List<NameValuePair> actual) {
			return new ParameterAssert(actual);
		}

		public ParameterAssert contains(String name, String value) {
			isNotNull();
			for (NameValuePair item : actual) {
				if (item.getName().equals(name)) {
					if (item.getValue().equals(value)) {
						return this;
					} else {
						failWithMessage("Expected value to be <%s> but was <%s> for parameter %s", value, item.getValue(), name);
					}
				}
			}
			failWithMessage("Paramater <%s> not found in <%s>", name, actual);

			return this;
		}
	}
}
