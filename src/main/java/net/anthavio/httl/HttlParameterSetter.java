package net.anthavio.httl;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import net.anthavio.httl.HttlSender.Parameters;

/**
 * 
 * Handle/convert request parameters when building HttlRequest
 * 
 * @author martin.vanek
 *
 */
public interface HttlParameterSetter {

	/**
	 * 
	 */
	public void handle(Parameters parameters, boolean reset, String paramName, Object paramValue);

	/**
	 * Default RequestParamSetter implementation
	 * 
	 * TODO introduce builder
	 */
	public static class ConfigurableParamSetter implements HttlParameterSetter {

		public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ssZ";

		private final boolean keepNull;

		private final boolean keepEmpty;

		private final boolean urlEncodeValues;

		private final boolean urlEncodeNames;

		private final String datePattern;

		private final SimpleDateFormat dateFormat;

		public ConfigurableParamSetter() {
			this(false, true, false, false, DEFAULT_DATE_PATTERN);
		}

		public ConfigurableParamSetter(String dateParamPattern) {
			this(false, true, false, false, dateParamPattern);
		}

		public ConfigurableParamSetter(boolean keepNullParams, boolean keepEmptyParams, boolean urlEncodeNames,
				boolean urlEncodeValues, String dateParamPattern) {
			this.keepNull = keepNullParams;
			this.keepEmpty = keepEmptyParams;
			this.urlEncodeNames = urlEncodeNames;
			this.urlEncodeValues = urlEncodeValues;
			this.datePattern = dateParamPattern;
			this.dateFormat = new SimpleDateFormat(dateParamPattern);
		}

		public boolean isKeepNull() {
			return keepNull;
		}

		public boolean isKeepEmpty() {
			return keepEmpty;
		}

		public boolean isUrlEncodeValues() {
			return urlEncodeValues;
		}

		public String getDatePattern() {
			return datePattern;
		}

		public SimpleDateFormat getDateFormat() {
			return dateFormat;
		}

		@Override
		public void handle(Parameters parameters, boolean reset, String paramName, Object paramValue) {
			if (urlEncodeNames) {
				try {
					if (paramName.charAt(0) != ';') {
						paramName = URLEncoder.encode(paramName, "UTF-8");
					} else {
						paramName = ';' + URLEncoder.encode(paramName.substring(1), "UTF-8");
					}

				} catch (UnsupportedEncodingException uex) {
					throw new IllegalStateException("Encoding UTF-8 is unsupported");
				}
			}
			if (paramValue == null) {
				if (keepNull) {
					parameters.put(paramName, null, reset);
				}
			} else {
				if (paramValue instanceof Collection) {
					collection(parameters, reset, paramName, (Collection<?>) paramValue);

				} else if (paramValue instanceof Iterator) {
					iterator(parameters, reset, paramName, (Iterator<?>) paramValue);

				} else if (paramValue.getClass().isArray()) {
					array(parameters, reset, paramName, paramValue);

				} else {
					String string = convert(paramName, paramValue);
					if (string != null) {
						parameters.put(paramName, string, reset);
					}
				}
			}
		}

		protected void collection(Parameters parameters, boolean reset, String paramName, Collection<?> paramValue) {
			List<String> list = parameters.get(paramName, reset);
			Collection<?> collection = (Collection<?>) paramValue;
			for (Object element : collection) {
				element(list, paramName, element);
			}
		}

		protected void iterator(Parameters parameters, boolean reset, String paramName, Iterator<?> paramValue) {
			List<String> list = parameters.get(paramName, reset);
			Iterator<?> iterator = (Iterator<?>) paramValue;
			while (iterator.hasNext()) {
				Object element = iterator.next();
				element(list, paramName, element);
			}
		}

		protected void array(Parameters parameters, boolean reset, String paramName, Object paramValue) {
			List<String> list = parameters.get(paramName, reset);
			int length = Array.getLength(paramValue);
			for (int i = 0; i < length; i++) {
				Object element = Array.get(paramValue, i);
				element(list, paramName, element);
			}
		}

		/**
		 * Handle single element of array/collection/iterator
		 */
		protected void element(List<String> list, String paramName, Object element) {
			if (element == null) {
				if (keepNull) {
					list.add(null);
				}
			} else {
				String string = convert(paramName, element);
				if (string == null) {
					if (keepNull) {
						list.add(null);
					}
				} else if (string.length() == 0) {
					if (keepEmpty) {
						list.add("");
					}
				} else {
					list.add(string);
				}
			}
		}

		/**
		 * Convert paramter value to String
		 */
		protected String convert(String paramName, Object paramValue) {
			String string;
			if (paramValue instanceof String) {
				string = (String) paramValue;
			} else if (paramValue instanceof Date) {
				string = dateFormat.format((Date) paramValue);
			} else if (paramValue instanceof Collection) {
				throw new IllegalArgumentException("Collection passed as parameter " + paramName + " value");
				//return String.valueOf(paramValue); // XXX Some special treatment ?
			} else if (paramValue.getClass().isArray()) {
				throw new IllegalArgumentException("Array passed as parameter " + paramName + " value");
				//return String.valueOf(paramValue); // XXX Some special treatment ?
			} else {
				string = String.valueOf(paramValue);
			}
			if (urlEncodeValues) {
				try {
					return URLEncoder.encode(string, "UTF-8");
				} catch (UnsupportedEncodingException uex) {
					throw new IllegalStateException("Encoding UTF-8 is unsupported");
				}
			} else {
				return string;
			}
		}
	}
}
