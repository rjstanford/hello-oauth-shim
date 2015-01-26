package cc.protea.foundation.webhooks.hello;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloOAuthShimServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	static Logger log = LoggerFactory.getLogger(HelloOAuthShimServlet.class);
	static Map<String, String> clientSecrets = new HashMap<String, String>();

	// All HTTP methods are handled the same way

	void handle(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

		Map<String, String> parameters = getExpandedParameters(req.getParameterMap());
		Map<String, String> oauth = getOauth(req.getParameterMap());

		if ((parameters.containsKey("code") || parameters.containsKey("refresh_token")) && parameters.containsKey("redirect_uri")) {
			HelloOAuth2Shim.login(parameters, oauth, resp);
			return;
		}

		if ((parameters.containsKey("redirect_uri") && getOAuthVersion(oauth.get("version")) == 1) ||
				parameters.containsKey("token_url") ||
				parameters.containsKey("oauth_token")) {
			HelloOAuth1Shim.login(parameters, oauth, resp);
			return;
		}

		if (parameters.containsKey("access_token") && parameters.containsKey("path")) {
			HelloOAuth1Shim.signRequest(parameters, req, resp);
			return;
		}

		if (parameters.containsKey("path")) {
			HelloOAuth1Shim.processRequest(parameters.get("path"), parameters, req, resp);
			return;
		}

		// ERROR

		HelloOAuthShimServlet.log.error("Unrecognized request: " + req.toString());
		HelloOAuthUtils.serve("{ error: { code : 'invalid_request', message : 'The request is unrecognised' } }", parameters, resp);
	}

	// Parameter parsing utilities

	Map<String, String> getOauth(final Map<String, String[]> in) {
		Map<String, String> map = new HashMap<String, String>();
		if (in == null || ! in.containsKey("state") || in.get("state").length == 0) {
			return map;
		}
		map.putAll(HelloOAuthUtils.jsonToMap(in.get("state")[0], "oauth"));
		return map;
	}

	Map<String, String> getExpandedParameters(final Map<String, String[]> in) {
		Map<String, String> map = new HashMap<String, String>();
		if (in == null) {
			return map;
		}
		for (String key : in.keySet()) {
			String[] value = in.get(key);
			if (value != null && value.length > 0) {
				System.out.println("Key: " + key + " = " + value[0].trim());
				map.put(key,  value[0].trim());
			}
		}
		if (map.containsKey("state")) {
			Map<String, String> fromJson = HelloOAuthUtils.jsonToMap(map.get("state"));
			fromJson.remove("state");
			map.putAll(fromJson);
		}
		return map;
	}

	int getOAuthVersion(final String oauth) {
		if (oauth == null) {
			return 0;
		}
		StringBuilder sb = new StringBuilder();
		for (Character c : oauth.toCharArray()) {
			if (Character.isDigit(c)) {
				sb.append(c);
			}
			if (c.equals('.')) {
				break;
			}
		}
		try {
			return Integer.parseInt(sb.toString());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// All HTTP methods are handled the same way

	protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		handle(req, resp);
	}

	protected void doDelete(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		handle(req, resp);
	}

	protected void doOptions(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		handle(req, resp);
	}

	protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		handle(req, resp);
	}

	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		handle(req, resp);
	}

	public static void addSecret(final String publicKey, final String privateKey) {
		HelloOAuthShimServlet.clientSecrets.put(publicKey, privateKey);
	}

	public static void setSecrets(final Map<String, String> secrets) {
		HelloOAuthShimServlet.clientSecrets = secrets;
	}

	static String getClientSecret(final String clientId) {
		return HelloOAuthShimServlet.clientSecrets.get(clientId);
	}


}
