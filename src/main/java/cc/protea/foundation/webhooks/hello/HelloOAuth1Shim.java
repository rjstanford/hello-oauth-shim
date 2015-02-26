package cc.protea.foundation.webhooks.hello;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.protea.util.http.Request;
import cc.protea.util.http.Response;

public class HelloOAuth1Shim {

	static Map<String, String> tokenSecrets = new HashMap<String, String>();
	static Logger log = LoggerFactory.getLogger(HelloOAuth1Shim.class);

	public static void login(final Map<String, String> parameters, final Map<String, String> oauth, final HttpServletResponse resp) throws IOException {

		String tokenSecret = null;

		Map<String, String> opts = new HashMap<String, String>();
		opts.put("oauth_consumer_key", parameters.get("client_id"));

		if (parameters.containsKey("access_token")) {
			Matcher m = Pattern.compile("^([^:]+)\\:([^@]+)@(.+)$").matcher(parameters.get("access_token"));
			if (m.matches()) {
				parameters.put("oauth_token", m.group(0));
				tokenSecret = m.group(1);
				if (parameters.containsKey("refresh_token")) {
					opts.put("oauth_session_handle", parameters.get("refresh_token"));
				}
			}
		}

		if (!parameters.containsKey("oauth_token")) {
			HelloOAuth1Shim.firstStep(parameters, oauth, opts, resp);
		} else {
			HelloOAuth1Shim.secondStep(parameters, oauth, opts, tokenSecret, resp);
		}

	}

