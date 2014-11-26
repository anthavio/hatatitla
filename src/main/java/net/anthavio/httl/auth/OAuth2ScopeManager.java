package net.anthavio.httl.auth;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author martin.vanek
 *
 */
public interface OAuth2ScopeManager {

	public char getSeparator(); // ' ' or ,

	public OAuth2Scope[] list();

	public OAuth2Scope get(String code);

	public static class EnumBasedScopeManager<E extends Enum<E>> {

		private final OAuth2Scope[] values;

		public EnumBasedScopeManager(Class<Enum<E>> clazz) {
			Enum<E>[] constants = clazz.getEnumConstants();
			List<OAuth2Scope> list = new ArrayList<OAuth2Scope>();
			for (Enum<E> enumv : constants) {
				list.add(new EnumOAuth2Scope<Enum<E>>(enumv));
			}
			values = list.toArray(new OAuth2Scope[constants.length]);
		}

		public OAuth2Scope[] values() {
			return values;
		}
	}

	public static class EnumOAuth2Scope<E extends Enum<?>> implements OAuth2Scope {

		private final E value;

		public EnumOAuth2Scope(E value) {
			this.value = value;
		}

		@Override
		public String getCode() {
			return value.name();
		}

	}
}
