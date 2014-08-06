package net.anthavio.httl.auth;

/**
 * Quite standard OpenID token response from token endpoint
 * 
 * Usually JSON
 * 
 * @author martin.vanek
 *
 */
public class OAuthTokenResponse {

	private String token_type; //Bearer

	private String access_token;

	private Integer expires_in;

	private String id_token;

	private String refresh_token;

	private String scope;

}
