package net.anthavio.httl.auth;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequest.Method;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.util.HttlUtil;

public class OAuth2Builder {

	private static final List<String[]> EMPTY = Collections.emptyList();

	private boolean strict = true; //some APIs has relaxed OAuth rules

	private URL authUrl; //authorization endpoint url

	private String clientId; // client_id=...

	private String clientSecret;// client_secret=...

	private String authResponseType = "code"; //response_type=code|token - response_type for authentication

	private String authAccessType; // access_type=online|offline

	private String redirectUri; //redirect_uri=http://... - callback url

	private HttlRequest.Method tokenHttpMethod = Method.POST; //some weirdos are using GET

	private URL tokenUrl; //token endpoint url

	private List<String[]> authParams;

	private List<String[]> tokenHeaders;

	private List<String[]> tokenParams;

	private HttlSender sender;

	private String authQuery; //build() product

	private String tokenQuery; //build() product

	public OAuth2Builder() {
	}

	public boolean isStrict() {
		return strict;
	}

	public OAuth2Builder setStrict(boolean strict) {
		this.strict = strict;
		return this;
	}

	public URL getAuthUrl() {
		return authUrl;
	}

	public OAuth2Builder setAuthorizationUrl(String authUrl) {
		if (authUrl == null || authUrl.isEmpty()) {
			throw new IllegalArgumentException("Authorization URL is required");
		}

		if (!authUrl.startsWith("http") && sender != null) {
			authUrl = HttlUtil.joinUrlParts(sender.getConfig().getUrl().toString(), authUrl);
		}
		try {
			this.authUrl = new URL(authUrl);
		} catch (MalformedURLException mux) {
			throw new IllegalArgumentException("Authorization URL is malformed: " + authUrl);
		}

		return this;
	}

	public URL getTokenUrl() {
		return tokenUrl;
	}

	public OAuth2Builder setTokenEndpointUrl(String tokenUrl) {
		String host = HttlUtil.splitUrlPath(tokenUrl)[0];
		return setTokenEndpoint(HttlSender.url(host).build(), tokenUrl);
	}

	public OAuth2Builder setTokenEndpoint(HttlSender sender, String url) {
		if (sender == null) {
			throw new IllegalArgumentException("Null HttlSender");
		}
		this.sender = sender;

		if (url == null || url.isEmpty()) {
			throw new IllegalArgumentException("Token URL is required");
		}
		if (!url.startsWith("http")) {
			url = HttlUtil.joinUrlParts(sender.getConfig().getUrl().toString(), url);
		}
		try {
			this.tokenUrl = new URL(url);
			URL senderUrl = sender.getConfig().getUrl();
			if (!senderUrl.getProtocol().equals(this.tokenUrl.getProtocol())
					|| !senderUrl.getHost().equals(this.tokenUrl.getHost()) || senderUrl.getPort() != this.tokenUrl.getPort()) {
				throw new IllegalArgumentException("Mismatch between HttlSender URL: " + senderUrl + " and Token URL: "
						+ this.tokenUrl);
			}

		} catch (MalformedURLException mux) {
			throw new IllegalArgumentException("Token URL is malformed: " + url);
		}

		return this;
	}

	public HttlRequest.Method getTokenHttpMethod() {
		return tokenHttpMethod;
	}

	public OAuth2Builder setTokenHttpMethod(HttlRequest.Method tokenHttpMethod) {
		this.tokenHttpMethod = tokenHttpMethod;
		return this;
	}

	/**
	 * @return client_id
	 */
	public String getClientId() {
		return clientId;
	}

	/**
	 * @param clientId client_id
	 */
	public OAuth2Builder setClientId(String clientId) {
		this.clientId = clientId;
		return this;
	}

	/**
	 * @return client_secret parameter
	 */
	public String getClientSecret() {
		return clientSecret;
	}

