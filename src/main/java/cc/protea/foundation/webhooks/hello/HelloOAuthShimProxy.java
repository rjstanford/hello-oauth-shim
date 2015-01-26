package cc.protea.foundation.webhooks.hello;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.protea.util.http.Request;
import cc.protea.util.http.Response;

public class HelloOAuthShimProxy {

	static Logger log = LoggerFactory.getLogger(HelloOAuthShimProxy.class);

	public static void proxy(final String url, final HttpServletRequest req, final HttpServletResponse resp) throws IOException {

		if (req.getMethod().equalsIgnoreCase("OPTIONS")) {
			resp.addHeader("Access-Control-Allow-Origin", "*");
			resp.addHeader("Access-Control-Allow-Methods", "OPTIONS, TRACE, GET, HEAD, POST, PUT");
			if (req.getHeader("Access-Control-Request-Headers") != null) {
				for (String value : Collections.list(req.getHeaders("access-control-request-headers"))) {
					resp.addHeader("Access-Control-Request-Headers", value);
				}
			}
			resp.setContentLength(0);
			return;
		}

		Request request = new Request(url);

		Enumeration<String> names = req.getHeaderNames();
		while (names != null && names.hasMoreElements()) {
			String name = names.nextElement();
//			if (req.getMethod().equalsIgnoreCase("DELETE") && )
			Enumeration<String> values = req.getHeaders(name);
			while (values.hasMoreElements()) {
				String value = values.nextElement();
				request.addHeader(name, value);
			}
		}

		Response response = null;

		if (req.getMethod().equals("POST")) {
			request.setBody(HelloOAuthShimProxy.getBody(req.getInputStream()));
			response = request.postResource();
		} else if (req.getMethod().equals("PUT")) {
			request.setBody(HelloOAuthShimProxy.getBody(req.getInputStream()));
			response = request.putResource();
		} else if (req.getMethod().equals("DELETE")) {
			response = request.deleteResource();
		} else if (req.getMethod().equals("HEAD")) {
			response = request.headResource();
		} else if (req.getMethod().equals("GET")) {
			response = request.getResource();
		} else if (req.getMethod().equals("OPTIONS")) {
			response = request.optionsResource();
		} else if (req.getMethod().equals("TRACE")) {
			response = request.traceResource();
		}

		for (String header : response.getHeaders().keySet()) {
			if (header != null) {
				resp.setHeader(header, response.getHeader(header));
			}
		}
		resp.setStatus(response.getResponseCode());
		resp.setContentLength(response.getBody().getBytes().length);
		resp.getOutputStream().write(response.getBody().getBytes());
		resp.getOutputStream().flush();
		resp.getOutputStream().close();
	}

	static String getBody(final InputStream is) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] buf = new byte[4 * 1024]; // 4 KB char buffer
		int len;
		while ((len = is.read(buf, 0, buf.length)) != -1) {
			os.write(buf, 0, len);
		}
		return os.toString();
}

	public static class HelloOAuthShimProxyWorker implements Runnable {

		/**
		 * Once the streams are set up, handle bidirectional communication until its time to close
		 */
		boolean chat() throws IOException {
			byte[] buffer = new byte[1024];
			boolean data = false;
			if (toServer != null) {
				while (fromClient.isReady()) {
					int bytesRead = fromClient.read(buffer);
					if (bytesRead > 0) {
						toServer.write(buffer, 0, bytesRead);
						data = true;
					} else {
						break;
					}
				}
			}
			while(fromServer.available() > 0) {
				int bytesRead = fromServer.read(buffer);
				if (bytesRead > 0) {
					toClient.write(buffer, 0, bytesRead);
					data = true;
				} else {
					break;
				}
			}
			return data;
		}

		void chatLoop() throws IOException {
			boolean data = true;
			boolean warning = false;
			while (data) {
				data = chat();
				if (! data && ! warning) {
					data = true;
					warning = true;
				}
				if (data) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {}
				}
			}
		}

		final String url;
		final HttpServletRequest req;
		final HttpServletResponse resp;
		final ServletInputStream fromClient;
		final ServletOutputStream toClient;
		OutputStream toServer = null;
		InputStream fromServer = null;

		public HelloOAuthShimProxyWorker(final String url, final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
			this.url = url;
			this.req = req;
			this.resp = resp;
			fromClient = req.getInputStream();
			toClient = resp.getOutputStream();
		}

		public void run() {
			Thread.currentThread().setName("Proxy for " + req);
			HttpURLConnection con = null;
			try {
				URL obj = new URL(url);
		        con = (HttpURLConnection) obj.openConnection();
		        con.setRequestMethod(req.getMethod());
		        setHeaders(con);
		        if (req.getMethod().equalsIgnoreCase("POST") || req.getMethod().equalsIgnoreCase("PUT")) {
		        	con.setDoOutput(true);
			        toServer = con.getOutputStream();
		        }
		        try {
		        	fromServer = con.getInputStream();
		        } catch (IOException e) {
		        	fromServer = con.getErrorStream();
		        }
		        chatLoop();
//		        con.get
//				resp.setStatus(con.getResponseCode());
			} catch (IOException e) {
				HelloOAuthShimProxy.log.error("Error proxying request", e);
			} finally {
				if (con != null) {
					con.disconnect();
				}
				close(fromServer);
				close(toServer);
			}
		}

		void setHeaders(final HttpURLConnection serverRequest) {
			Enumeration<String> names = req.getHeaderNames();
			while (names != null && names.hasMoreElements()) {
				String name = names.nextElement();
//				if (req.getMethod().equalsIgnoreCase("DELETE") && )
				Enumeration<String> values = req.getHeaders(name);
				while (values.hasMoreElements()) {
					String value = values.nextElement();
					serverRequest.addRequestProperty(name, value);
				}
			}
		}


		void close(final Closeable c) {
			if (c == null) {
				return;
			}
			try {
				c.close();
			} catch (IOException e) {
				HelloOAuthShimProxy.log.error("Could not clean up properly", e);
			}
		}

	}


	static void error(final String message, final Throwable t, final ServletRequest sreq, final ServletResponse sresp) throws IOException {
		HelloOAuthShimProxy.log.error(message, t);
		HttpServletResponse resp = (HttpServletResponse) sresp;
		HttpServletRequest req = (HttpServletRequest) sreq;
		resp.addHeader("Access-Control-Allow-Origin", "*");
		resp.addHeader("Access-Control-Allow-Methods", "OPTIONS, TRACE, GET, HEAD, POST, PUT");
		if (req.getMethod().equalsIgnoreCase("HEAD")) {
			resp.sendError(502);
		} else {
			resp.setContentType("text/plain");
			resp.sendError(502, "{ 'error' : '" + message.replaceAll("'", "\\\\\'") + "' }");
		}
	}

}
