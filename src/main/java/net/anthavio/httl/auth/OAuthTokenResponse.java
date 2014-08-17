package net.anthavio.httl.auth;

/**
 * Quite standard OpenID token response from token endpoint
 * 
 * Usually JSON so keep it as simple Bean to be "jsonable" for Jackson or Gson 
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

	private String error;

	private String error_description;

	protected OAuthTokenResponse() {
		//marshalling
	}

	public OAuthTokenResponse(String access_token) {
		this.access_token = access_token;
	}

	public String getToken_type() {
		return token_type;
	}

	public void setToken_type(String token_type) {
		this.token_type = token_type;
	}

	public String getAccess_token() {
		return access_token;
	}

	public void setAccess_token(String access_token) {
		this.access_token = access_token;
	}

	public Integer getExpires_in() {
		return expires_in;
	}

	public void setExpires_in(Integer expires_in) {
		this.expires_in = expires_in;
	}

	public String getId_token() {
		return id_token;
	}

	public void setId_token(String id_token) {
		this.id_token = id_token;
	}

	public String getRefresh_token() {
		return refresh_token;
	}

	public void setRefresh_token(String refresh_token) {
		this.refresh_token = refresh_token;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	@Override
	public String toString() {
		if (error != null) {
			return "OAuthTokenResponse [error=" + error + ", error_description=" + error_description + "]";
		} else {
			return "OAuthTokenResponse [token_type=" + token_type + ", access_token=" + access_token + ", expires_in="
					+ expires_in + ", id_token=" + id_token + ", refresh_token=" + refresh_token + ", scope=" + scope + "]";
		}
	}

}
