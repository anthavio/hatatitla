package net.anthavio.httl.auth;

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

	public <T> T getAccessToken(String code, Class<T> tokenClass) {
		String path = config.getTokenUrl().getPath();
		String body = getAccessTokenQuery(code);
		return sender.POST(path).body(body, "application/x-www-form-urlencoded").extract(tokenClass).getBody();
	}

	public String getAuthUrl(String state, String scope) {
		return config.getAuthUrl() + "?" + getAuthQuery(scope, state);
	}

	public String getAuthQuery(String scope, String state) {
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

	public String getAccessTokenQuery(String code) {

		StringBuilder sb = config.getTokenQueryBuilder();
		append(sb, "grant_type", "authorization_code");

		if (code == null || code.isEmpty()) {
			throw new IllegalStateException("code is required");
		} else {
			append(sb, "code", code);
		}

		return sb.toString();
	}

	public String getRefreshTokenQuery(String refresh_token) {
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
