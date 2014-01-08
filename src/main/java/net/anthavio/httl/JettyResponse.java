package net.anthavio.httl;

import java.io.ByteArrayInputStream;

import net.anthavio.httl.HttpSender.Multival;
import net.anthavio.httl.JettySender.JettyContentExchange;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpFields.Field;

/**
 * 
 * @author martin.vanek
 *
 */
public class JettyResponse extends SenderResponse {

	private static final long serialVersionUID = 1L;

	public JettyResponse(JettyContentExchange exchange) {
		super(exchange.getStatus(), exchange.getMessage(), convert(exchange.getResponseHeaders()),
				new ByteArrayInputStream(exchange.getResponseBody()));
	}

	private static Multival convert(HttpFields fields) {
		Multival ret = new Multival();
		for (int i = 0; i < fields.size(); ++i) {
			Field field = fields.getField(i);
			ret.add(field.getName(), field.getValue());
		}
		return ret;
	}
}
