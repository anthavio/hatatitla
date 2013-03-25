package com.anthavio.client.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.CharsetUtil;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

/**
 * I'm leaving this madness for long winter evenings...
 * 
 * @author martin.vanek
 *
 */
public class NettySender extends HttpSender {

	private ClientBootstrap client = null;

	private ChannelGroup channels = null;

	public NettySender(HttpSenderConfig config) {
		this(config, null);
	}

	public NettySender(HttpSenderConfig config, ExecutorService executor) {
		super(config, executor);
		client = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
				Executors.newCachedThreadPool()));
		client.setOption("connectTimeoutMillis", 10000);
		client.setOption("keepAlive", true);
		client.setPipelineFactory(new HttpClientPipelineFactory(true));
		channels = new DefaultChannelGroup();
	}

	@Override
	public void close() throws IOException {
		channels.close().awaitUninterruptibly();
		client.releaseExternalResources();
	}

	@Override
	protected SenderResponse doExecute(SenderRequest request, String path, String query) throws IOException {
		/*
		HttpRequest hrequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(request.getMethod().name()),
				uri.toASCIIString());

		request.setHeader(HttpHeaders.Names.HOST, host);
		request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
		
		if (cookie != null) {
			CookieEncoder httpCookieEncoder = new CookieEncoder(false);
			for (Map.Entry<String, String> m : cookie.entrySet()) {
				httpCookieEncoder.addCookie(m.getKey(), m.getValue());
				request.setHeader(HttpHeaders.Names.COOKIE, httpCookieEncoder.encode());
			}
		}
		
		ChannelPipeline pipeline = retrieve(request);
		*/
		return null;
	}

	public ChannelPipeline retrieve(HttpRequest request) throws Exception {
		URI uri = new URI(request.getUri());
		int port = uri.getPort() == -1 ? 80 : uri.getPort();
		ChannelFuture connectFuture = client
				.connect(new InetSocketAddress(request.getHeader(HttpHeaders.Names.HOST), port));
		//connectFuture.addListener(new ConnectOk(request));
		connectFuture.awaitUninterruptibly();
		if (!connectFuture.isSuccess()) {
			connectFuture.getCause().printStackTrace();
			client.releaseExternalResources();
			throw new RuntimeException(connectFuture.getCause());
		}
		channels.add(connectFuture.getChannel());
		return connectFuture.getChannel().getPipeline();
	}

	private static class HttpClientPipelineFactory implements ChannelPipelineFactory {

		private final boolean ssl;

		public HttpClientPipelineFactory(boolean ssl) {
			this.ssl = ssl;
		}

		@Override
		public ChannelPipeline getPipeline() throws Exception {
			ChannelPipeline pipeline = Channels.pipeline();
			Timer timer = new HashedWheelTimer();
			pipeline.addLast("timeout", new ReadTimeoutHandler(timer, 30));
			pipeline.addLast("codec", new HttpClientCodec());

			if (ssl) {
				SSLContext sslContext = SSLContext.getInstance("TLS");
				//sslContext.init(keyManagers, trustManagers, null);
				SSLEngine engine = sslContext.createSSLEngine();

				engine.setUseClientMode(true);

				pipeline.addLast("ssl", new SslHandler(engine));
			}

			//handle HttpChunks
			pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
			//automatic content decompression
			//pipeline.addLast("inflater", new HttpContentDecompressor());
			pipeline.addLast("handler", new HttpResponseHandler());
			return pipeline;
		}
	}

	static class HttpResponseHandler extends SimpleChannelUpstreamHandler {

		private boolean readingChunks;

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
			if (!readingChunks) {
				HttpResponse response = (HttpResponse) e.getMessage();

				System.out.println("STATUS: " + response.getStatus());
				System.out.println("VERSION: " + response.getProtocolVersion());
				System.out.println();

				if (!response.getHeaderNames().isEmpty()) {
					for (String name : response.getHeaderNames()) {
						for (String value : response.getHeaders(name)) {
							System.out.println("HEADER: " + name + " = " + value);
						}
					}
					System.out.println();
				}

				if (response.isChunked()) {
					readingChunks = true;
					System.out.println("CHUNKED CONTENT {");
				} else {
					ChannelBuffer content = response.getContent();
					if (content.readable()) {
						System.out.println("CONTENT {");
						System.out.println(content.toString(CharsetUtil.UTF_8));
						System.out.println("} END OF CONTENT");
					}
				}
			} else {
				HttpChunk chunk = (HttpChunk) e.getMessage();
				if (chunk.isLast()) {
					readingChunks = false;
					System.out.println("} END OF CHUNKED CONTENT");
				} else {
					System.out.print(chunk.getContent().toString(CharsetUtil.UTF_8));
					System.out.flush();
				}
			}
		}
	}
}
