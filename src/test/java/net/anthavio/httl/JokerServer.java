package net.anthavio.httl;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import net.anthavio.httl.TestResponse.NameValue;

import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Password;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JokerServer {

	private Logger logger = LoggerFactory.getLogger(getClass());

	private Server jetty;

	private int portHttp;

	private int portHttps;

	private int portHttpsMutual;

	private ServerSocket serverSocket;

	private int frozenPort;

	private int httpCode = HttpURLConnection.HTTP_OK;

	private String etag = "\"this_is_computed_in_real_life\"";

	private AtomicInteger requestCount = new AtomicInteger();

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");

	public JokerServer() {
		try {
			//httper
			jetty = new Server();
			SelectChannelConnector conHttp = new SelectChannelConnector();
			conHttp.setPort(0);//dynamic port

			//server ssl
			SslContextFactory sslFactory = new SslContextFactory("src/test/resources/localhost.jks");
			sslFactory.setKeyStorePassword("password");
			sslFactory.setKeyManagerPassword("password");
			SslSelectChannelConnector conHttps = new SslSelectChannelConnector(sslFactory);
			conHttps.setPort(0);//dynamic port

			//mutual ssl (require client cert)
			SslContextFactory sslFactoryMut = new SslContextFactory("src/test/resources/localhost.jks");
			sslFactoryMut.setKeyStorePassword("password");
			sslFactoryMut.setKeyManagerPassword("password");
			sslFactoryMut.setCertAlias("localhost");
			sslFactoryMut.setNeedClientAuth(true);
			SslSelectChannelConnector conHttpMut = new SslSelectChannelConnector(sslFactoryMut);
			conHttpMut.setPort(0);//dynamic port

			jetty.setConnectors(new Connector[] { conHttp, conHttps, conHttpMut });

			jetty.setStopAtShutdown(true);

			//realm
			HashLoginService basicLoginService = new HashLoginService("MyBasicRealm");
			basicLoginService.putUser("lajka", new Password("haf!haf!"), new String[] { "lajka", "kosmonaut" });

			jetty.addBean(basicLoginService);

			HashLoginService digestLoginService = new HashLoginService("MyDigestRealm");
			digestLoginService.putUser("zora", new Password("stekystek"), new String[] { "zora", "potapec" });

			//jetty.addBean(digestLoginService);

			/*
			HashUserRealm realmBasic = new HashUserRealm("MyBasicRealm");
			realmBasic.put("lajka", new Password("haf!haf!"));
			realmBasic.addUserToRole("lajka", "kosmonaut");

			HashUserRealm realmDigest = new HashUserRealm("MyDigestRealm");
			realmDigest.put("zora", new Password("stekystek"));
			realmDigest.addUserToRole("zora", "potapec");

			UserRealm[] realms = { realmBasic, realmDigest };
			jetty.setUserRealms(realms);
			*/

			//security

			RequestHandler servlet = new RequestHandler();
			ServletHolder servletHolder = new ServletHolder(servlet);
			//ServletHandler servletHandler = new ServletHandler();
			//servletHandler.addServletWithMapping(new ServletHolder(new RequestHandler()), "/*");

			SecurityHandler digestHandler = getDigestSecurityHandler(digestLoginService, "potapec");
			//digestHandler.setHandler(servletHandler);//response handler

			SecurityHandler basicHandler = getBasicSecurityHandler(basicLoginService, "kosmonaut");
			//basicHandler.setHandler(servletHandler); //response handler

			ServletContextHandler basicContext = new ServletContextHandler();
			basicContext.setContextPath("/basic");
			basicContext.setSecurityHandler(basicHandler);
			basicContext.addServlet(servletHolder, "/*");

			ServletContextHandler digestContext = new ServletContextHandler();
			digestContext.setContextPath("/digest");
			digestContext.setSecurityHandler(digestHandler);
			digestContext.addServlet(servletHolder, "/*");

			ServletContextHandler rootContext = new ServletContextHandler();
			rootContext.setContextPath("/");
			rootContext.addServlet(servletHolder, "/*");

			HandlerList handlers = new HandlerList();
			handlers.addHandler(basicContext);
			handlers.addHandler(digestContext);
			handlers.addHandler(rootContext);

			jetty.setHandler(handlers);

			//server socket with single element backlog queue and dynamicaly allocated port
			serverSocket = new ServerSocket(0, 1);
			frozenPort = serverSocket.getLocalPort();
			//fill backlog queue by this request so any other request will hang
			new Socket().connect(serverSocket.getLocalSocketAddress());
		} catch (Exception x) {
			throw new RuntimeException(x);
		}
	}

	public JokerServer start() {
		try {
			jetty.start();
		} catch (Exception x) {
			throw new RuntimeException(x);
		}
		portHttp = jetty.getConnectors()[0].getLocalPort();
		portHttps = jetty.getConnectors()[1].getLocalPort();
		portHttpsMutual = jetty.getConnectors()[2].getLocalPort();
		logger.info("Http is listening on port " + portHttp + ", " + portHttps + "," + portHttpsMutual);
		logger.info("Freezer is listening on port " + frozenPort);
		return this;
	}

	public void stop() {
		try {
			jetty.stop();
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	public String getEtag() {
		return etag;
	}

	public void setEtag(String etag) {
		this.etag = etag;
	}

	public void setHttpCode(int httpCode) {
		this.httpCode = httpCode;
	}

	public int getPortHttp() {
		return portHttp;
	}

	public int getPortHttps() {
		return portHttps;
	}

	public int getPortHttpsMutual() {
		return portHttpsMutual;
	}

	public int getRequestCount() {
		return requestCount.get();
	}

	public int getFrozenPort() {
		return frozenPort;
	}

	private class RequestHandler extends HttpServlet {

		private static final long serialVersionUID = 1L;

		@Override
		protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException,
				IOException {

			//@Override
			//public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			//	throws IOException, ServletException {

			logger.info("Handling " + request);

			requestCount.incrementAndGet();
			Date now = new Date();

			int httpc = httpCode; //save before sleep

			//request.setCharacterEncoding("utf-8");
			String hsleep = request.getHeader("sleep");
			if (hsleep != null) {
				sleep(hsleep, request);
			}

			String psleep = request.getParameter("sleep");
			if (psleep != null) {
				sleep(psleep, request);
			}

			if (httpc != HttpURLConnection.HTTP_OK) {
				response.sendError(httpc);
				((Request) request).setHandled(true);
				return;
			}

			String docache = request.getParameter("docache");
			if (docache != null) {
				int seconds = Integer.parseInt(docache);
				Calendar calendar = GregorianCalendar.getInstance();
				calendar.setTime(now);
				calendar.add(Calendar.SECOND, seconds);
				//HTTP 1.0 - Expire header
				response.setDateHeader("Date", now.getTime());
				response.setDateHeader("Expire", calendar.getTimeInMillis());
				//HTTP 1.1 - Cache-Control header
				response.setHeader("Cache-Control", "private, max-age=" + seconds);
				writeResponse(request, response, now, "Docache for " + seconds + " seconds");
				return;
			}

			String doetag = request.getParameter("doetag");
			if (doetag != null) {
				String cetag = request.getHeader("If-None-Match");
				if (etag.equals(cetag)) {
					response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
					((Request) request).setHandled(true);
					return; //no response...
				} else {
					response.setHeader("ETag", etag);
					//we need unique content - put date into it
					String message = "Doetag  for " + etag + " ETag";
					writeResponse(request, response, now, message);
					return;
				}
			}

			String pstatus = request.getParameter("dostatus");
			if (pstatus != null) {
				int status = Integer.parseInt(pstatus);
				response.setStatus(status);
				writeResponse(request, response, now, "Dostatus " + status);
				return;
			}

			if (((Request) request).isHandled() == false) {
				response.setStatus(HttpServletResponse.SC_OK);
				writeResponse(request, response, now, "Hello");
			}

		}

		private void writeResponse(HttpServletRequest request, HttpServletResponse response, Date now, String text)
				throws IOException {
			//System.out.println(request.getCharacterEncoding());
			//System.out.println(request.getParameter("pmsg"));
			String contentType = request.getContentType();

			TestBodyRequest trequest = null;
			//System.out.println(contentType + ", " + request.getCharacterEncoding());
			if (contentType != null) {
				if (contentType.startsWith("application/json")) {
					trequest = new ObjectMapper().readValue(request.getReader(), TestBodyRequest.class);
					//request.getInputStream();
				} else if (contentType.startsWith("text/xml") || contentType.startsWith("application/xml")) {
					try {
						JAXBContext context = JAXBContext.newInstance(TestBodyRequest.class);
						Unmarshaller unmarshaller = context.createUnmarshaller();
						JAXBElement<TestBodyRequest> element = unmarshaller.unmarshal(new StreamSource(request.getInputStream()),
								TestBodyRequest.class);
						trequest = element.getValue();
					} catch (Exception x) {
						x.printStackTrace();
					}
				}
			}
			/*
			if (trequest != null) {
				System.out.println(trequest.getMessage());
			} else if (request.getParameter("xxx") != null) {
				System.out.println(request.getParameter("xxx"));
			}
			*/
			String message = text + " at " + sdf.format(now);

			TestResponse.Request toutrequest = new TestResponse.Request();
			if (trequest != null) {
				toutrequest.setMessage(trequest.getMessage());
			}

			List<TestResponse.NameValue> parameters = new ArrayList<TestResponse.NameValue>();
			Enumeration<String> parameterNames = request.getParameterNames();
			while (parameterNames.hasMoreElements()) {
				String name = parameterNames.nextElement();
				parameters.add(new NameValue(name, request.getParameter(name)));
			}
			toutrequest.setParameters(parameters);

			List<TestResponse.NameValue> headers = new ArrayList<TestResponse.NameValue>();
			Enumeration<String> headerNames = request.getHeaderNames();
			while (headerNames.hasMoreElements()) {
				String name = headerNames.nextElement();
				headers.add(new NameValue(name, request.getHeader(name)));
			}
			toutrequest.setHeaders(headers);

			TestResponse tresponse = new TestResponse(message, toutrequest);

			String outputCharset;
			String acceptCharset = request.getHeader("Accept-Charset");
			if (acceptCharset != null) {
				outputCharset = acceptCharset;
			} else if (contentType != null) {
				int idxCharset = contentType.indexOf("charset=");
				if (idxCharset != -1) {
					outputCharset = contentType.substring(idxCharset + 8);
				} else {
					outputCharset = "ISO-8859-1";
				}
			} else {
				outputCharset = "ISO-8859-1";
			}

			response.setCharacterEncoding(outputCharset);
			//response.setBufferSize(1024);
			ServletOutputStream output = response.getOutputStream();

			String acceptMimeType = request.getHeader("Accept");
			acceptMimeType = acceptMimeType == null ? "text/html" : acceptMimeType;

			if (acceptMimeType.startsWith("application/json")) {

				response.setContentType("application/json");
				new ObjectMapper().writeValue(new OutputStreamWriter(output, outputCharset), tresponse);

			} else if (acceptMimeType.startsWith("text/xml") || acceptMimeType.startsWith("application/xml")) {

				response.setContentType("application/xml");
				try {
					JAXBContext context = JAXBContext.newInstance(TestResponse.class);
					Marshaller marshaller = context.createMarshaller();
					marshaller.setProperty(Marshaller.JAXB_ENCODING, outputCharset);
					//http://java.dzone.com/articles/jaxb-no-annotations-required
					QName qname = new QName("response");
					JAXBElement<TestResponse> element = new JAXBElement<TestResponse>(qname, TestResponse.class, tresponse);
					marshaller.marshal(element, output);
				} catch (JAXBException jx) {
					jx.printStackTrace();
				}
			} else {
				//default output type is html
				response.setContentType("text/html");
				output.println("<h1> " + message + "</h1>");
				//TODO html output of parameters and headers...
			}
			((Request) request).setHandled(true);
		}

		private void sleep(String value, HttpServletRequest request) {
			int seconds = Integer.parseInt(value);
			if (seconds != 0) {
				logger.info("server sleep for " + seconds + " seconds");
				try {
					Thread.sleep(seconds * 1000);
				} catch (InterruptedException ix) {
					//nothing
				}
				logger.info("server waked after " + seconds + " seconds");
			}
		}

	}

	private SecurityHandler getBasicSecurityHandler(LoginService realm, String role) {

		Constraint constraint = new Constraint();
		constraint.setName(Constraint.__BASIC_AUTH);
		constraint.setAuthenticate(true);
		constraint.setRoles(new String[] { role });

		ConstraintMapping mapping = new ConstraintMapping();
		mapping.setPathSpec("/*");
		mapping.setConstraint(constraint);

		ConstraintSecurityHandler handler = new ConstraintSecurityHandler();
		handler.setConstraintMappings(Collections.singletonList(mapping));
		handler.setAuthenticator(new BasicAuthenticator());
		handler.setLoginService(realm);
		return handler;
	}

	private ConstraintSecurityHandler getDigestSecurityHandler(LoginService realm, String role) {
		Constraint constraint = new Constraint(Constraint.__DIGEST_AUTH, role);
		constraint.setAuthenticate(true);

		ConstraintMapping mapping = new ConstraintMapping();
		mapping.setPathSpec("/*");
		mapping.setConstraint(constraint);

		//HashSet<String> roles = new HashSet<String>();
		//roles.add(role);

		ConstraintSecurityHandler handler = new ConstraintSecurityHandler();
		handler.setConstraintMappings(Collections.singletonList(mapping));
		handler.setAuthenticator(new DigestAuthenticator());
		handler.setLoginService(realm);
		return handler;
	}

}
