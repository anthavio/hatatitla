package net.anthavio.httl.auth;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.anthavio.httl.HttlBuilderVisitor;
import net.anthavio.httl.HttlRequestBuilders.HttlRequestBuilder;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlResponseExtractor.ExtractedResponse;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.util.HttpHeaderUtil;

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
		jetty = new Server(port);
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

		//Twitter has OAuth 1.0 - https://dev.twitter.com/docs/auth/implementing-sign-twitter
		/*
		sender = HttlSender.For("https://api.worldoftanks.eu").setHeader("Accept", "application/json").build();

		builder = new OAuth2Builder(sender).setStrict(false).setAuthUrl("/wot/auth/login/")
				.setCustomParam("application_id", "a58197e5c9dc213a5c56865014dbd08c")
				.setRedirectUri("http://local.nature.com:3030/callback/wot").build();
		*/

		/*
		//Facebook https://developers.facebook.com/docs/facebook-login/manually-build-a-login-flow/v2.1
		sender = HttlSender.with("https://graph.facebook.com").config().addHeader("Accept", "application/json").build();

		builder = new OAuth2Builder(sender).setAuthUrl("https://www.facebook.com/dialog/oauth")
				.setClientId("257584864432184").setClientSecret("362da435e18c5fe6424f993541f15690").setAuthResponseType("code")
				.setTokenUrl("https://graph.facebook.com/oauth/access_token")
				.setCustomParam("display", "popup").setRedirectUri("http://localhost:3030/callback/facebook").build();
		*/
		/*
		sender = HttlSender.For("https://github.com").setHeader("Accept", "application/json").build();
		builder = new OAuth2Builder(sender).setAuthUrl("https://github.com/login/oauth/authorize")
				.setTokenUrl("https://github.com/login/oauth/access_token").setClientId("22d827124162f9f9a81b")
				.setClientSecret("91f27372b6835a31a963be4007c090cdb6b4def3")
				.setRedirectUri("http://local.nature.com:3030/callback/github").build();
		*/
		/*
		sender = HttlSender.Build("https://accounts.google.com");
		builder = new OAuth2Builder(sender).setAuthUrl("https://accounts.google.com/o/oauth2/auth")
				.setTokenUrl("https://accounts.google.com/o/oauth2/token")
				.setClientId("164620382615-95h6b232a0uin7toqka3nbhdf3noip9c.apps.googleusercontent.com")
				.setClientSecret("T3Z_hlCWtwT1hiJVuRt1WWCH").setRedirectUri("http://local.nature.com:3030/callback/google")
				.build();
		*/
		/*
		sender = HttlSender.with("https://disqus.com").build();
		builder = new OAuth2Builder(sender).setAuthUrl("/api/oauth/2.0/authorize/")
				.setTokenUrl("/api/oauth/2.0/access_token/")
				.setClientId("gOqjfpFfNaEqPBvctATwmhdvNgLA4lgMks5NQu3kDtq2oD9leufjLBGclWLhFKaE")
				.setClientSecret("LBlyuwnClIlTy58y1GYVd8g7pS8658cZHuJuZpZnvX9wwY84gb6dYpBIPcrofoNu")
				.setRedirectUri("http://local.nature.com:3030/callback/disqus").build();
		*/

		sender = HttlSender.url("https://login.salesforce.com").httpClient4().sender().build();

		builder = OAuth2.Builder().setAuthorizationUrl("/services/oauth2/authorize")
				.setTokenEndpoint(sender, "/services/oauth2/token")
				.setClientId("3MVG9A_f29uWoVQs9OTALjR7nPS40H71arZb6KCiJTZSTqBf2yTLqemMxAoXWSj75X9ReK7OIePpbFytVpg_r")
				.setClientSecret("9045636227386680652").setRedirectUri("https://local.nature.com:3030/callback/salesforce")
				.build();

	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		System.out.print(request.getRequestURI());
		System.out.println(request.getParameterMap());

		response.getWriter().print(request.getRequestURI());
		response.getWriter().println(request.getParameterMap());

		if (request.getRequestURI().contains("authorize")) {
			String url;
			if (request.getRequestURI().contains("github")) {
				//"public access" github				
				url = builder.getAuthorizationUrl("random-state", "openid email"); //github

			} else if (request.getRequestURI().contains("wot")) {
				url = builder.getAuthorizationUrl(null, null); //wot

			} else if (request.getRequestURI().contains("facebook")) {
				url = builder.getAuthorizationUrl("public_profile,email", "whatever");//facebook

			} else if (request.getRequestURI().contains("disqus")) {
				url = builder.getAuthorizationUrl("read,write", "whatever"); //Disqus

			} else if (request.getRequestURI().contains("loop")) {
				url = builder.getAuthorizationUrl("openid profile email", "whtever"); //Loop

			} else if (request.getRequestURI().contains("salesforce")) {
				OAuthTokenResponse token = builder.password("anthavio@post.cz", "dhrv5haq" + "Bu5YU13kOOrSZeh49BHCM0Dp").get();
				System.out.println(token.getAccess_token());
				response.getWriter().print(token.getAccess_token());

				return;

			} else {
				throw new IllegalArgumentException("Authorize for what?!? " + request.getRequestURI());
			}

			System.out.println("Redirecting to " + url);
			response.sendRedirect(url);

		} else if (request.getRequestURI().contains("callback")) {

			if (request.getRequestURI().contains("wot")) {
				String status = request.getParameter("status");
				if ("ok".equals(status)) {
					String accessToken = request.getParameter("access_token");
					//account_id, nickname, expires_at
					System.out.println("WOT access_token " + accessToken);
				} else if ("error".equals(status)) {
					System.out.println("WOT error callback " + request.getParameter("code") + " "
							+ request.getParameter("message"));
				} else {
					System.out.println("Unknown WOT status " + status);
				}
			} else { //civilized OAuth
				String error = request.getParameter("error");
				String code = request.getParameter("code");
				String token = request.getParameter("token");
				if (error != null) {
					response.getWriter().println(error);
					response.getWriter().println(request.getParameter("error_description"));
				} else if (code != null) {

					HttlBuilderVisitor visitor = new HttlBuilderVisitor() {

						@Override
						public void visit(HttlRequestBuilder<?> builder) {
							builder.header("Accept", "application/json");

						}
					};
					//OAuthTokenResponse tokenResponse = builder.getAccessToken(code, visitor, new FacebookTokenExtractor());
					OAuthTokenResponse tokenResponse = builder.access(code).visitor(visitor).get();
					String access_token = tokenResponse.getAccess_token();

					ExtractedResponse<String> extract;

					if (request.getRequestURI().contains("loop")) {
						extract = sender.GET("/api/v1/profile/me").header("Authorization", "Bearer " + access_token)
								.extract(String.class);

					} else if (request.getRequestURI().contains("disqus")) {
						extract = sender.POST("/api/3.0/users/checkUsername.json").param("access_token", access_token)
								.param("username", "asdewqdddd")
								.param("api_key", "gOqjfpFfNaEqPBvctATwmhdvNgLA4lgMks5NQu3kDtq2oD9leufjLBGclWLhFKaE")
								.extract(String.class);
					} else {
						throw new IllegalArgumentException("Callback for what?!? " + request.getRequestURI());
					}

					response.getWriter().print(extract);
					response.setStatus(200);
				}
			}
		} else {
		}
	}

	/**
	 * https://developers.facebook.com/docs/facebook-login/manually-build-a-login-flow/v2.1#
	 * 
	 * https://developers.facebook.com/docs/facebook-login/access-tokens/
	 */
	class FacebookTokenExtractor implements HttlResponseExtractor<OAuthTokenResponse> {

		/**
		 * access_token={access-token}&expires={seconds-til-expiration}
		 */
		@Override
		public OAuthTokenResponse extract(HttlResponse response) throws IOException {
			if ("text/plain".equals(response.getMediaType())) {
				String extracted = HttpHeaderUtil.readAsString(response);
				String[] parameters = extracted.split("&");
				for (String parameter : parameters) {
					String[] nameValue = parameter.split("=");
					if (nameValue[0].equals("access_token")) {
						return new OAuthTokenResponse(nameValue[1]);
					}
				}
				throw new IllegalArgumentException("access_token not found in " + extracted + " in " + response);

			} else {
				throw new IllegalArgumentException("Can't parse " + response);
			}
		}
	}
}
