package net.anthavio.httl;

import net.anthavio.httl.util.Cutils;

/**
 * 
 * @author martin.vanek
 *
 */
public class Authentication {

	public static enum Scheme {
		BASIC, DIGEST;
	}

	private Scheme scheme;

	private String username;

	private String password;

	private boolean preemptive;

	private String realm;

	private String nonce;

	protected Authentication() {

	}

	public static Authentication BASIC(String username, String password) {
		return new Authentication(Scheme.BASIC, username, password);
	}

	public static Authentication DIGEST(String username, String password) {
		return new Authentication(Scheme.DIGEST, username, password);
	}

	public Authentication(Scheme scheme, String username, String password) {
		this(scheme, username, password, null, scheme == Scheme.BASIC); //only BASIC can be preemptive
	}

	public Authentication(Scheme scheme, String username, String password, String realm) {
		this(scheme, username, password, realm, scheme == Scheme.BASIC); //only BASIC can be preemptive
	}

	public Authentication(Scheme scheme, String username, String password, boolean preemptive) {
		this(scheme, username, password, null, preemptive, null);
	}

	public Authentication(Scheme scheme, String username, String password, String realm, boolean preemptive) {
		this(scheme, username, password, realm, preemptive, null);
	}

	public Authentication(Scheme scheme, String username, String password, String realm, boolean preemptive, String nonce) {
		if (scheme == null) {
			throw new IllegalArgumentException("scheme is null");
		}
		this.scheme = scheme;

		if (Cutils.isBlank(username)) {
			throw new IllegalArgumentException("username is blank");
		}
		this.username = username;

		if (password == null) {
			throw new IllegalArgumentException("password is blank");//leave option for blank password
		}
		this.password = password;

		this.preemptive = preemptive;

		this.realm = realm;

		if (preemptive && scheme == Scheme.DIGEST) {
			if (Cutils.isBlank(realm)) {
				throw new IllegalArgumentException("Parameter realm must be known when using preemptive DIGEST");
			}
			if (Cutils.isBlank(nonce)) {
				throw new IllegalArgumentException("Parameter nonce must be known when using preemptive DIGEST");
			}
			this.nonce = nonce;
		}
	}

	public Scheme getScheme() {
		return this.scheme;
	}

	public Authentication setScheme(Scheme scheme) {
		this.scheme = scheme;
		return this;
	}

	public String getUsername() {
		return this.username;
	}

	public Authentication setUsername(String username) {
		this.username = username;
		return this;
	}

	public String getPassword() {
		return this.password;
	}

	public Authentication setPassword(String password) {
		this.password = password;
		return this;
	}

	public boolean getPreemptive() {
		return this.preemptive;
	}

	public Authentication setPreemptive(boolean preemptive) {
		this.preemptive = preemptive;
		return this;
	}

	public String getRealm() {
		return this.realm;
	}

	public Authentication setRealm(String realm) {
		this.realm = realm;
		return this;
	}

	public String getNonce() {
		return this.nonce;
	}

	public Authentication setNonce(String nonce) {
		this.nonce = nonce;
		return this;
	}

}
