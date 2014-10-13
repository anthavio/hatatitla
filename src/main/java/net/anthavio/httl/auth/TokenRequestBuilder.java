package net.anthavio.httl.auth;

import net.anthavio.httl.HttlBuilderVisitor;
import net.anthavio.httl.HttlRequestBuilders.SenderRequestBuilder;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.auth.OAuth2.FinalBuildStep;
import net.anthavio.httl.auth.OAuth2.SelectTypeBuildStep;

/**
 * 
 * @author martin.vanek
 *
 */
public class TokenRequestBuilder implements SelectTypeBuildStep, FinalBuildStep {

	private final OAuth2 oauth;

	private HttlBuilderVisitor visitor;

	private String code;

	private String refresh_token;

	private String username;

	private String password;

	public TokenRequestBuilder(OAuth2 oauth) {
		this.oauth = oauth;
	}

	public FinalBuildStep access(String code) {
		if (code == null || code.isEmpty()) {
			throw new IllegalArgumentException("Empty code");
		}
		this.code = code;

		return this;
	}

	public FinalBuildStep refresh(String refresh_token) {
		if (refresh_token == null || refresh_token.isEmpty()) {
			throw new IllegalArgumentException("Empty refresh_token");
		}
		this.refresh_token = refresh_token;

		return this;
	}

	public FinalBuildStep password(String username, String password) {
		if (username == null || username.isEmpty()) {
			throw new IllegalArgumentException("Empty username");
		}
		this.username = username;

		if (password == null || password.isEmpty()) {
			throw new IllegalArgumentException("Empty password");
		}
		this.password = password;

		return this;
	}

	@Override
	public FinalBuildStep visitor(HttlBuilderVisitor visitor) {

		if (visitor == null) {
			throw new IllegalArgumentException("Null visitor");
		}
		this.visitor = visitor;
		return this;
	}

	@Override
	public OAuthTokenResponse get() {
		return getRequestBuilder().extract(OAuthTokenResponse.class).getBody();
	}

	@Override
	public <T> T get(Class<T> tokenClass) {
		return getRequestBuilder().extract(tokenClass).getBody();
	}

	@Override
	public <T> T get(HttlResponseExtractor<T> extractor) {
		return getRequestBuilder().extract(extractor).getBody();
	}

	private SenderRequestBuilder<?> getRequestBuilder() {
		SenderRequestBuilder<?> builder;
		if (code != null) {
			builder = oauth.buildCodeTokenRequest(code);
		} else if (refresh_token != null) {
			builder = oauth.buildRefreshTokenRequest(refresh_token);
		} else if (username != null) {
			builder = oauth.buildPasswordTokenRequest(username, password);
		} else {
			throw new IllegalStateException("Impossible happend!");
		}
		if (visitor != null) {
			visitor.visit(builder);
		}
		return builder;
	}
}
