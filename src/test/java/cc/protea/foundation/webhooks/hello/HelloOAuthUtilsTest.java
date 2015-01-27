package cc.protea.foundation.webhooks.hello;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HelloOAuthUtilsTest {

	int test_port = 3333;
	Map<String, String> query = new HashMap<String, String>();

	@Before
	public void init() {
		query.clear();
		query.put("request_url", "http://localhost:" + test_port + "/oauth/request");
		query.put("token_url", "http://localhost:" + test_port + "/oauth/token");
		query.put("auth_url", "http://localhost:" + test_port + "/oauth/auth");
		query.put("version", "1.0a");
		query.put("state", "");
		query.put("client_id", "oauth_consumer_key");
		query.put("redirect_uri", "http://localhost:" + test_port + "/");
	}

	@Test
	public void testSign() throws Exception {
		String callback = "http://location.com/?wicked=knarly&redirect_uri=http%3A%2F%2Flocal.knarly.com%2Fhello.js%2Fredirect.html%3Fstate%3D%257B%2522proxy%2522%253A%2522http%253A%252F%252Flocalhost%2522%257D";
		Map<String, String> opts = new HashMap<String, String>();
		opts.put("oauth_consumer_key", "t5s644xtv7n4oth");
		opts.put("oauth_callback", callback);
		String sign = HelloOAuthUtils.sign("https://api.dropbox.com/1/oauth/request_token", opts, "h9b3uri43axnaid", null, "1354345524", null, null);
		Assert.assertEquals("https://api.dropbox.com/1/oauth/request_token?oauth_callback=http%3A%2F%2Flocation.com%2F%3Fwicked%3Dknarly%26redirect_uri%3Dhttp%253A%252F%252Flocal.knarly.com%252Fhello.js%252Fredirect.html%253Fstate%253D%25257B%252522proxy%252522%25253A%252522http%25253A%25252F%25252Flocalhost%252522%25257D&oauth_consumer_key=t5s644xtv7n4oth&oauth_nonce=1354345524&oauth_signature_method=HMAC-SHA1&oauth_timestamp=1354345524&oauth_version=1.0&oauth_signature=7hCq53%2Bcl5PBpKbCa%2FdfMtlGkS8%3D", sign);
	}

}
