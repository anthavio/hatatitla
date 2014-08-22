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
		return config.getAuthUrl() + "?" + getAuthQuery(scope, state);
	}

	protected String getAuthQuery(String scope, String state) {
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

	public <T> T getAccessToken(String code, HttlBuilderVisitor visitor, HttlResponseExtractor<T> extractor) {
		SenderRequestBuilder<?> builder = buildTokenRequest(code);
		visitor.visit(builder);
		return builder.extract(extractor).getBody();
	}

	public <T> T getAccessToken(String code, HttlBuilderVisitor visitor, Class<T> tokenClass) {
		SenderRequestBuilder<?> builder = buildTokenRequest(code);
		visitor.visit(builder);
		return builder.extract(tokenClass).getBody();
	}

	public <T> T getAccessToken(String code, HttlResponseExtractor<T> extractor) {
		return buildTokenRequest(code).extract(extractor).getBody();
	}

	public <T> T getAccessToken(String code, Class<T> tokenClass) {
		return buildTokenRequest(code).extract(tokenClass).getBody();
	}

	protected SenderRequestBuilder<?> buildTokenRequest(String code) {
		String path = config.getTokenUrl().getPath();
		String query = getAccessTokenQuery(code);
		if (config.getTokenHttpMethod() == Method.POST) {
			return sender.POST(path).body(query, "application/x-www-form-urlencoded");
		} else {
			return sender.GET(path + "?" + query);
		}
	}

	protected String getAccessTokenQuery(String code) {

		StringBuilder sb = config.getTokenQueryBuilder();
		append(sb, "grant_type", "authorization_code");

		if (code == null || code.isEmpty()) {
			throw new IllegalStateException("code is required");
		} else {
			append(sb, "code", code);
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
