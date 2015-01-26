# hello-oauth-shim
A Java Servlet version of [MrSwitch/node-oauth-shim](https://github.com/MrSwitch/node-oauth-shim)

```
<dependency>
    <groupId>cc.protea.helloShim</groupId>
    <artifactId>helloShim</artifactId>
    <version>0.1</version>
</dependency>
```

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
