package net.anthavio.httl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import net.anthavio.httl.GetRequest;
import net.anthavio.httl.HttpClient3Response;
import net.anthavio.httl.HttpClient3Sender;
import net.anthavio.httl.HttpClient4Response;
import net.anthavio.httl.HttpClient4Sender;
import net.anthavio.httl.HttpURLSender;
import net.anthavio.httl.PostRequest;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderResponse;

import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.http.conn.BasicManagedEntity;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicHttpResponse;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


/**
 * 
 * @author martin.vanek
 *
 */
public class ConnectionPoolingTest {

	private JokerServer server = new JokerServer();

	@BeforeClass
	public void setup() throws Exception {
		this.server.start();
	}

	@AfterClass
	public void destroy() throws Exception {
		this.server.stop();
	}

	@Test
	public void simple() throws IOException, Exception {
		String url = "http://localhost:" + this.server.getHttpPort();
		HttpURLSender sender = new HttpURLSender(url);
		SenderRequest request = new GetRequest("/");
		//connection persistence is controlled with system properties
		System.setProperty("http.keepAlive", "true");
		System.setProperty("http.maxConnections", "5");

		SenderResponse response1 = sender.execute(request);
		assertThat(response1.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		response1.close();

		SenderResponse response2 = sender.execute(request);
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
	public void http3() throws IOException, Exception {
		String url = "http://localhost:" + this.server.getHttpPort();
		HttpClient3Sender sender = new HttpClient3Sender(url);
		SenderRequest request = new PostRequest("/");

		//XXX this does not work in 3.1 - it manages Connection headers by itself
		//request.setHeader("Connection", "close");

		//prepare reflection objects
		Field fResponsec = HttpMethodBase.class.getDeclaredField("responseConnection");
		fResponsec.setAccessible(true);

		Class<?> classx = Class
				.forName("org.apache.commons.httpclient.MultiThreadedHttpConnectionManager$HttpConnectionAdapter");
		Field fWrappedc = classx.getDeclaredField("wrappedConnection");
		fWrappedc.setAccessible(true);

		MultiThreadedHttpConnectionManager connectionManager = (MultiThreadedHttpConnectionManager) sender.getHttpClient()
				.getHttpConnectionManager();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(0);

		HttpClient3Response response1 = (HttpClient3Response) sender.execute(request);
		assertThat(response1.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		Object responsec1 = fResponsec.get(response1.getHttpMethod());
		Object wrappedc1 = fWrappedc.get(responsec1);
		response1.close();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(1);

		HttpClient3Response response2 = (HttpClient3Response) sender.execute(request);
		assertThat(response2.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		Object responsec2 = fResponsec.get(response2.getHttpMethod());
		Object wrappedc2 = fWrappedc.get(responsec2);
		response2.close();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(1);

		//System.out.println(wrappedc1);
		//System.out.println(wrappedc2);
		assertThat(wrappedc1).isEqualTo(wrappedc2);

		connectionManager.closeIdleConnections(0); //empty pool
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(0);

		//now test out of order opening/closing
		SenderResponse responsea = sender.execute(request);
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(1);

		SenderResponse responseb = sender.execute(request);
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(2);

		SenderResponse responsec = sender.execute(request);
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(3);

		responsea.close();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(3);

		responseb.close();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(3);

		responsec.close();
		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(3);

		sender.execute(request); //open before client closing...
		sender.close();

		assertThat(connectionManager.getConnectionsInPool()).isEqualTo(1); //leaking connection is still there!
	}

	@Test
	public void http4() throws IOException, Exception {
		String url = "http://localhost:" + this.server.getHttpPort();
		HttpClient4Sender sender = new HttpClient4Sender(url);
		PoolingClientConnectionManager connectionManager = (PoolingClientConnectionManager) sender.getHttpClient()
				.getConnectionManager();
		//all is empty at the beggining
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		SenderRequest request = new PostRequest("/");
		//http 1.1 does keep-alive by default
		Object[] entriesDefault = http4(sender, request);
		assertThat(entriesDefault[0]).isEqualTo(entriesDefault[1]);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(1);

		//explicit connection close header will disable connection persistence
		request.setHeader("Connection", "close");
		Object[] entriesClose = http4(sender, request);
		assertThat(entriesClose[0]).isNotEqualTo(entriesClose[1]); //isNotEqualTo 
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0); //closed => not in pool

		//explicit keep-alive
		request.setHeader("Connection", "keep-alive");
		Object[] entriesKeepAlive = http4(sender, request);
		assertThat(entriesKeepAlive[0]).isEqualTo(entriesKeepAlive[1]); //must be the same connection
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(1);

		connectionManager.closeIdleConnections(0, TimeUnit.MILLISECONDS); //empty pool
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);

		//now test out of order opening/closing
		SenderResponse response1 = sender.execute(request);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(1);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		SenderResponse response2 = sender.execute(request);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(2);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		SenderResponse response3 = sender.execute(request);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(3);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		response1.close();
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(1);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(2);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		response2.close();
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(2);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(1);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		response3.close();
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(3);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		sender.execute(request); //open before client closing...
		sender.close();
		//all is empty after closing
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);
	}

	private Object[] http4(HttpClient4Sender sender, SenderRequest request) throws Exception {
		//we can only check connection reusing via reflection
		Field fEntity = BasicHttpResponse.class.getDeclaredField("entity");
		fEntity.setAccessible(true);

		Field fMc = BasicManagedEntity.class.getDeclaredField("managedConn");
		fMc.setAccessible(true);

		Field fEntry = Class.forName("org.apache.http.impl.conn.ManagedClientConnectionImpl").getDeclaredField("poolEntry");
		fEntry.setAccessible(true);

		PoolingClientConnectionManager connectionManager = (PoolingClientConnectionManager) sender.getHttpClient()
				.getConnectionManager();

		HttpClient4Response response1 = (HttpClient4Response) sender.execute(request);
		assertThat(response1.getHttpStatusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
		assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(1);// only one
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		Object entity1 = fEntity.get(response1.getHttpResponse());
		Object managedc1 = fMc.get(entity1);
		Object entry1 = fEntry.get(managedc1);
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
		Object entry2 = fEntry.get(managedc2);
		//System.out.println(entry2);
		response2.close();

		//avaliable count dependes on Connection header
		assertThat(connectionManager.getTotalStats().getLeased()).isEqualTo(0);
		assertThat(connectionManager.getTotalStats().getPending()).isEqualTo(0);

		return new Object[] { entry1, entry2 };
	}

}
