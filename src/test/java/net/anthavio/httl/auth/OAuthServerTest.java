package net.anthavio.httl.auth;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.anthavio.httl.HttlBuilderVisitor;
import net.anthavio.httl.HttlRequestBuilders.HttlRequestBuilder;
import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.HttlSender;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * 
 * @author martin.vanek
 *
 */
public class OAuthServerTest extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public static void main(String[] args) {
		int port = new OAuthServerTest(3030).start();
		System.out.println("Jetty running on " + port);
	}

	private final Server jetty;

	private int httpPort;

	public OAuthServerTest(int port) {
		jetty = new Server(port); //0 = dynamic port
		jetty.setStopAtShutdown(true);
		ServletHolder servletHolder = new ServletHolder(this);

		ServletContextHandler rootContext = new ServletContextHandler();
		rootContext.setContextPath("/");
		rootContext.addServlet(servletHolder, "/*");
		jetty.setHandler(rootContext);
	}

	public int start() {
		try {
			jetty.start();
		} catch (Exception x) {
			throw new RuntimeException(x);
		}
		httpPort = jetty.getConnectors()[0].getLocalPort();
		return httpPort;
	}

	public void stop() {
		if (jetty.isStarted()) {
			try {
				jetty.stop();
			} catch (Exception x) {
				x.printStackTrace();
			}
		}
	}

	private OAuth2 builder;
	private HttlSender sender;

	@Override
	public void init() throws ServletException {
		super.init();
		//ObjectMapper mapper = new ObjectMapper();
		//mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		sender = HttlSender.For("https://github.com").setHeader("Accept", "application/json").build();

		builder = new OAuth2Builder(sender).setAuthUrl("https://github.com/login/oauth/authorize")
				.setTokenUrl("https://github.com/login/oauth/access_token").setClientId("22d827124162f9f9a81b")
				.setClientSecret("91f27372b6835a31a963be4007c090cdb6b4def3")
				.setRedirectUri("http://local.nature.com:3030/callback/github").build();
		/*
		HttlSender sender = HttlSender.Build("https://accounts.google.com");
		builder = new OAuth2Builder(sender).setAuthUrl("https://accounts.google.com/o/oauth2/auth")
				.setTokenUrl("https://accounts.google.com/o/oauth2/token")
				.setClientId("164620382615-95h6b232a0uin7toqka3nbhdf3noip9c.apps.googleusercontent.com")
				.setClientSecret("T3Z_hlCWtwT1hiJVuRt1WWCH").setRedirectUri("http://local.nature.com:3030/callback/google")
				.build();
		*/
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.getWriter().print(request.getRequestURI());
		response.getWriter().println(request.getParameterMap());

		if (request.getRequestURI().contains("authorize")) {
			//"public access" github
			String url = builder.getAuthUrl("random-state", "openid email");
			System.out.println(url);
			response.sendRedirect(url);

		} else if (request.getRequestURI().contains("callback")) {
			String error = request.getParameter("error");
			if (error != null) {
				response.getWriter().println(error);
				response.getWriter().println(request.getParameter("error_description"));
			} else {
				System.out.println("Code callback!");
				String code = request.getParameter("code");
				HttlBuilderVisitor visitor = new HttlBuilderVisitor() {

					@Override
					public void visit(HttlRequestBuilder<?> builder) {
						builder.accept("application/json");

					}
				};
				OAuthTokenResponse tokenResponse = builder.getAccessToken(code, visitor, OAuthTokenResponse.class);
				String access_token = tokenResponse.getAccess_token();
				ExtractedResponse<String> extract = sender.GET("/user").param("access_token", access_token)
						.extract(String.class);
				response.getWriter().print(extract);

			}
		} else {
		}
	}

}
