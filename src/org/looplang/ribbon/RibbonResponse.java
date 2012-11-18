package org.looplang.ribbon;

import org.jboss.netty.handler.codec.http.HttpResponse;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
class RibbonResponse {
  final HttpResponse response;
  final Method callback;
  final File staticFile;

  RibbonResponse(HttpResponse response, Method callback) {
    this(response, callback, null);
  }

  RibbonResponse(HttpResponse response, Method callback, File staticFile) {
    this.response = response;
    this.callback = callback;
    this.staticFile = staticFile;
  }

  public void complete() throws InvocationTargetException, IllegalAccessException {
    callback.invoke(null, response);
  }
}