	/**
	 * @param clientSecret client_secret parameter
	 */
	public OAuth2Builder setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
		return this;
	}

	/**
	 * response_type=code|token
	 * 
	 * @return response_type auth parameter
	 */
	public String getAuthResponseType() {
		return authResponseType;
	}

	/**
	 * response_type=code|token
	 * 
	 * @param responseType response_type auth parameter
	 */
	public OAuth2Builder setAuthResponseType(String responseType) {
		this.authResponseType = responseType;
		return this;
	}

	/**
	 * Optional authorization parameter
	 * access_type=online|offline
	 * 
	 * @return access_type auth parameter
	 */
	public String getAuthAccessType() {
		return authAccessType;
	}

	/**
	 * Optional authorization parameter
	 * access_type=online|offline
	 * 
	 * @param accessType access_type auth parameter
	 */
	public OAuth2Builder setAuthAccessType(String accessType) {
		this.authAccessType = accessType;
		return this;
	}

	/**
	 * @return redirect_uri parameter
	 */
	public String getRedirectUri() {
		return redirectUri;
	}

	/**
	 * @param redirectUri redirect_uri parameter
	 */
	public OAuth2Builder setRedirectUri(String redirectUri) {
		this.redirectUri = redirectUri;
		return this;
	}

	public List<String[]> getAuthParams() {
		return authParams;
	}

	public OAuth2Builder setAuthParam(String name, String value) {
		if (this.authParams == null) {
			this.authParams = new ArrayList<String[]>();
		}
		authParams.add(new String[] { name, value });
		return this;
	}

	public List<String[]> getTokenParams() {
		return tokenParams;
	}

	public OAuth2Builder setTokenParam(String name, String value) {
		if (this.tokenParams == null) {
			this.tokenParams = new ArrayList<String[]>();
		}
		tokenParams.add(new String[] { name, value });
		return this;
	}

	public List<String[]> getTokenHeaders() {
		return tokenHeaders;
	}

	public OAuth2Builder setTokenHeader(String name, String value) {
		if (this.tokenHeaders == null) {
			this.tokenHeaders = new ArrayList<String[]>();
		}
		tokenHeaders.add(new String[] { name, value });
		return this;
	}

	public StringBuilder getAuthQueryBuilder() {
		return new StringBuilder(authQuery);
	}

	public StringBuilder getTokenQueryBuilder() {
		return new StringBuilder(tokenQuery);
	}

	public OAuth2 build() {

		if (authUrl == null) {
			throw new IllegalStateException("Authorization URL is required");
		}
		/*
		if (!authUrl.startsWith("http") && sender != null) {

		}
		*/
		if (tokenUrl == null) {
			if (strict) {
				throw new IllegalStateException("Token URL is required");
			}
		}

		StringBuilder authQuery = new StringBuilder();
		StringBuilder tokenQuery = new StringBuilder();

		if (authResponseType != null) {
			OAuth2.append(authQuery, "response_type", authResponseType);
		}

		if (authAccessType != null) {
			OAuth2.append(authQuery, "access_type", authAccessType);
		}

		if (redirectUri == null || redirectUri.isEmpty()) {
			if (strict) {
				throw new IllegalStateException("redirect_uri is required");
			}
		} else {
			OAuth2.append(authQuery, "redirect_uri", redirectUri);
			OAuth2.append(tokenQuery, "redirect_uri", redirectUri);
		}

		if (clientId == null || clientId.isEmpty()) {
			if (strict) {
				throw new IllegalStateException("client_id is required");
			}
		} else {
			OAuth2.append(authQuery, "client_id", clientId);
			OAuth2.append(tokenQuery, "client_id", clientId);
		}

		if (clientSecret == null || clientSecret.isEmpty()) {
			if (strict) {
				throw new IllegalStateException("client_secret is required");
			}
		} else {
			OAuth2.append(tokenQuery, "client_secret", clientSecret);
		}

		if (authParams != null) {
			for (String[] param : authParams) {
				OAuth2.append(authQuery, param[0], param[1]);
			}
		}

		if (tokenParams != null) {
			for (String[] param : tokenParams) {
				OAuth2.append(tokenQuery, param[0], param[1]);
			}
		}

		this.authQuery = authQuery.toString();
		this.tokenQuery = tokenQuery.toString();

		return new OAuth2(sender, this);
	}

	/*
		public OAuth2AuthUrlBuilder setScope(String... scope) {
			if (scope != null && scope.length > 0) {
				StringBuilder sb = new StringBuilder();
				for (String string : scope) {
					if (string != null && !string.isEmpty()) {
						sb.append(string).append(' ');
					}
				}
				if (sb.length() > 1) {
					sb.deleteCharAt(sb.length() - 1);
				}
				this.scope = sb.toString();
			}
			return this;
		}
	*/

}
