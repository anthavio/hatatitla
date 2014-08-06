package net.anthavio.httl.auth;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import net.anthavio.httl.HttlSender;
import net.anthavio.httl.api.HttpMethod;
import net.anthavio.httl.util.HttpHeaderUtil;

public class OAuth2Builder {

	private boolean strict = true; //some APIs has relaxed OAuth rules

	private HttpMethod authMethod = HttpMethod.GET;

	private URL authUrl; //authorization endpoint url

	private String clientId;

	private String clientSecret;

	private String authResponseType = "code"; //or token - response_type for authentication

	//private String scope;

	private String redirectUri; //callback url

	//private String state; 

	private HttpMethod tokenMethod = HttpMethod.POST;

	private URL tokenUrl; //token endpoint url

	private List<String[]> customAuthParams;

	private List<String[]> customTokenParams;

	private HttlSender sender;

	private String authQuery;

	private String tokenQuery;

	private String tokenUrlPath;

	public OAuth2Builder(HttlSender sender) {
		if (sender == null) {
			throw new IllegalArgumentException("Null HttlSender");
		}
		this.sender = sender;
	}

	public boolean isStrict() {
		return strict;
	}

	public void setStrict(boolean strict) {
		this.strict = strict;
	}

	public URL getAuthUrl() {
		return authUrl;
	}

	public OAuth2Builder setAuthUrl(String authUrl) {
		if (authUrl == null || authUrl.isEmpty()) {
			throw new IllegalArgumentException("Authorization URL is required");
		}

		if (!authUrl.startsWith("http")) {
			authUrl = HttpHeaderUtil.joinUrlParts(sender.getConfig().getUrl().toString(), authUrl);
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

	public OAuth2Builder setTokenUrl(String tokenUrl) {
		if (tokenUrl == null || tokenUrl.isEmpty()) {
			if (strict) {
				throw new IllegalArgumentException("Token URL is required");
			}
		} else {

			if (!tokenUrl.startsWith("http")) {
				tokenUrl = HttpHeaderUtil.joinUrlParts(sender.getConfig().getUrl().toString(), tokenUrl);
			}
			try {
				this.tokenUrl = new URL(tokenUrl);
				URL senderUrl = sender.getConfig().getUrl();
				if (!senderUrl.getProtocol().equals(this.tokenUrl.getProtocol())
						|| !senderUrl.getHost().equals(this.tokenUrl.getHost()) || senderUrl.getPort() != this.tokenUrl.getPort()) {
					throw new IllegalArgumentException("Mismatch between HttlSender URL: " + senderUrl + " and Token URL: "
							+ this.tokenUrl);
				}

			} catch (MalformedURLException mux) {
				throw new IllegalArgumentException("Token URL is malformed: " + tokenUrl);
			}

		}
		return this;
	}

	public String getClientId() {
		return clientId;
	}

	public OAuth2Builder setClientId(String clientId) {
		this.clientId = clientId;
		return this;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public OAuth2Builder setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
		return this;
	}

	public String getAuthResponseType() {
		return authResponseType;
	}

	public OAuth2Builder setAuthResponseType(String responseType) {
		this.authResponseType = responseType;
		return this;
	}

	public String getRedirectUri() {
		return redirectUri;
	}

	public OAuth2Builder setRedirectUri(String redirectUri) {
		this.redirectUri = redirectUri;
		return this;
	}

	public List<String[]> getCustomAuthParams() {
		return customAuthParams;
	}

	public OAuth2Builder setCustomParam(String name, String value) {
		if (this.customAuthParams != null) {
			this.customAuthParams = new ArrayList<String[]>();
		}
		customAuthParams.add(new String[] { name, value });
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

		if (customAuthParams != null) {
			for (String[] param : customAuthParams) {
				OAuth2.append(authQuery, param[0], param[1]);
			}
		}

		if (customTokenParams != null) {
			for (String[] param : customTokenParams) {
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