	public static void firstStep(final Map<String, String> parameters, final Map<String, String> oauth, final Map<String, String> opts, final HttpServletResponse resp) throws IOException {

		String path = (parameters.containsKey("request_url") ? parameters.get("request_url") : oauth.get("request"));
		if (path == null) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), "required_request_url", "A request_url is required", parameters.get("state"), resp);
			return;
		}

		String oauth_callback = parameters.get("redirect_uri");
		oauth_callback = HelloOAuthUtils.addParameter(oauth_callback, "proxy_url", parameters.get("oauth_proxy"));
		oauth_callback = HelloOAuthUtils.addParameter(oauth_callback, "state", parameters.get("state"));
		oauth_callback = HelloOAuthUtils.addParameter(oauth_callback, "token_url", parameters.containsKey("token_url") ? parameters.get("token_url") : oauth.get("token"));
		oauth_callback = HelloOAuthUtils.addParameter(oauth_callback, "client_id", parameters.get("client_id"));

		if ("1.0a".equals(oauth.get("version")) || "1.0a".equals(oauth.get("version"))) {
			opts.put("oauth_callback", oauth_callback);
		}

		String clientSecret = HelloOAuthShimServlet.getClientSecret(parameters.get("client_id"));
		if (clientSecret == null) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), "invalid_credentials", "Credentials were not recognised", parameters.get("state"), resp);
			return;
		}

		String signedUrl = HelloOAuthUtils.sign(path, opts, clientSecret, null);
		Response response = null;

		try {
			System.out.println("OA1 Request:  " + signedUrl);
			response = new Request(signedUrl).getResource();
			System.out.println("OA1 Response: " + response.getBody());
		} catch (IOException e) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), "server_error", "Unable to connect to " + signedUrl, parameters.get("state"), resp);
			return;
		}

		String body = response.getBody();
		Map<String, String> responseMap = HelloOAuthUtils.parseResponse(body);

		if (responseMap.containsKey("error") || response.getResponseCode() >= 400) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), responseMap.containsKey("oauth_problem") ? responseMap.get("oauth_problem") : "auth_failed", response.getResponseCode() + " could not authenticate", parameters.get("state"), resp);
			return;
		}

		if (responseMap.containsKey("oauth_token_secret")) {
			HelloOAuth1Shim.tokenSecrets.put(responseMap.get("oauth_token"), responseMap.get("oauth_token_secret"));
		}

		String url = parameters.containsKey("auth_url") ? parameters.get("auth_url") : oauth.get("auth");

		Map<String, String> map = new HashMap<String, String>();
		map.put("oauth_token", responseMap.get("oauth_token"));
		map.put("oauth_callback", oauth_callback);

		HelloOAuthUtils.redirect(url, map, resp);

	}

	public static void secondStep(final Map<String, String> parameters, final Map<String, String> oauth, final Map<String, String> opts, String tokenSecret, final HttpServletResponse resp) throws IOException {

		String path = (parameters.containsKey("request_url") ? parameters.get("request_url") : oauth.get("request"));
		if (path == null) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), "required_request_url", "A request_url is required", parameters.get("state"), resp);
			return;
		}

		String oauth_callback = parameters.get("redirect_uri");
		oauth_callback = HelloOAuthUtils.addParameter(oauth_callback, "proxy_url", parameters.get("oauth_proxy"));
		oauth_callback = HelloOAuthUtils.addParameter(oauth_callback, "state", parameters.get("state"));
		oauth_callback = HelloOAuthUtils.addParameter(oauth_callback, "token_url", parameters.containsKey("token_url") ? parameters.get("token_url") : oauth.get("token"));
		oauth_callback = HelloOAuthUtils.addParameter(oauth_callback, "client_id", parameters.get("client_id"));

		if ("1.0a".equals(oauth.get("version")) || "1.0a".equals(oauth.get("version"))) {
			opts.put("oauth_callback", oauth_callback);
		}

		path = (parameters.containsKey("token_url") ? parameters.get("token_url") : oauth.get("token"));
		if (path == null) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), "required_token_url", "A token_url is required", parameters.get("state"), resp);
			return;
		}

		opts.put("oauth_token", parameters.get("oauth_token"));
		if (parameters.containsKey("oauth_verifier")) {
			opts.put("oauth_verifier", parameters.get("oauth_verifier"));
		}

		if (tokenSecret == null && HelloOAuth1Shim.tokenSecrets.containsKey(parameters.get("oauth_token"))) {
			tokenSecret = HelloOAuth1Shim.tokenSecrets.get(parameters.get("oauth_token"));
		}

		if (tokenSecret == null) {
			if (parameters.containsKey("oauth_token")) {
				HelloOAuthUtils.error(parameters.get("redirect_uri"), "invalid_oauth_token", "The oauth_token was not recognised", parameters.get("state"), resp);
			} else {
				HelloOAuthUtils.error(parameters.get("redirect_uri"), "required_oauth_token", "The oauth_token is required", parameters.get("state"), resp);
			}
			return;
		}

		String clientSecret = HelloOAuthShimServlet.getClientSecret(parameters.get("client_id"));
		if (clientSecret == null) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), "invalid_credentials", "Credentials were not recognised", parameters.get("state"), resp);
			return;
		}

		String signedUrl = HelloOAuthUtils.sign(path, opts, clientSecret, tokenSecret);
		Response response = null;

		try {
			System.out.println("OA1 Request:  " + signedUrl);
			response = new Request(signedUrl).getResource();
			System.out.println("OA1 Response: " + response.getBody());
		} catch (IOException e) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), "server_error", "Unable to connect to " + signedUrl, parameters.get("state"), resp);
			return;
		}

		String body = response.getBody();
		Map<String, String> responseMap = HelloOAuthUtils.parseResponse(body);

		if (responseMap.containsKey("error") || response.getResponseCode() >= 400) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), responseMap.containsKey("oauth_problem") ? responseMap.get("oauth_problem") : "auth_failed", response.getResponseCode() + " could not authenticate", parameters.get("state"), resp);
			return;
		}

		responseMap.put("access_token", responseMap.get("oauth_token") + ":" + responseMap.get("oauth_token_secret") + "@" + parameters.get("client_id"));
		responseMap.put("state", parameters.containsKey("state") ? parameters.get("state") : "");
		responseMap.remove("oauth_token");
		responseMap.remove("oauth_token_secret");

		if (responseMap.containsKey("oauth_expires_in")) {
			responseMap.put("expires_in", responseMap.get("oauth_expires_in"));
			responseMap.remove("oauth_expires_in");
		}

		if (responseMap.containsKey("oauth_session_handle")) {
			responseMap.put("refresh_token", responseMap.get("oauth_session_handle"));
			responseMap.remove("oauth_session_handle");
			if (responseMap.containsKey("oauth_authorization_expires_in")) {
				responseMap.put("refresh_expires_in", responseMap.get("oauth_authorization_expires_in"));
				responseMap.remove("oauth_authorization_expires_in");
			}
		}

		HelloOAuthUtils.redirect(parameters.get("redirect_uri"), responseMap, resp);
	}

	public static void signRequest(final Map<String, String> parameters, final HttpServletRequest req, final HttpServletResponse resp) throws IOException {

		String method = parameters.containsKey("method") ? parameters.get("method") : req.getMethod();
		Map<String, String> data = HelloOAuthUtils.jsonToMap(parameters.get("data"));

		String url = HelloOAuth1Shim.sign(method, parameters.get("path"), data, parameters.get("access_token"));

		HelloOAuth1Shim.processRequest(url, parameters, req, resp);
	}

	public static void processRequest(final String url, final Map<String, String> parameters, final HttpServletRequest req, final HttpServletResponse resp) throws IOException {

		if (! parameters.containsKey("then")) {
			if (req.getMethod().equalsIgnoreCase("GET")) {
				if (! parameters.containsKey("method") || parameters.get("method").equalsIgnoreCase("GET")) {
					parameters.put("then", "redirect");
				} else {
					parameters.put("then", "return");
				}
			} else {
				parameters.put("then", "proxy");
			}
		}

		if ("redirect".equals(parameters.get("then"))) {
			HelloOAuthUtils.redirect(url, null, resp);
		} else if ("return".equals(parameters.get("then"))) {
			HelloOAuthUtils.serve(url, parameters, resp);
		} else {
			HelloOAuthShimProxy.proxy(url, req, resp);
		}

	}

	static String sign(final String method, final String path, final Map<String, String> data, final String accessToken) {

		Matcher m = Pattern.compile("^([^:]+)\\:([^@]+)@(.+)$").matcher(accessToken);
		if (! m.matches()) {
			return HelloOAuthUtils.addParameter(path, "access_token", accessToken);
		}

		String clientSecret = HelloOAuthShimServlet.getClientSecret(m.group(3));
		if (clientSecret == null) {
			return path;
		}

		Map<String, String> opts = new HashMap<String, String>();
		opts.put("oauth_token", m.group(1));
		opts.put("oauth_consumer_key", m.group(3));
		return HelloOAuthUtils.sign(path, opts, clientSecret, m.group(2), null, method.toUpperCase(), data);

	}

}
