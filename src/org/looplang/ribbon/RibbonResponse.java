package org.looplang.ribbon;

import org.jboss.netty.handler.codec.http.HttpResponse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
class RibbonResponse {
  final HttpResponse response;
  final Method callback;

  RibbonResponse(HttpResponse response, Method callback) {
    this.response = response;
    this.callback = callback;
  }

  public void complete() throws InvocationTargetException, IllegalAccessException {
    callback.invoke(null, response);
  }
}
