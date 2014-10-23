package net.anthavio.httl.auth;

import java.util.List;

import net.anthavio.httl.HttlBuilderVisitor;
import net.anthavio.httl.HttlRequest.Method;
import net.anthavio.httl.HttlRequestBuilder;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.util.HttlUtil;

/**
 * http://tools.ietf.org/html/rfc6749
 * 
 * Google:
 * https://developers.google.com/accounts/docs/OAuth2Login
 * https://developers.google.com/accounts/docs/OAuth2WebServer
 * 
 * Facebook:
 * https://developers.facebook.com/docs/facebook-login/manually-build-a-login-flow/v2.0
 * https://developers.facebook.com/docs/facebook-login/access-tokens/
 * https://www.facebook.com/dialog/oauth
 * https://graph.facebook.com/oauth/access_token
 * 
 * Github:
 * https://developer.github.com/v3/oauth/
 * 
 * @author martin.vanek
 *
 */
public class OAuth2 {

	private final HttlSender sender;
	private final OAuth2Builder config;

	protected OAuth2(HttlSender sender, OAuth2Builder config) {
		this.sender = sender;
		this.config = config;
	}

	/**
	 * @return Builder for OAuth2
	 */
	public static OAuth2Builder Builder() {
		return new OAuth2Builder();
	}

	/**
	 * 
	 * @param  ' ' or , separated list of requited scopes
	 * @return authorization url for redirect
	 */
	public String getAuthorizationUrl(String scope) {
		return config.getAuthUrl() + "?" + getAuthorizationQuery(scope, null);
	}

	/**
	 * @param scope ' ' or , separated list of requited scopes
	 * @param state optional state to check on redirect
	 * @return authorization url for redirect
	 */
	public String getAuthorizationUrl(String scope, String state) {
		return config.getAuthUrl() + "?" + getAuthorizationQuery(scope, state);
	}

	/**
	 * @param scope ' ' or , separated list of requited scopes
	 * @param state optional state to check on redirect
	 */
	protected String getAuthorizationQuery(String scope, String state) {
		StringBuilder sb = config.getAuthQueryBuilder();

		if (scope != null) {
			append(sb, "scope", scope);
		} else if (config.isStrict()) {
			throw new IllegalArgumentException("Scope is mandatory");
		}

		if (state != null) {
			append(sb, "state", state);
		}

		return sb.toString();

	}

	public static interface SelectTypeBuildStep {

		public FinalBuildStep access(String code);

		public FinalBuildStep refresh(String refresh_token);

		public FinalBuildStep password(String username, String password);

	}

	public static interface FinalBuildStep {

		public FinalBuildStep visitor(HttlBuilderVisitor visitor);

		public OAuthTokenResponse get();

		public <T> T get(Class<T> tokenClass);

		public <T> T get(HttlResponseExtractor<T> extractor);

	}

	public SelectTypeBuildStep token() {
		return new TokenRequestBuilder(this);
	}

	/**
	 * Trade code for access_token
	 * 
	 * Standard OAuth for Web Server Applications
	 * http://tools.ietf.org/html/rfc6749#section-4.1
	 * 
	 */
	public FinalBuildStep access(String code) {
		return new TokenRequestBuilder(this).access(code);
	}

	/**
	 * Trade refresh_token for access_token
	 * 
	 * So called Offline access
	 * http://tools.ietf.org/html/rfc6749#section-6
	 * 
	 */
	public FinalBuildStep refresh(String refresh_token) {
		return new TokenRequestBuilder(this).refresh(refresh_token);
	}

	/**
	 * Trade username & password for access_token
	 * 
	 * Usualy only for privileged applications
	 * http://tools.ietf.org/html/rfc6749#section-4.3
	 */
	public FinalBuildStep password(String username, String password) {
		return new TokenRequestBuilder(this).password(username, password);
	}

	/**
	 * Trade code for access_token
	 */
	protected HttlRequestBuilder<?> buildCodeTokenRequest(String code) {
		String query = getCodeTokenQuery(code);
		return buildTokenRequest(query);
	}

	/**
	 * Trade refresh_token for access_token
	 * 
	 * So called Offline access
	 */
	protected HttlRequestBuilder<?> buildRefreshTokenRequest(String refresh_token) {
		String query = getRefreshTokenQuery(refresh_token);
		return buildTokenRequest(query);
	}

	/**
	 * Trade username & password for access_token
	 * 
	 * Usualy only for privileged applications
	 */
	protected HttlRequestBuilder<?> buildPasswordTokenRequest(String username, String password) {
		String query = getPasswordTokenQuery(username, password);
		return buildTokenRequest(query);
	}

	protected HttlRequestBuilder<?> buildTokenRequest(String query) {

		String path = config.getTokenUrl().getPath();
		String senderPath = sender.getConfig().getUrl().getPath();
		if (senderPath != null && path.startsWith(senderPath)) {
			path = path.substring(senderPath.length() + 1);
		}

		HttlRequestBuilder<?> builder;
		if (config.getTokenHttpMethod() == Method.POST) {
			builder = sender.POST(path).body(query, "application/x-www-form-urlencoded");
		} else {
			builder = sender.GET(path + "?" + query);
		}

		List<String[]> headers = config.getTokenHeaders();
		if (headers != null) {
			for (String[] header : headers) {
				builder.setHeader(header[0], header[1]);
			}
		}

		return builder;
	}

	/**
	 * Trade code for access_token
	 * 
	 * Standard OAuth for Web Server Applications
	 */
	protected String getCodeTokenQuery(String code) {

		StringBuilder sb = config.getTokenQueryBuilder();
		append(sb, "grant_type", "authorization_code");

		if (code == null || code.isEmpty()) {
			throw new IllegalStateException("code is required");
		} else {
			append(sb, "code", code);
		}

		return sb.toString();
	}

	/**
	 * Trade refresh_token for access_token
	 * 
	 * So called Offline access
	 */
	protected String getRefreshTokenQuery(String refresh_token) {
		StringBuilder sb = config.getTokenQueryBuilder();
		append(sb, "grant_type", "refresh_token");

		if (refresh_token == null || refresh_token.isEmpty()) {
			throw new IllegalStateException("refresh_token is required");
		} else {
			append(sb, "refresh_token", refresh_token);
		}

		return sb.toString();
	}

	/**
	 * Trade username & password for access_token
	 * 
	 * Usualy only for privileged applications
	 */
	protected String getPasswordTokenQuery(String username, String password) {
		StringBuilder sb = config.getTokenQueryBuilder();
		append(sb, "grant_type", "password");

		if (username == null || username.isEmpty()) {
			throw new IllegalStateException("username is required");
		} else {
			append(sb, "username", username);
		}

		if (password == null || password.isEmpty()) {
			throw new IllegalStateException("password is required");
		} else {
			append(sb, "password", password);
		}

		return sb.toString();
	}

	public static void append(StringBuilder sb, String name, String value) {
		if (value != null) {
			if (sb.length() != 0) {
				sb.append("&");
			}
			sb.append(name).append("=").append(HttlUtil.urlencode(value));
		}
	}

}
