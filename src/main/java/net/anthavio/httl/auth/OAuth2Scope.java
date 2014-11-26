package net.anthavio.httl.auth;

/**
 * 
 * @author martin.vanek
 *
 */
public interface OAuth2Scope {

	public String getCode();

	public static class SimpleScope implements OAuth2Scope {

		private final String code;

		public SimpleScope(String code) {
			if (code == null || code.length() == 0) {
				throw new IllegalArgumentException("Invalid code: " + code);
			}
			this.code = code;
		}

		@Override
		public String getCode() {
			return code;
		}

	}
}
