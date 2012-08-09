package org.looplang.ribbon;

import com.rethrick.jade.Jade;
import com.rethrick.jade.JadeOptions;
import loop.Loop;
import loop.ast.script.ModuleDecl;
import loop.ast.script.ModuleLoader;
import loop.runtime.Caller;
import loop.runtime.Closure;
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
  public static final String PREFIX = "app/";
  public static final String APP = "app";
  public static final String ROUTE_FUNC = "route";
  private final Jade jade;

  RibbonApp() {
    JadeOptions options = new JadeOptions();
    options.setBaseDir(PREFIX + "views");

    jade = new Jade(options);
  }

  public RibbonResponse dispatch(HttpRequest request) throws Exception {
    // In development mode, we need to clear out our Loop class loader.
    ModuleLoader.reset();

    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
        HttpResponseStatus.OK);

    Object foundRoute;
    Class<?> app = Loop.compile(PREFIX + APP + ".loop");
    Endpoint primary = new Endpoint(APP, app);

    if (primary.methods.containsKey(ROUTE_FUNC)) {
      foundRoute = primary.dispatch(ROUTE_FUNC, request.getUri());
      if (null == foundRoute)
        throw new RuntimeException(
            APP + ".route() did not return a valid endpoint (must be a symbol or a function).");

      if (foundRoute instanceof Closure) {
        Closure closure = (Closure) foundRoute;
        try {
          Object result = Caller.callClosure(closure, closure.target, new Object[]{request});

          return respond(response, result, primary.getAfter());
        } catch (Throwable throwable) {
          throw (Exception) throwable;
        }
      } else {
        Endpoint endpoint = new Endpoint(foundRoute.toString(), Loop.compile(PREFIX + foundRoute + ".loop"));
        Object result = endpoint.dispatch(request.getMethod().getName().toLowerCase(), request);

        return respond(response, result, endpoint.getAfter());
      }
    } else {
      // Otherwise there is no routeing info, just treat the app file as the endpoint.
      Object result = primary.dispatch(request.getMethod().getName().toLowerCase(), request);

      return respond(response, result, primary.getAfter());
    }
  }

  private RibbonResponse respond(HttpResponse response, Object result, Method after)
      throws Exception {
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

    return new RibbonResponse(response, after);
  }

  private static class Endpoint {
    private final Map<String, Method> methods;
    private final Method after;

    private Endpoint(String name, Class<?> clazz) {
      if (!APP.equals(name) && ModuleDecl.DEFAULT.name.equals(clazz.getName()))
        throw new RuntimeException("Endpoint '" + name + "' is missing module declaration.");

      methods = new HashMap<String, Method>();
      for (Method method : clazz.getDeclaredMethods()) {
        if (Modifier.isPublic(method.getModifiers()) && method.getParameterTypes().length == 1)
          methods.put(method.getName(), method);
      }

      after = methods.get("after");
    }

    public Object dispatch(String method, Object request) throws Exception {
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
