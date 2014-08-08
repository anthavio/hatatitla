package net.anthavio.httl.api;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.Charset;

import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlRequest;

/**
 * 
 * @author martin.vanek
 *
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RestBody {

	/**
	 * @return 'Content-Type' http request header value
	 */
	String value() default "";

	/**
	 * @return Optional RequestMarshaller to serialize request body
	 */
	Class<? extends HttlBodyMarshaller> marshaller() default NoopRequestMarshaller.class;

	static class NoopRequestMarshaller<T> implements HttlBodyMarshaller {

		@Override
		public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException {

		}

		@Override
		public HttlBodyMarshaller supports(HttlRequest request) {
			return null;
		}

	}
}
