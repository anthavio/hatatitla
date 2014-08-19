package net.anthavio.httl;

import java.io.IOException;
import java.util.Date;

import net.anthavio.httl.HttlParameterSetter.ConfigurableParamSetter;
import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;

public class DeleteMe {

	public static void main(String[] args) throws IOException {
		//HttpSender sender = new HttpURLSender("http://httpbin.org");
		HttlSender sender = HttlSender.with("http://httpbin.org").httpClient4().sender().build();

		ConfigurableParamSetter handler = new ConfigurableParamSetter(false, true, true, false,
				ConfigurableParamSetter.DEFAULT_DATE_PATTERN);
		sender.getConfig().setParamSetter(handler);
		ExtractedResponse<String> extract = sender.GET("/get").param("date param", new Date()).extract(String.class);

		System.out.println(extract.getBody());
		sender.close();

		GetMethod get = new GetMethod("http://httpbin.org/get");
		get.setQueryString(new NameValuePair[] { new NameValuePair("date", new Date().toString()) });
		HttpClient client = new HttpClient();
		client.executeMethod(get);
		String xxx = get.getResponseBodyAsString();
		System.out.println(xxx);
	}
}
