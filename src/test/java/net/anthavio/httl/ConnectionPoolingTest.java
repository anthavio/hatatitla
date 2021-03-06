package net.anthavio.httl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import net.anthavio.httl.transport.HttpClient3Response;
import net.anthavio.httl.transport.HttpClient3Transport;
import net.anthavio.httl.transport.HttpClient4Response;
import net.anthavio.httl.transport.HttpClient4Transport;

import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.http.conn.BasicManagedEntity;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.pool.PoolEntry;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class ConnectionPoolingTest {

	private static JokerServer server = new JokerServer();

	@BeforeClass
	public static void setup() throws Exception {
		server.start();
	}

	@AfterClass
	public static void destroy() throws Exception {
		server.stop();
	}

	/**
	 * It is impossible to test and verify connection pooling properly
	 * 
	 * http://docs.oracle.com/javase/6/docs/technotes/guides/net/http-keepalive.html
	 */
	@Test
	public void httpUrlConnectionPooling() throws IOException, Exception {
		//Given 
		String url = "http://localhost:" + server.getPortHttp();
		HttlSender sender = HttlBuilder.sender(url).build();
		HttlRequest request = sender.GET("/").build();
		//connection persistence is controlled with system properties
		System.setProperty("http.keepAlive", "true");
		System.setProperty("http.maxConnections", "5");

		HttlResponse response1 = sender.execute(request);
		assertThat(response1.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response1.close();

		HttlResponse response2 = sender.execute(request);
		assertThat(response2.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response2.close();

		sender.close();
		/*
		Class<? extends HttpURLConnection> class1 = response1.getConnection().getClass();
		Field[] fields = class1.getDeclaredFields();
		for (Field field : fields) {
			field.setAccessible(true);
			System.out.println(field.getName() + " " + field.get(response1.getConnection()));
		}
		*/
	}

	@Test
	public void httpClient3pooling() throws IOException, Exception {
		String url = "http://localhost:" + server.getPortHttp();
		HttlSender sender = HttlBuilder.transport(url).httpClient3().sender().build();
		HttlRequest request = sender.POST("/").build();

		//XXX this does not work in 3.1 - it manages Connection headers by itself
		//request.setHeader("Connection", "close");

		//prepare reflection objects
		Field fResponsec = HttpMethodBase.class.getDeclaredField("responseConnection");
		fResponsec.setAccessible(true);

		Class<?> classx = Class
				.forName("org.apache.commons.httpclient.MultiThreadedHttpConnectionManager$HttpConnectionAdapter");
		Field fWrappedc = classx.getDeclaredField("wrappedConnection");
		fWrappedc.setAccessible(true);

		//Given - MultiThreadedHttpConnectionManager

		HttpClient3Transport transport = (HttpClient3Transport) sender.getTransport();
		MultiThreadedHttpConnectionManager connectionManager = (MultiThreadedHttpConnectionManager) transport
				.getHttpClient().getHttpConnectionManager();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(0);

		//When - first request
		HttpClient3Response response1 = (HttpClient3Response) sender.execute(request);
		assertThat(response1.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		Object responsec1 = fResponsec.get(response1.getHttpMethod());
		Object wrappedc1 = fWrappedc.get(responsec1);
		response1.close();
		//Then 
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(1); //+1 kept alive

		//When - second request
		HttpClient3Response response2 = (HttpClient3Response) sender.execute(request);
		assertThat(response2.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		Object responsec2 = fResponsec.get(response2.getHttpMethod());
		Object wrappedc2 = fWrappedc.get(responsec2);
		response2.close();
		//Then
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(1); //1 still same
		//And !!!! 
		assertThat(wrappedc1).isEqualTo(wrappedc2);

		//When - pool cleanup
		connectionManager.closeIdleConnections(0);
		//Then - empty
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(0);

		//now test out of order opening/closing

		HttlResponse responsea = sender.execute(request);
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(1);//+1

		HttlResponse responseb = sender.execute(request);
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(2);//+1

		HttlResponse responsec = sender.execute(request);
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(3);//+1

		//cannot distinguish between borrowed and available connections in pool

		responsea.close();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(3);
		responsea = sender.execute(request);
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(3);
		responsea.close();

		responseb.close();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(3);

		responsec.close();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(3);

		HttlResponse leak = sender.execute(request); //open connection before client closing...
		sender.close();

		// Seems that nothing can be done about this leek

		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(1); //leaking connection!

		leak.close();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(1); //leaking connection is still there!
	}

	/**
	 * http://hc.apache.org/httpcomponents-client-4.3.x/tutorial/html/advanced.html#stateful_conn
	 */
	@Test
	public void httpClient4pooling() throws IOException, Exception {
		String url = "http://localhost:" + server.getPortHttp();
		HttlSender sender = HttlBuilder.transport(url).httpClient4().sender().build();

		HttpClient4Transport transport = (HttpClient4Transport) sender.getTransport();
		PoolingClientConnectionManager connectionManager = (PoolingClientConnectionManager) transport.getHttpClient()
				.getConnectionManager();
		//all is empty at the beggining
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		//When - default behaviour
		HttlRequest request = sender.POST("/").build();
		//Then - http 1.1 does keep-alive by default
		Object[] entriesDefault = http4(sender, request);
		assertThat(entriesDefault[0]).isEqualTo(entriesDefault[1]);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(1); //kept alive

		//When - explicit connection close header will disable connection persistence
		request = sender.POST("/").setHeader("Connection", "Close").build();
		//Then - will be closed
		Object[] entriesClose = http4(sender, request);
		assertThat(entriesClose[0]).isNotEqualTo(entriesClose[1]); //isNotEqualTo 
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0); //closed => not pooled

		//When - explicit keep-alive
		request = sender.POST("/").setHeader("Connection", "Keep-Alive").build();
		//Then - will remain open
		Object[] entriesKeepAlive = http4(sender, request);
		assertThat(entriesKeepAlive[0]).isEqualTo(entriesKeepAlive[1]); //must be the same connection
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(1); //kept alive

		connectionManager.closeIdleConnections(0, TimeUnit.MILLISECONDS); //empty pool
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);

		//now test out of order opening/closing

		HttlResponse response1 = sender.execute(request);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(1); //+1
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		HttlResponse response2 = sender.execute(request);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(2); //+1
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		HttlResponse response3 = sender.execute(request);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(3); //+1
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		response1.close();
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(1);//+1
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(2);//-1
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		response2.close();
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(2);//+1
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(1); //-1
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		response3.close();
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(3);//+1
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(0);//-1
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		//When - Create leaking response
		sender.execute(request);
		//And - Close whole HttlSender
		sender.close();

		//Then - no leaks - all is empty after closing
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);
	}

	/**
	 * Helper for accessing http client 4 internal connection pools
	 */
	private PoolEntry<HttpRoute, OperatedClientConnection>[] http4(HttlSender sender, HttlRequest request)
			throws Exception {
		//we can only check connection reusing via reflection
		Field fEntity = BasicHttpResponse.class.getDeclaredField("entity");
		fEntity.setAccessible(true);

		Field fMc = BasicManagedEntity.class.getDeclaredField("managedConn");
		fMc.setAccessible(true);

		Field fEntry = Class.forName("org.apache.http.impl.conn.ManagedClientConnectionImpl").getDeclaredField("poolEntry");
		fEntry.setAccessible(true);

		HttpClient4Transport transport = (HttpClient4Transport) sender.getTransport();
		PoolingClientConnectionManager connectionManager = (PoolingClientConnectionManager) transport.getHttpClient()
				.getConnectionManager();

		HttpClient4Response response1 = (HttpClient4Response) sender.execute(request);
		assertThat(response1.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(1);// only one
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		Object entity1 = fEntity.get(response1.getHttpResponse());
		Object managedc1 = fMc.get(entity1);
		PoolEntry<HttpRoute, OperatedClientConnection> entry1 = (PoolEntry<HttpRoute, OperatedClientConnection>) fEntry
				.get(managedc1);
		//System.out.println(entry1);
		response1.close();

		//avaliable count dependes on Connection header
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		HttpClient4Response response2 = (HttpClient4Response) sender.execute(request);
		assertThat(response2.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(1); // only one
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		Object entity2 = fEntity.get(response2.getHttpResponse());
		Object managedc2 = fMc.get(entity2);
		PoolEntry<HttpRoute, OperatedClientConnection> entry2 = (PoolEntry<HttpRoute, OperatedClientConnection>) fEntry
				.get(managedc2);
		//System.out.println(entry2 + " " + entry2.getClass());
		response2.close();

		//avaliable count dependes on Connection header
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		return new PoolEntry[] { entry1, entry2 };
	}

}
