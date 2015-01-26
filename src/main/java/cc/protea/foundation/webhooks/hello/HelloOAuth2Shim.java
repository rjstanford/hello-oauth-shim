package cc.protea.foundation.webhooks.hello;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import cc.protea.util.http.Request;
import cc.protea.util.http.Response;

public class HelloOAuth2Shim {

	public static void login(final Map<String, String> parameters, final Map<String, String> oauth, final HttpServletResponse resp) throws IOException {

		String clientSecret = HelloOAuthShimServlet.getClientSecret(parameters.get("client_id"));
		if (clientSecret == null) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), "invalid_credentials", "Credentials were not recognised", parameters.get("state"), resp);
			return;
		}

		Map<String, String> post = new HashMap<String, String>();
		post.put("client_id", parameters.get("client_id"));
		post.put("client_secret", clientSecret);

		if (parameters.containsKey("code")) {
			post.put("code", parameters.get("code"));
			post.put("grant_type", "authorization_code");
			post.put("redirect_uri", parameters.get("redirect_uri"));
		} else if (parameters.containsKey("refresh_token")) {
			post.put("refresh_token", parameters.get("refresh_token"));
			post.put("grant_type", "refresh_token");
		}

		String grantUrl = HelloOAuthUtils.getFirst(parameters.get("grant_url"), parameters.get("grant"), oauth.get("grant"));
		if (grantUrl == null) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), "required_grant", "Missing parameter grant_url", parameters.get("state"), resp);
			return;
		}

		Request request = new Request(grantUrl);
		request.setBodyUrlEncoded(post);
		Response response = null;

		try {
			System.out.println("Request:  " + grantUrl);
			response = request.postResource();
			System.out.println("Response: " + response.getBody());
		} catch (IOException e) {
			HelloOAuthUtils.error(parameters.get("redirect_uri"), "server_error", "Unable to connect to " + grantUrl, parameters.get("state"), resp);
			return;
		}

		String body = response.getBody();
		Map<String, String> responseMap = HelloOAuthUtils.parseResponse(body);

		if (response.getResponseCode() >= 400) {
			responseMap.put("error", "invalid_grant");
			responseMap.put("error_message", "Could not find the authenticating server " + grantUrl);
		} else if (! responseMap.containsKey("access_token")) {
			responseMap.put("error", "invalid_grant");
			responseMap.put("error_message", "Could not get a sensible response from the authenticating server " + grantUrl);
		} else if (! responseMap.containsKey("expires_in")) {
			responseMap.put("expires_in", "3600");
		}

		if (parameters.containsKey("state")) {
			responseMap.put("state", parameters.get("state"));
		}

		if (parameters.containsKey("refresh_token") && ! responseMap.containsKey("refresh_token")) {
			responseMap.put("refresh_token", parameters.get("refresh_token"));
		}

		HelloOAuthUtils.redirect(parameters.get("redirect_uri"), responseMap, resp);

	}

}
