package net.anthavio.httl.transport;

import java.io.ByteArrayInputStream;

import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlSender.Multival;
import net.anthavio.httl.transport.JettyTransport.JettyContentExchange;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpFields.Field;

/**
 * 
 * @author martin.vanek
 *
 */
public class JettyResponse extends HttlResponse {

	private static final long serialVersionUID = 1L;

	public JettyResponse(HttlRequest request, JettyContentExchange exchange) {
		super(request, exchange.getHttpStatus(), exchange.getMessage(), convert(exchange.getResponseHeaders()),
				new ByteArrayInputStream(exchange.getResponseBody()));
	}

	private static Multival<String> convert(HttpFields fields) {
		Multival<String> ret = new Multival<String>();
		for (int i = 0; i < fields.size(); ++i) {
			Field field = fields.getField(i);
			ret.add(field.getName(), field.getValue());
		}
		return ret;
	}
}
