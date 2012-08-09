package org.looplang.ribbon;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import static org.jboss.netty.channel.Channels.pipeline;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class Ribbon {
  private static final int CORES = Runtime.getRuntime().availableProcessors();
  private static final int PORT = 8080;

  public static void main(String[] args) {
    start();
  }

  public static void start() {
    RibbonApp ribbonApp = new RibbonApp();

    ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
        Executors.newFixedThreadPool(CORES),
        Executors.newFixedThreadPool(CORES)
    ));

    bootstrap.setPipelineFactory(newHttpPipelineFactory(ribbonApp));
    bootstrap.bind(new InetSocketAddress(PORT));

    System.out.println("Ribbon server started on port: " + PORT);
  }

  public static ChannelPipelineFactory newHttpPipelineFactory(final RibbonApp ribbonApp) {
    return new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline();

        // Uncomment the following line if you want HTTPS
        //SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
        //engine.setUseClientMode(false);
        //pipeline.addLast("ssl", new SslHandler(engine));

        pipeline.addLast("decoder", new HttpRequestDecoder());
        // Uncomment the following line if you don't want to handle HttpChunks.
        pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        pipeline.addLast("deflater", new HttpContentCompressor());
        pipeline.addLast("handler", new RibbonWebHandler(ribbonApp));

        return pipeline;
      }
    };
  }
}
