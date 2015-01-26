package cc.protea.foundation.webhooks.hello;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue.ValueType;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

public class HelloOAuthUtils {

	// Response Utilities

	public static Map<String, String> parseResponse(final String body) {
		Map<String, String> map = new HashMap<String, String>();
		map.putAll(HelloOAuthUtils.jsonToMap(body, null, false));
		if (map.isEmpty()) {
			String[] pairs = body.split("&");
			for (String pair : pairs) {
				String[] keyValue = pair.split("=");
				if (keyValue.length == 2) {
					map.put(HelloOAuthUtils.decode(keyValue[0]), HelloOAuthUtils.decode(keyValue[1]));
				}
			}
		}
		return map;
	}

	// JSON Utilities

	public static Map<String, String> jsonToMap(final String json) {
		return HelloOAuthUtils.jsonToMap(json, null);
	}

	public static Map<String, String> jsonToMap(final String json, final String component) {
		return HelloOAuthUtils.jsonToMap(json, component, true);
	}

	public static Map<String, String> jsonToMap(final String json, final String component, final boolean logError) {
		HashMap<String, String> map = new HashMap<String, String>();
		if (json == null || json.trim().length() == 0) {
			return map;
		}
		JsonReader jsonReader = null;
		try {
			jsonReader = Json.createReader(new ByteArrayInputStream(json.getBytes()));
			JsonObject jsonObject = jsonReader.readObject();
			if (component != null && jsonObject.containsKey(component) && jsonObject.get(component).getValueType().equals(ValueType.OBJECT)) {
				jsonObject = jsonObject.getJsonObject(component);
			}
			for (String key : jsonObject.keySet()) {
				switch(jsonObject.get(key).getValueType()) {
				case NULL:
					map.put(key, null);
					break;
				case STRING:
					map.put(key, jsonObject.getString(key));
					break;
				case NUMBER:
					map.put(key, jsonObject.getJsonNumber(key).toString());
					break;
				default:
					// no-op
				}
			}
		} catch (Exception je) {
			if (logError) {
				Logger.getLogger(HelloOAuthUtils.class.getName()).warning("Could not parse as JSON: " + json);
			}
		} finally {
			if (jsonReader != null) {
				jsonReader.close();
			}
		}
		return map;
	}

	// URL Utilities

	public static Map<String, String> getQueryMap(final String query) {
		Map<String, String> map = new HashMap<String, String>();
		if (! query.contains("?")) {
			return map;
		}
		String[] params = query.substring(query.indexOf("?") + 1).split("&");
		for (String param : params) {
			if (param == null || param.trim().length() == 0) {
				continue;
			}
			if (param.indexOf("=") != -1) {
				String key = param.split("=")[0];
				String value = param.split("=")[1];
				map.put(key, value);
			} else {
				map.put(param, "");
			}
		}
		return map;
	}

	public static String encode(final String in) {
		try {
			return URLEncoder.encode(in, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return in;
		}
	}

	public static String decode(final String in) {
		try {
			return URLDecoder.decode(in, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return in;
		}
	}

	public static String addParameter(final String url, final String key, final String value) {
		if (url == null || key == null || value == null) {
			return url;
		}
		return new StringBuilder().append(url).append(HelloOAuthUtils.getAppendChar(url)).append(key).append("=").append(HelloOAuthUtils.encode(value)).toString();
	}

	static char getAppendChar(final String url) {
		return url.contains("?") ? '&' : '?';
	}

	// Generic Utilities

	static String getFirst(final String... options) {
		for (String option : options) {
			if (option != null && option.trim().length() > 0) {
				return option;
			}
		}
		return null;
	}

	// Shim Utilities

	static void error(final String url, final String error, final String message, final String state, final HttpServletResponse resp) throws IOException {
		Map<String, String> map = new HashMap<String, String>();
		map.put("error", error);
		map.put("error_message", message);
		map.put("state", state);
		HelloOAuthUtils.redirect(url, map, resp);
	}

	static void redirect(String path, final Map<String, String> hash, final HttpServletResponse resp) throws IOException {
		resp.addHeader("Access-Control-Allow-Origin", "*");
		if (hash != null) {
			path = path + "?";
			for (String key : hash.keySet()) {
				path = path + (path.endsWith("#") ? "" : "&") + HelloOAuthUtils.encode(key) + "=" + HelloOAuthUtils.encode(hash.get(key));
			}
		}
		System.out.println("Sending redirect to: " + path);
		resp.sendRedirect(path);
	}

	static void serve(final String body, final Map<String, String> parameters, final HttpServletResponse resp) throws IOException {
		resp.addHeader("Access-Control-Allow-Origin", "*");
		ServletOutputStream os = resp.getOutputStream();
		System.out.println("Serving response: " + body);
		if (parameters.containsKey("callback")) {
			System.out.println("  - Using callback: " + parameters.get("callback"));
			os.print(parameters.get("callback"));
			os.print("('");
			os.print(body);
			os.print("')");
			resp.getOutputStream().print(body);
			return;
		}

		os.print(body);
	}

	// OAuth Utilities

	static String hashString(final String key, final String in) {
		try {
			SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), "HmacSHA1");
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(signingKey);
			byte[] rawHmac = mac.doFinal(in.getBytes());
			return Base64.encodeToString(rawHmac, false);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	public static String sign(final String uri, final Map<String, String> opts, final String consumerSecret, final String tokenSecret) {
		return HelloOAuthUtils.sign(uri, opts, consumerSecret, tokenSecret, null, null, null);
	}

	public static String sign(final String uri, final Map<String, String> opts, final String consumerSecret, final String tokenSecret, final String nonce, final String method, final Map<String, String> data) {

		String path = uri.replaceAll("[?#].*", "");
		Map<String, String> qs = HelloOAuthUtils.getQueryMap(uri);
		SortedMap<String, String> query = new TreeMap<String, String>();

		query.put("oauth_nonce", nonce == null ? "" + UUID.randomUUID().toString().replaceAll("-", "") : nonce);
		query.put("oauth_timestamp", nonce == null ? "" + System.currentTimeMillis() / 1000: nonce);
		query.put("oauth_signature_method", "HMAC-SHA1");
		query.put("oauth_version", "1.0");

		query.putAll(opts);
		query.putAll(qs);
		if (data != null) {
			query.putAll(data);
		}

		String params = "";
		String queryString = "";

		for(String key : query.keySet()) {
			params = HelloOAuthUtils.addParameter(params, key, query.get(key));
			if (data == null || ! data.containsKey(key)) {
				queryString = HelloOAuthUtils.addParameter(queryString, key, query.get(key));
			}
		}

		// Remove leading '?' characters
		params = params.substring(1);
		queryString = queryString.substring(1);

		StringBuilder http = new StringBuilder(method == null ? "GET" : method);
		http.append("&").append(HelloOAuthUtils.encode(path).replaceAll("\\+"," ").replaceAll("%7E", "~"));
		http.append("&").append(HelloOAuthUtils.encode(params).replaceAll("\\+"," ").replaceAll("%7E", "~"));

		String key = consumerSecret + "&" + (tokenSecret == null ? "" : tokenSecret);
		String signature = HelloOAuthUtils.hashString(key, http.toString());

		query.put("oauth_signature", signature);

		return path + "?" + queryString + "&oauth_signature=" + HelloOAuthUtils.encode(query.get("oauth_signature"));
	}

}
