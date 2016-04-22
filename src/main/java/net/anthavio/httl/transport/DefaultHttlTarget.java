package net.anthavio.httl.transport;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.anthavio.httl.transport.HttlTarget.HttlTargetBase;

/**
 * 
 * @author mvanek
 *
 */
public class DefaultHttlTarget extends HttlTargetBase {

	private final Random random;

	private final URL[] urls;

	private final Map<URL, boolean[]> downMap = new HashMap<URL, boolean[]>();

	public DefaultHttlTarget(int timeout, String... hosts) {
		super(hosts);
		this.random = new Random();
		this.urls = getUrls();
		for (URL url : urls) {
			downMap.put(url, new boolean[] { false });
		}
	}

	public DefaultHttlTarget(int markDownTimeout, URL url) {
		super(url);
		this.random = new Random();
		this.urls = getUrls();
		downMap.put(url, new boolean[] { false });
	}

	@Override
	public URL getUrl() {
		return getUrls()[random.nextInt(urls.length)];
	}

	@Override
	public void failed(URL url) {
		boolean[] flag = downMap.get(url);
		if (flag == null) {
			throw new IllegalArgumentException("URL not found: " + url);
		} else {
			flag[0] = true;
		}
	}

}
