package net.anthavio.httl.rest;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 
 * @author martin.vanek
 *
 */
public class ArgConfig {

	/**
	 * 
	 * Argh! This is nice example where ploymorphism and generics are horrible syntax overkill
	 *
	 */
	public abstract static class ArgType<P, S> {

		public static final ArgType<String, Serializable> STRING = new ArgType<String, Serializable>(String.class,
				Serializable.class) {

			@Override
			public String convert(Serializable secondary) {
				return String.valueOf(secondary);
			}
		};

		public static final ArgType<Number, String> INTEGER = new ArgType<Number, String>(Number.class, String.class) {

			@Override
			public Integer convert(String secondary) {
				return Integer.parseInt(secondary);
			}
		};
		public static final ArgType<Number, String> LONG = new ArgType<Number, String>(Number.class, String.class) {

			@Override
			public Long convert(String secondary) {
				return Long.parseLong(secondary);
			}
		};

		public static final ArgType<Boolean, String> BOOLEAN = new ArgType<Boolean, String>(Boolean.class, String.class) {

			@Override
			public Boolean convert(String secondary) {
				return Boolean.valueOf(secondary);
			}
		};

		public static String DATE_FORMAT;

		public static final ArgType<Date, String> DATE = new ArgType<Date, String>(Date.class, String.class) {

			@Override
			public Date convert(String secondary) {
				if (DATE_FORMAT == null) {
					throw new IllegalStateException("DATE_FORMAT is not set"); //XXX this is quite stupid
				}
				try {
					return new SimpleDateFormat(DATE_FORMAT).parse(secondary);
				} catch (ParseException px) {
					throw new IllegalArgumentException("Parameter value " + secondary + " does not conform " + DATE_FORMAT
							+ " format");
				}
			}

		};

		public static final ArgType<String, String> EMAIL = new ArgType<String, String>(String.class, String.class) {

			@Override
			public String convert(String secondary) {
				return secondary;
			}
		};

		private final Class<P> primary;

		private final Class<S> alternate;

		private ArgType(Class<P> primary, Class<S> alternate) {
			this.primary = primary;
			this.alternate = alternate;
		}

		public Class<P> getPrimary() {
			return this.primary;
		}

		public Class<S> getAlternate() {
			return this.alternate;
		}

		protected void check(P primary) {
			//to be overiden
		}

		protected abstract P convert(S secondary);

		public P validate(Object value) {
			Class<? extends Object> vclass = value.getClass();
			if (this.primary.isAssignableFrom(vclass)) {
				P p = (P) value;
				check(p);
				return p;
			} else if (this.alternate.isAssignableFrom(vclass)) {
				P p = convert((S) value);
				check(p);
				return p;
			} else {
				throw new IllegalArgumentException("Parameter type is neither " + this.primary.getSimpleName() + " nor "
						+ this.alternate.getSimpleName() + " but " + vclass.getSimpleName());
			}
		}
	}

	/*
	public static enum Related {
		forum, thread, author, category;
	}
	
	public static final ArgType<Related, String> RELATED = new ArgType<Related, String>(Related.class, String.class) {

		@Override
		public Related convert(String secondary) {
			return Related.valueOf(secondary);
		}
	};
	*/

	/*
		public static final ArgumentConfig API_KEY = new ArgumentConfig("api_key", ArgType.STRING, false, false);
		public static final ArgumentConfig SECRET_KEY = new ArgumentConfig("secret_key", ArgType.STRING, false, false);
		public static final ArgumentConfig REMOTE_AUTH = new ArgumentConfig("remote_auth", ArgType.STRING, false, false);
		public static final ArgumentConfig ACCESS_TOKEN = new ArgumentConfig("access_token", ArgType.STRING, false, false);

		public static enum PostState {
			unapproved, approved, spam, deleted, flagged, highlighted;

			public static PostState[] ALL = new PostState[] { unapproved, approved, spam, deleted, flagged, highlighted };

			public static List<PostState> CALL = Arrays.asList(ALL);
		}

		public static enum ThreadState {
			open, closed, killed;

			public static ThreadState[] ALL = new ThreadState[] { open, closed, killed };

			public static List<ThreadState> CALL = Arrays.asList(ALL);
		}

		public static enum UserInclude {
			user, replies, following; //Users.listActivity
		}

		public static enum Related {
			forum, thread, author, category;
		}

		public static enum Order {
			asc, desc;
		}

		public static enum Vote {

			PLUS(1), ZERO(0), MINUS(-1);

			public final int value;

			private Vote(int value) {
				this.value = value;
			}

			public static Vote valueOf(int value) {
				if (value > 0) {
					return Vote.PLUS;
				} else if (value < 0) {
					return Vote.MINUS;
				} else {
					return Vote.ZERO;
				}
			}
		}

		public static enum FilterType {

			domain, word, ip, user, thread_slug, email;

			public static FilterType[] ALL = new FilterType[] { domain, word, ip, user, thread_slug, email };
		}
	*/
	private final String name;

	private final ArgType<?, ?> type;

	private final boolean isRequired;

	private final boolean isMultivalue;

	public ArgConfig(String name, ArgType<?, ?> type, boolean required, boolean multi) {
		this.name = name;
		this.type = type;
		this.isRequired = required;
		this.isMultivalue = multi;
	}

	public String getName() {
		return this.name;
	}

	public ArgType<?, ?> getType() {
		return this.type;
	}

	public boolean getIsRequired() {
		return this.isRequired;
	}

	public boolean getIsMultivalue() {
		return this.isMultivalue;
	}

}
