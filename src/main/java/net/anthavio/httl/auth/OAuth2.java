package net.anthavio.httl.auth;

import net.anthavio.httl.HttlBuilderVisitor;
import net.anthavio.httl.HttlRequest.Method;
import net.anthavio.httl.HttlRequestBuilders.SenderRequestBuilder;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.util.HttpHeaderUtil;

/**
 * Google:
 * https://developers.google.com/accounts/docs/OAuth2Login
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

	public static OAuth2Builder Builder(HttlSender sender) {
		return new OAuth2Builder(sender);
	}

	public String getAuthorizationUrl(String scope, String state) {
		return config.getAuthUrl() + "?" + getAuthorizationQuery(scope, state);
	}

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

	/**
	 * Trade Code for Token
	 */
	public <T> T getAccessToken(String code, HttlBuilderVisitor visitor, HttlResponseExtractor<T> extractor) {
		SenderRequestBuilder<?> builder = buildCodeTokenRequest(code);
		visitor.visit(builder);
		return builder.extract(extractor).getBody();
	}

	/**
	 * Trade Code for Token
	 */
	public <T> T getAccessToken(String code, HttlBuilderVisitor visitor, Class<T> tokenClass) {
		SenderRequestBuilder<?> builder = buildCodeTokenRequest(code);
		visitor.visit(builder);
		return builder.extract(tokenClass).getBody();
	}

	/**
	 * Trade Code for Token
	 */
	public <T> T getAccessToken(String code, HttlResponseExtractor<T> extractor) {
		return buildCodeTokenRequest(code).extract(extractor).getBody();
	}

	/**
	 * Trade Code for Token
	 */
	public <T> T getAccessToken(String code, Class<T> tokenClass) {
		return buildCodeTokenRequest(code).extract(tokenClass).getBody();
	}

	/**
	 * Trade username & password for Token
	 */
	public <T> T getAccessToken(String username, String password, Class<T> tokenClass) {
		return buildPasswordTokenRequest(username, password).extract(tokenClass).getBody();
	}

	/**
	 * Trade Code for Token
	 */
	protected SenderRequestBuilder<?> buildCodeTokenRequest(String code) {
		String path = config.getTokenUrl().getPath();
		String senderPath = sender.getConfig().getUrl().getPath();
		if (senderPath != null && path.startsWith(senderPath)) {
			path = path.substring(senderPath.length() + 1);
		}

		String query = getCodeTokenQuery(code);
		if (config.getTokenHttpMethod() == Method.POST) {
			return sender.POST(path).body(query, "application/x-www-form-urlencoded");
		} else {
			return sender.GET(path + "?" + query);
		}
	}

	/**
	 * Trade Code for Token
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
	 * Trade username & password for Token
	 */
	protected SenderRequestBuilder<?> buildPasswordTokenRequest(String username, String password) {
		String path = config.getTokenUrl().getPath();
		String query = getPasswordTokenQuery(username, password);
		if (config.getTokenHttpMethod() == Method.POST) {
			return sender.POST(path).body(query, "application/x-www-form-urlencoded");
		} else {
			return sender.GET(path + "?" + query);
		}
	}

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

	public static void append(StringBuilder sb, String name, String value) {
		if (value != null) {
			if (sb.length() != 0) {
				sb.append("&");
			}
			sb.append(name).append("=").append(HttpHeaderUtil.urlencode(value));
		}
	}

}
