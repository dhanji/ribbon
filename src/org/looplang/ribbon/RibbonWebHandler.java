package org.looplang.ribbon;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;

/**
 * This is a singleton unlike normal netty convention.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
class RibbonWebHandler extends SimpleChannelHandler {
  private final RibbonApp app;

  public RibbonWebHandler(RibbonApp app) {
    this.app = app;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    HttpRequest request = (HttpRequest) e.getMessage();

    final RibbonResponse response = app.dispatch(request);

    boolean keepAlive = isKeepAlive(request);
    if (keepAlive) {
      // Add 'Content-Length' header only for a keep-alive connection.
      response.response.setHeader(HttpHeaders.Names.CONTENT_LENGTH,
          response.response.getContent().readableBytes());
      // Add keep alive header as per:
      // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
      response.response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
    }

    ChannelFuture future = ctx.getChannel().write(response.response);
    if (response.callback != null) {
      future.addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
        response.complete();
        }
      });
    }

    if (!keepAlive)
      future.addListener(ChannelFutureListener.CLOSE);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    Throwable ex = e.getCause();
    StringWriter trace = new StringWriter();
    trace.append("<h2>500: Internal Server Error</h2>\n\n<pre>");

    ex.printStackTrace(new PrintWriter(trace));
    trace.append("</pre>");

    DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    response.setContent(ChannelBuffers.copiedBuffer(trace.toString(), CharsetUtil.UTF_8));
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");

    ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
  }
}
