package com.anthavio.httl;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import org.codehaus.jackson.map.ObjectMapper;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.security.BasicAuthenticator;
import org.mortbay.jetty.security.Constraint;
import org.mortbay.jetty.security.ConstraintMapping;
import org.mortbay.jetty.security.DigestAuthenticator;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.Password;
import org.mortbay.jetty.security.SecurityHandler;
import org.mortbay.jetty.security.UserRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthavio.httl.TestResponse.NameValue;

public class JokerServer {

	private Logger logger = LoggerFactory.getLogger(getClass());

	private Server jetty;

	private int httpPort;

	private ServerSocket serverSocket;

	private int frozenPort;

	private int nonHttpPort;

	private int httpCode = HttpURLConnection.HTTP_OK;

	private String etag = "\"this_is_computed_in_real_life\"";

	private AtomicInteger requestCount = new AtomicInteger();

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");

	public JokerServer() {
		try {
			//httper
			jetty = new Server(0); //dynamic port
			RequestHandler requestHandler = new RequestHandler();
			//jetty.setHandler(requestHandler);//maps to /*
			jetty.setStopAtShutdown(true);

			//realm
			HashUserRealm realmBasic = new HashUserRealm("MyBasicRealm");
			realmBasic.put("lajka", new Password("haf!haf!"));
			realmBasic.addUserToRole("lajka", "kosmonaut");

			HashUserRealm realmDigest = new HashUserRealm("MyDigestRealm");
			realmDigest.put("zora", new Password("stekystek"));
			realmDigest.addUserToRole("zora", "potapec");

			UserRealm[] realms = { realmBasic, realmDigest };
			jetty.setUserRealms(realms);

			//security
			SecurityHandler basicHandler = getBasicSecurityHandler(realmBasic, "kosmonaut");
			//basicHandler.setHandler(requestHandler); //response handler
			SecurityHandler digestHandler = getDigestSecurityHandler(realmDigest, "potapec");
			//digestHandler.setHandler(requestHandler);//response handler
			jetty.setHandlers(new Handler[] { basicHandler, digestHandler, requestHandler });

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
		httpPort = jetty.getConnectors()[0].getLocalPort();
		logger.info("Http is listening on port " + httpPort);
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

	public int getHttpPort() {
		return httpPort;
	}

	public int getRequestCount() {
		return requestCount.get();
	}

	public int getFrozenPort() {
		return frozenPort;
	}

	private class RequestHandler extends AbstractHandler {

		@Override
		public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
				throws IOException, ServletException {
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
			//((Request) request).setHandled(true);
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
			response.setBufferSize(1024);
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

	private SecurityHandler getBasicSecurityHandler(UserRealm realm, String role) {

		Constraint constraint = new Constraint(Constraint.__BASIC_AUTH, role);
		constraint.setAuthenticate(true);

		ConstraintMapping mapping = new ConstraintMapping();
		mapping.setConstraint(constraint);
		mapping.setPathSpec("/basic/*");

		SecurityHandler handler = new SecurityHandler();
		handler.setAuthenticator(new BasicAuthenticator());
		handler.setUserRealm(realm);
		handler.setConstraintMappings(new ConstraintMapping[] { mapping });

		return handler;
	}

	private SecurityHandler getDigestSecurityHandler(UserRealm realm, String role) {

		Constraint constraint = new Constraint(Constraint.__DIGEST_AUTH, role);
		constraint.setAuthenticate(true);

		ConstraintMapping mapping = new ConstraintMapping();
		mapping.setConstraint(constraint);
		mapping.setPathSpec("/digest/*");

		SecurityHandler handler = new SecurityHandler();
		handler.setAuthenticator(new DigestAuthenticator());
		handler.setUserRealm(realm);
		handler.setConstraintMappings(new ConstraintMapping[] { mapping });

		return handler;

	}

}
