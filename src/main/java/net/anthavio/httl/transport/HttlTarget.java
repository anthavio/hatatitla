package net.anthavio.httl.transport;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * 
 * @author mvanek
 *
 */
public interface HttlTarget {

	public URL getUrl();

	public URL[] getUrls();

	/**
	 * Callback from transport or higher layer...possibly mark down some hostname
	 */
	public abstract void failed(URL request);

	public static class SingleHttlTarget implements HttlTarget {

		private final URL url;

		public SingleHttlTarget(URL url) {
			this.url = url;
		}

		@Override
		public URL getUrl() {
			return url;
		}

		@Override
		public URL[] getUrls() {
			return new URL[] { url };
		}

		@Override
		public void failed(URL request) {
			//ignore
		}

	}

	public static abstract class HttlTargetBase implements HttlTarget {

		private URL[] targetUrls;

		public HttlTargetBase(URL url) {
			this.targetUrls = new URL[] { url };
		}

		public HttlTargetBase(String... hosts) {
			this.targetUrls = new URL[hosts.length];
			for (int i = 0; i < hosts.length; i++) {
				this.targetUrls[i] = parse(hosts[i]);
			}
		}

		/**
		 * For senders setup
		 */
		@Override
		public URL[] getUrls() {
			return targetUrls;
		}

		private static URL parse(String urlString) {
			try {
				if (!urlString.startsWith("http")) {
					urlString = "http://" + urlString;
				}
				return new URL(urlString);
			} catch (MalformedURLException mux) {
				throw new IllegalArgumentException("Unable to parse " + urlString, mux);
			}
		}

	}

}
