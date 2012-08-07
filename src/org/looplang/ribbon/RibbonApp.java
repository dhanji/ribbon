package org.looplang.ribbon;

import com.rethrick.jade.Jade;
import com.rethrick.jade.JadeOptions;
import loop.Loop;
import loop.ast.script.ModuleDecl;
import loop.ast.script.ModuleLoader;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
class RibbonApp {
  private final Jade jade;

  RibbonApp() {
    JadeOptions options = new JadeOptions();
    options.setBaseDir("views");

    jade = new Jade(options);
  }

  public RibbonResponse dispatch(HttpRequest request) throws Exception {
    // In development mode, we need to clear out our Loop class loader.
    ModuleLoader.reset();

    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
        HttpResponseStatus.OK);

    Class<?> app = Loop.compile("app.loop");

    Object foundRoute;
    Method route = app.getDeclaredMethod("route", Object.class);

    foundRoute = route.invoke(null, request.getUri());
    if (null == foundRoute)
      throw new RuntimeException("app.route() did not return a valid endpoint (must be a symbol).");

    Endpoint endpoint = new Endpoint(foundRoute.toString(), Loop.compile(foundRoute + ".loop"));
    Object result = endpoint.dispatch(request.getMethod().getName().toLowerCase(), request);

    if (result instanceof String) {
      response.setContent(ChannelBuffers.copiedBuffer(result.toString(), CharsetUtil.UTF_8));
      response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
    } else if (result instanceof Map) {
      @SuppressWarnings("unchecked") // Ensured by API contract.
          Map<String, Object> map = (Map<String, Object>) result;
      Object view = map.get("view");
      Object body = map.get("body");
      Object status = map.get("status");
      Object type = map.get("type");
      Object redirect = map.get("redirect");

      if (redirect != null) {
        response.setStatus(HttpResponseStatus.FOUND);
        response.setHeader(HttpHeaders.Names.LOCATION, redirect.toString());
      } else {
        if (type == null)
          type = "text/html; charset=UTF-8";

        if (status != null)
          response.setStatus(new HttpResponseStatus((Integer) status,
              (status.equals(200)) ? "" : body.toString()));

        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, type);

        if (view != null)
          body = jade.execute(view.toString(), map);

        if (body == null)
          throw new RuntimeException(
              "Illegal Ribbon response. You must specify either 'body' or 'view'.");

        response.setContent(ChannelBuffers.copiedBuffer(body.toString(), CharsetUtil.UTF_8));
      }
    } else if (result == null) {
      response.setStatus(HttpResponseStatus.NOT_FOUND);
      response.setContent(ChannelBuffers.copiedBuffer("404: Not found", CharsetUtil.UTF_8));
      response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
    } else if (result instanceof HttpResponse)
      response = (HttpResponse) result;

    return new RibbonResponse(response, endpoint.getAfter());
  }

  private static class Endpoint {
    private final Map<String, Method> methods;
    private final Method after;

    private Endpoint(String name, Class<?> clazz) {
      if (ModuleDecl.DEFAULT.name.equals(clazz.getName()))
        throw new RuntimeException("Endpoint '" + name + "' is missing module declaration.");

      methods = new HashMap<String, Method>();
      for (Method method : clazz.getDeclaredMethods()) {
        if (Modifier.isPublic(method.getModifiers()) && method.getParameterTypes().length == 1)
          methods.put(method.getName(), method);
      }

      after = methods.get("after");
    }

    public Object dispatch(String method, HttpRequest request) throws Exception {
      Method found = methods.get(method);
      if (found == null)
        return null;

      return found.invoke(null, request);
    }

    public Method getAfter() {
      return after;
    }
  }
}
