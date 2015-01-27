# hello-oauth-shim
A Java Servlet version of [MrSwitch/node-oauth-shim](https://github.com/MrSwitch/node-oauth-shim)

```
<dependency>
    <groupId>cc.protea.helloShim</groupId>
    <artifactId>helloShim</artifactId>
    <version>0.1</version>
</dependency>
```

I've avoided bringing in any significant outside dependencies other than my own tiny HTTP utility library so you shouldn't experience any conflicts.  It does expect to find the javax.servlet and javax.json APIs somewhere in your classpath.

I am using the org.slf4j API for logging. If you don't configure it, no logging will occur and no errors will be thrown. If you'd like some log output, see http://slf4j.org/manual.html for the configuration details.

To configure the servlet, you currently need to pass in a list of public-token/private-secret pairs (whever you want, but before they're used). I'll update it to support a helper class for more dynamic configration, but this actually works well for many projects.

```java
  // Do this for each social network you want to support
  HelloOAuthShimServlet.addSecret(publicKey, privateKey); 
```

To start it simply include it as a servlet, either in your web.xml file:

```xml
    <servlet>
        <servlet-name>Hello</servlet-name>
        <servlet-class>cc.protea.foundation.webhooks.hello.HelloOAuthShimServlet</servlet-class>
    </servlet>

    <servlet-mapping>
        <servlet-name>Hello</servlet-name>
        <url-pattern>/helloshim</url-pattern>
    </servlet-mapping>
```

Or programmatically:

```java
    	ServletHolder servlet = new ServletHolder("Hello", HelloOAuthShimServlet.class);
    	servlet.setAsyncSupported(true);
    	context.addServlet(servlet, "/helloshim/*");
```

Then once its configured, update your [hello.js](https://github.com/MrSwitch/hello.js) initialization:

```js
hello.init({ 
	facebook : '...',
	twitter  : '...',
	...
},{
	redirect_uri : '...',
	oauth_proxy  : 'http://yourserver:yourport/helloshim'
});
```

Those of you looking at the code will notice some odd constructs, particularly around the use of Map<String, String> - I was trying to strike a balance between more standard Java conventions and copying the code from the original project.  As I build up a bigger library of tests a lot of this may be refactored over time (or not - this way it is going to be somewhat easier to bring modifications forward from the JS version and there are many cases where the contracts for inputs and outputs are a little unclear).    