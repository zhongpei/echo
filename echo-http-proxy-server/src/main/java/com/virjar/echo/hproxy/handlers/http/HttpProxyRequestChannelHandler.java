package com.virjar.echo.hproxy.handlers.http;

import com.google.common.io.BaseEncoding;
import com.virjar.echo.hproxy.EchoHttpProxyServer;
import com.virjar.echo.hproxy.bean.ConnectionState;
import com.virjar.echo.server.common.upstream.ProxyNode;
import com.virjar.echo.hproxy.util.HProxyConstants;
import com.virjar.echo.hproxy.util.ProxyUtils;
import com.virjar.echo.server.common.CommonConstant;
import com.virjar.echo.server.common.NatUpstreamMeta;
import com.virjar.echo.server.common.NettyUtils;
import com.virjar.echo.server.common.auth.AuthenticateDeviceInfo;
import com.virjar.echo.server.common.auth.IAuthenticator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaders.isContentLengthSet;

@Slf4j
public class HttpProxyRequestChannelHandler extends SimpleChannelInboundHandler<Object> {
    private static final int MAX_INITIAL_LINE_LENGTH_DEFAULT = 8192;
    private static final int MAX_HEADER_SIZE_DEFAULT = 8192 * 2;
    private static final int MAX_CHUNK_SIZE_DEFAULT = 8192 * 2;
    private volatile boolean tunneling = false;
    private ConnectionState connectionState = ConnectionState.AWAITING_INITIAL;
    private ChannelHandlerContext ctx;
    private static final HttpResponseStatus CONNECTION_ESTABLISHED = new HttpResponseStatus(
            200, "Connection established");
    /**
     * The current HTTP request that this connection is currently servicing.
     */
    private volatile HttpRequest currentRequest;
    /**
     * Used for case-insensitive comparisons when checking direct proxy request.
     */
    private static final Pattern HTTP_SCHEME = Pattern.compile("^http://.*", Pattern.CASE_INSENSITIVE);

    /**
     * 连接到真实服务器后建立的channelFuture
     */
    private ChannelFuture natMappingChannelFuture;

    private final EchoHttpProxyServer echoHttpProxyServer;

    private AtomicBoolean authenticated = new AtomicBoolean();
    /**
     * 构建完整的FullHttpRequest请求
     */
    private AggregatedFullHttpMessage currentHttpRequest;

    private static final int DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS = 1024;
    private static final int MAX_CONTENT_LENGTH = Integer.MAX_VALUE;
    private static final int maxCumulationBufferComponents = DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS;


    public HttpProxyRequestChannelHandler(EchoHttpProxyServer echoHttpProxyServer) {
        this.echoHttpProxyServer = echoHttpProxyServer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        this.ctx = ctx;
        if (tunneling && msg instanceof ByteBuf) {
            // In tunneling mode, this connection is simply shoveling bytes
            readRaw((ByteBuf) msg);
        } else {
            // If not tunneling, then we are always dealing with HttpObjects.
            readHTTP((HttpObject) msg);
        }
    }

    private void readHTTP(HttpObject httpObject) {
        ConnectionState nextState = connectionState;
        switch (connectionState) {
            case AWAITING_INITIAL:
                if (httpObject instanceof HttpMessage) {
                    nextState = readHTTPInitial((HttpRequest) httpObject);
                } else {
                    // Similar to the AWAITING_PROXY_AUTHENTICATION case below, we may enter an AWAITING_INITIAL
                    // state if the proxy responded to an earlier request with a 502 or 504 response, or a short-circuit
                    // response from a filter. The client may have sent some chunked HttpContent associated with the request
                    // after the short-circuit response was sent. We can safely drop them.
                    log.debug("Dropping message because HTTP object was not an HttpMessage. HTTP object may be orphaned content from a short-circuited response. Message: {}", httpObject);
                }
                break;
            case AWAITING_CHUNK:
                HttpContent chunk = (HttpContent) httpObject;
//                readHTTPChunk(chunk);
                mergeFullHttpRequest(chunk);
                nextState = ProxyUtils.isLastChunk(chunk) ? ConnectionState.AWAITING_INITIAL
                        : ConnectionState.AWAITING_CHUNK;
                break;
            case AWAITING_PROXY_AUTHENTICATION:
                if (httpObject instanceof HttpRequest) {
                    // Once we get an HttpRequest, try to process it as usual
                    nextState = readHTTPInitial((HttpRequest) httpObject);
                } else {
                    // Anything that's not an HttpRequest that came in while
                    // we're pending authentication gets dropped on the floor. This
                    // can happen if the connected host already sent us some chunks
                    // (e.g. from a POST) after an initial request that turned out
                    // to require authentication.
                }
                break;
            case CONNECTING:
                log.warn("Attempted to read from connection that's in the process of connecting.  This shouldn't happen.");
                break;
            case NEGOTIATING_CONNECT:
                log.debug("Attempted to read from connection that's in the process of negotiating an HTTP CONNECT.  This is probably the LastHttpContent of a chunked CONNECT.");
                break;
            case DISCONNECT_REQUESTED:
            case DISCONNECTED:
                log.info("Ignoring message since the connection is closed or about to close");
                break;
        }
        connectionState = nextState;
    }

    private void readHTTPChunk(HttpContent chunk) {
        Channel natMappingChannel = ctx.channel().attr(CommonConstant.NEXT_CHANNEL).get();
        if (natMappingChannel != null) {
            natMappingChannel.write(chunk);
            return;
        }

        if (natMappingChannelFuture != null) {
            natMappingChannelFuture.addListener(future -> {
                if (future.isSuccess()) {
                    readHTTPChunk(chunk);
                } else {
                    ctx.close();
                }
            });
            return;
        }
        log.error("no natMapping channel bound!!");
        ctx.close();
    }

    private void fillAuthAccount(AuthenticateDeviceInfo authenticateDeviceInfo, HttpRequest request) {
        //获取代理验证
        List<String> values = request.headers().getAll(
                HttpHeaders.Names.PROXY_AUTHORIZATION);
        if (values.isEmpty()) {
            return;
        }
        String fullValue = values.iterator().next();
        String value = StringUtils.substringAfter(fullValue, "Basic ").trim();

        byte[] decodedValue = BaseEncoding.base64().decode(value);
        String decodedString = new String(decodedValue, StandardCharsets.UTF_8);

        String userName = StringUtils.substringBefore(decodedString, ":");
        String password = StringUtils.substringAfter(decodedString, ":");
        authenticateDeviceInfo.setUserName(userName);
        authenticateDeviceInfo.setPassword(password);

    }

    private boolean authenticationRequired(HttpRequest request, ProxyNode proxyNode) {
        if (authenticated.get()) {
            return false;
        }

        IAuthenticator authenticator = echoHttpProxyServer.getAuthConfigManager().getAuthenticator();

        //未设置代理验证
        if (authenticator == null) {
            authenticated.set(true);
            return false;
        }
        String remoteHost = ((InetSocketAddress) ctx.channel().remoteAddress()).getHostName();

        NatUpstreamMeta natUpstreamMeta = proxyNode.getNatUpstreamMeta();
        AuthenticateDeviceInfo authenticateDeviceInfo =
                AuthenticateDeviceInfo.create(natUpstreamMeta, remoteHost, proxyNode.getClientId());

        fillAuthAccount(authenticateDeviceInfo, request);

        if (authenticator.authenticate(authenticateDeviceInfo)) {
            removeProxyAuthHead(request);
            return false;
        }
        if (authenticateDeviceInfo.getRateLimited()) {
            // 被限流了，写入503，然后关闭连接
            String body = "代理连接被限流，当前qps阈值:" + authenticateDeviceInfo.getGussAccessAccount().nowRate();
            FullHttpResponse response = ProxyUtils.createFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY, body);

            HttpHeaders.setKeepAlive(request, false);
            if (ProxyUtils.isHEAD(request)) {
                // don't allow any body content in response to a HEAD request
                response.content().clear();
            }
            respondWithShortCircuitResponse(response);
            return false;
        }

        writeAuthenticationRequired();
        return true;
    }

    private void writeAuthenticationRequired() {
        String body = "<!DOCTYPE HTML \"-//IETF//DTD HTML 2.0//EN\">\n"
                + "<html><head>\n"
                + "<title>407 Proxy Authentication Required</title>\n"
                + "</head><body>\n"
                + "<h1>Proxy Authentication Required</h1>\n"
                + "<p>This server could not verify that you\n"
                + "are authorized to access the document\n"
                + "requested.  Either you supplied the wrong\n"
                + "credentials (e.g., bad password), or your\n"
                + "browser doesn't understand how to supply\n"
                + "the credentials required.</p>\n" + "</body></html>\n";
        FullHttpResponse response = ProxyUtils.createFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED, body);
        HttpHeaders.setDate(response, new Date());
        response.headers().set("Proxy-Authenticate",
                "Basic realm=\"Basic\"");
        respondWithShortCircuitResponse(response);
    }

    private void removeProxyAuthHead(HttpRequest request) {
        log.debug("Got proxy authorization!");
        // We need to remove the header before sending the request on.
        String authentication = request.headers().get(
                HttpHeaders.Names.PROXY_AUTHORIZATION);
        log.debug(authentication);
        request.headers().remove(HttpHeaders.Names.PROXY_AUTHORIZATION);
        authenticated.set(true);
    }

    private ConnectionState readHTTPInitial(HttpRequest httpRequest) {
        log.info("Received raw request: {}", httpRequest);
        if (httpRequest.getDecoderResult().isFailure()) {
            log.warn("Could not parse request from client. Decoder result: {}", httpRequest.getDecoderResult().toString());

            FullHttpResponse response = ProxyUtils.createFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.BAD_REQUEST,
                    "Unable to parse HTTP request");
            HttpHeaders.setKeepAlive(response, false);

            respondWithShortCircuitResponse(response);

            return ConnectionState.DISCONNECT_REQUESTED;
        }
        ProxyNode proxyNode = ctx.channel().parent().attr(HProxyConstants.BIND_PROXY_NODE).get();

        boolean authenticationRequired = authenticationRequired(httpRequest, proxyNode);
        if (authenticationRequired) {
            log.debug("Not authenticated!!");
            return ConnectionState.AWAITING_PROXY_AUTHENTICATION;
        }

        this.currentRequest = copy(httpRequest);
        if (isRequestToOriginServer(httpRequest)) {
            // 纯http请求，url代理需要指定到全称路径： GET http://www.baidu.com?
            // 否则认为是错误请求，此时不满足代理协议规定；如果依然给他提供代理，那么可能导致理解误差
            // https请求直接是connect+密文隧道，不需要
            boolean keepAlive = writeBadRequest(httpRequest);
            if (keepAlive) {
                return ConnectionState.AWAITING_INITIAL;
            } else {
                return ConnectionState.DISCONNECT_REQUESTED;
            }
        }
        // Identify our server and chained proxy
        // 解析我们需要代理到哪里去
        String serverHostAndPort = identifyHostAndPort(httpRequest);
        // 从UserAgent中获取echoTrace
        String echoTrace = ProxyUtils.getEchoTraceFromUserAgent(httpRequest);
        log.info("Ensuring that hostAndPort are available in {}",
                httpRequest.getUri());
        if (serverHostAndPort == null || StringUtils.isBlank(serverHostAndPort)) {
            log.warn("No host and port found in {}", httpRequest.getUri());
            boolean keepAlive = writeBadGateway(httpRequest);
            if (keepAlive) {
                return ConnectionState.AWAITING_INITIAL;
            } else {
                return ConnectionState.DISCONNECT_REQUESTED;
            }
        }

        // 创建到 NatMapping的连接
        //TODO http代理，需要后端连接复用

        // 暂停从client端读取数据，等待连接创建完成
        ctx.channel().config().setAutoRead(false);
        // 判断是否为https连接
        boolean isHttps = ProxyUtils.isCONNECT(httpRequest);
        natMappingChannelFuture = echoHttpProxyServer.connectToNatMapping(proxyNode, serverHostAndPort, echoTrace);
        // 建立到真实服务器的请求
        natMappingChannelFuture.addListener((ChannelFutureListener) channelFuture -> {
            if (!channelFuture.isSuccess()) {
                log.warn("connect to proxy server failed:{} ", serverHostAndPort, natMappingChannelFuture.cause());
                writeBadGateway(httpRequest);
                return;
            }

            Channel natMappingChannel = channelFuture.channel();
            natMappingChannel.attr(HProxyConstants.ECHO_TRACE_ID).set(echoTrace);

            NettyUtils.makePair(natMappingChannel, ctx.channel());
            NettyUtils.loveOther(natMappingChannel, ctx.channel());

            //恢复proxy request client数据读取
            ctx.channel().config().setAutoRead(true);
            Channel echoUpstreamChannel = natMappingChannelFuture.channel();
            if (isHttps) { // 确认是否为https连接
                log.info("Responding with CONNECT successful");
                HttpResponse response = ProxyUtils.createFullHttpResponse(HttpVersion.HTTP_1_1,
                        CONNECTION_ESTABLISHED);
                response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
                ProxyUtils.addVia(response, "echo-proxy");
                ctx.channel().writeAndFlush(response)
                        .addListener(future -> startTuning(echoUpstreamChannel));
            } else {
                startHttpProxy(echoUpstreamChannel);
            }

        });

        if (!isHttps && currentRequest != null) {  // HTTP连接的请求，构建FullHttpRequest 头部
            HttpRequest header = currentRequest;
            currentHttpRequest = new AggregatedFullHttpRequest(
                    header, ctx.alloc().compositeBuffer(maxCumulationBufferComponents), null);
            log.debug("construct fullHttpRequst init-part");
        }

        // Figure out our next state
        if (ProxyUtils.isCONNECT(httpRequest)) {
            return ConnectionState.NEGOTIATING_CONNECT;
        } else if (ProxyUtils.isChunked(httpRequest)) {
            return ConnectionState.AWAITING_CHUNK;
        } else {
            return ConnectionState.AWAITING_INITIAL;
        }
    }

    private void startTuning(Channel echoUpstreamChannel) {
        tunneling = true;
        ctx.pipeline().remove("encoder");
        ctx.pipeline().remove("decoder");
        echoUpstreamChannel.pipeline()
                .addAfter("idle", "http-replay",
                        new HttpEchoUpstreamChannelHandler()
                );
    }

    private void startHttpProxy(Channel echoUpstreamChannel) {
        echoUpstreamChannel.pipeline().addFirst("encoder", new HttpRequestEncoder());
        echoUpstreamChannel.pipeline().addFirst("decoder", new HttpResponseDecoder(MAX_INITIAL_LINE_LENGTH_DEFAULT,
                MAX_HEADER_SIZE_DEFAULT,
                MAX_CHUNK_SIZE_DEFAULT));
        echoUpstreamChannel.pipeline()
                .addAfter("idle", "http-replay",
                        new HttpEchoUpstreamChannelHandler()
                );
    }

    /**
     * 拼接HttpRequest 和多个 HttpContent
     *
     * @param msg
     */
    private void mergeFullHttpRequest(HttpObject msg) {

        if (currentHttpRequest == null) {
            return;
        }
        // Merge the received chunk into the content of the current message.
        HttpContent chunk = (HttpContent) msg;

        CompositeByteBuf content = (CompositeByteBuf) currentHttpRequest.content();
        if (content.readableBytes() > MAX_CONTENT_LENGTH - chunk.content().readableBytes()) {
            // release current message to prevent leaks
            currentHttpRequest.release();
            currentHttpRequest = null;

            throw new TooLongFrameException(
                    "HTTP content length exceeded " + MAX_CONTENT_LENGTH +
                            " bytes.");
        }

        // Append the content of the chunk
        if (chunk.content().isReadable()) {
            content.addComponent(true, chunk.content().retain());
        }

        final boolean last;
        if (!chunk.getDecoderResult().isSuccess()) {
            currentHttpRequest.setDecoderResult(
                    DecoderResult.failure(chunk.getDecoderResult().cause()));
            last = true;
        } else {
            last = chunk instanceof LastHttpContent;
        }

        if (last) {
            // Merge trailing headers into the message.
            if (chunk instanceof LastHttpContent) {
                LastHttpContent trailer = (LastHttpContent) chunk;
                currentHttpRequest.setTrailingHeaders(trailer.trailingHeaders());
            } else {
                currentHttpRequest.setTrailingHeaders(new DefaultHttpHeaders());
            }

            if (!isContentLengthSet(currentHttpRequest)) {
                currentHttpRequest.headers().set(
                        HttpHeaders.Names.CONTENT_LENGTH,
                        String.valueOf(content.readableBytes()));
            }
            // Set our currentMessage member variable to null in case adding to out will cause re-entry.
            Channel natMappingChannel = ctx.channel().attr(CommonConstant.NEXT_CHANNEL).get();
            if (natMappingChannel != null) {
                try {
                    natMappingChannel.writeAndFlush(currentHttpRequest);
                    // 拼接完成，将currentMessage置空
                    this.currentHttpRequest = null;
                } finally {
                    log.debug("http transfer Wrote: {}", msg);

                }
                return;
            }
            if (natMappingChannelFuture != null) {
                natMappingChannelFuture.addListener(future -> {
                    if (future.isSuccess()) {
                        natMappingChannelFuture.channel().writeAndFlush(currentHttpRequest);
                        this.currentHttpRequest = null;
                    } else {
                        ctx.close();
                    }
                });
            }

        }

    }


    /**
     * Identify the host and port for a request.
     *
     * @param httpRequest
     * @return
     */
    private String identifyHostAndPort(HttpRequest httpRequest) {
        String hostAndPort = ProxyUtils.parseHostAndPort(httpRequest);
        if (StringUtils.isBlank(hostAndPort)) {
            List<String> hosts = httpRequest.headers().getAll(
                    HttpHeaders.Names.HOST);
            if (hosts != null && !hosts.isEmpty()) {
                hostAndPort = hosts.get(0);
            }
        }

        if (StringUtils.isBlank(hostAndPort)) {
            return hostAndPort;
        }

        // add add default port config
        // set port=80 for http ;set port=443 for https
        if (hostAndPort.contains(":")) {
            return hostAndPort;
        }

        String uri = httpRequest.getUri();
        if (StringUtils.startsWith(uri, "https:")) {
            return hostAndPort + ":443";
        } else {
            return hostAndPort + ":80";
        }
    }

    /**
     * Tells the client that something went wrong trying to proxy its request. If the Bad Gateway is a response to
     * an HTTP HEAD request, the response will contain no body, but the Content-Length header will be set to the
     * value it would have been if this 502 Bad Gateway were in response to a GET.
     *
     * @param httpRequest the HttpRequest that is resulting in the Bad Gateway response
     * @return true if the connection will be kept open, or false if it will be disconnected
     */
    private boolean writeBadGateway(HttpRequest httpRequest) {
        String body = "Bad Gateway: " + httpRequest.getUri();
        FullHttpResponse response = ProxyUtils.createFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY, body);

        if (ProxyUtils.isHEAD(httpRequest)) {
            // don't allow any body content in response to a HEAD request
            response.content().clear();
        }

        return respondWithShortCircuitResponse(response);
    }

    /**
     * Tells the client that the request was malformed or erroneous. If the Bad Request is a response to
     * an HTTP HEAD request, the response will contain no body, but the Content-Length header will be set to the
     * value it would have been if this Bad Request were in response to a GET.
     *
     * @return true if the connection will be kept open, or false if it will be disconnected
     */
    private boolean writeBadRequest(HttpRequest httpRequest) {
        String body = "Bad Request to URI: " + httpRequest.getUri();
        FullHttpResponse response = ProxyUtils.createFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST, body);

        if (ProxyUtils.isHEAD(httpRequest)) {
            // don't allow any body content in response to a HEAD request
            response.content().clear();
        }

        return respondWithShortCircuitResponse(response);
    }

    private boolean isRequestToOriginServer(HttpRequest httpRequest) {
        if (httpRequest.getMethod() == HttpMethod.CONNECT) {
            return false;
        }

        // direct requests to the proxy have the path only without a scheme
        String uri = httpRequest.getUri();
        return !HTTP_SCHEME.matcher(uri).matches();
    }

    private HttpRequest copy(HttpRequest original) {
        if (original instanceof FullHttpRequest) {
            return ((FullHttpRequest) original).copy();
        } else {
            HttpRequest request = new DefaultHttpRequest(original.getProtocolVersion(),
                    original.getMethod(), original.getUri());
            request.headers().set(original.headers());
            return request;
        }
    }

    private boolean respondWithShortCircuitResponse(HttpResponse httpResponse) {
        // we are sending a response to the client, so we are done handling this request
        this.currentRequest = null;

        // allow short-circuit messages to close the connection. normally the Connection header would be stripped when modifying
        // the message for proxying, so save the keep-alive status before the modifications are made.
        boolean isKeepAlive = HttpHeaders.isKeepAlive(httpResponse);

        // restore the keep alive status, if it was overwritten when modifying headers for proxying
        HttpHeaders.setKeepAlive(httpResponse, isKeepAlive);

        ChannelFuture channelFuture = ctx.channel().writeAndFlush(httpResponse);

        if (ProxyUtils.isLastChunk(httpResponse)) {
            writeEmptyBuffer();
        }

        if (!HttpHeaders.isKeepAlive(httpResponse)) {
            channelFuture.addListener(future -> ctx.close());
            return false;
        }
        return true;
    }

    private void writeEmptyBuffer() {
        ctx.channel().write(Unpooled.EMPTY_BUFFER);
        //write(Unpooled.EMPTY_BUFFER);
    }

    private void readRaw(ByteBuf msg) {
        if (msg == null) {
            return;
        }
        Channel natMappingChannel = ctx.channel().attr(CommonConstant.NEXT_CHANNEL).get();
        if (natMappingChannel == null) {
            log.error("not upstream channel bind for ByteBuf message,drop connection");
            ctx.close();
            return;
        }

        msg.retain();
        natMappingChannel.writeAndFlush(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
    }


    private abstract static class AggregatedFullHttpMessage implements ByteBufHolder, FullHttpMessage {
        final HttpMessage message;
        private final ByteBuf content;
        private HttpHeaders trailingHeaders;

        AggregatedFullHttpMessage(HttpMessage message, ByteBuf content, HttpHeaders trailingHeaders) {
            this.message = message;
            this.content = content;
            this.trailingHeaders = trailingHeaders;
        }

        @Override
        public HttpHeaders trailingHeaders() {
            HttpHeaders trailingHeaders = this.trailingHeaders;
            if (trailingHeaders == null) {
                return HttpHeaders.EMPTY_HEADERS;
            } else {
                return trailingHeaders;
            }
        }

        void setTrailingHeaders(HttpHeaders trailingHeaders) {
            this.trailingHeaders = trailingHeaders;
        }

        @Override
        public HttpVersion getProtocolVersion() {
            return message.getProtocolVersion();
        }

        @Override
        public FullHttpMessage setProtocolVersion(HttpVersion version) {
            message.setProtocolVersion(version);
            return this;
        }

        @Override
        public HttpHeaders headers() {
            return message.headers();
        }

        @Override
        public DecoderResult getDecoderResult() {
            return message.getDecoderResult();
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
            message.setDecoderResult(result);
        }

        @Override
        public ByteBuf content() {
            return content;
        }

        @Override
        public int refCnt() {
            return content.refCnt();
        }

        @Override
        public FullHttpMessage retain() {
            content.retain();
            return this;
        }

        @Override
        public FullHttpMessage retain(int increment) {
            content.retain(increment);
            return this;
        }

        @Override
        public boolean release() {
            return content.release();
        }

        @Override
        public boolean release(int decrement) {
            return content.release(decrement);
        }

        @Override
        public abstract FullHttpMessage copy();

        @Override
        public abstract FullHttpMessage duplicate();
    }

    private static final class AggregatedFullHttpRequest extends AggregatedFullHttpMessage implements FullHttpRequest {

        AggregatedFullHttpRequest(HttpRequest request, ByteBuf content, HttpHeaders trailingHeaders) {
            super(request, content, trailingHeaders);
        }

        @Override
        public FullHttpRequest copy() {
            DefaultFullHttpRequest copy = new DefaultFullHttpRequest(
                    getProtocolVersion(), getMethod(), getUri(), content().copy());
            copy.headers().set(headers());
            copy.trailingHeaders().set(trailingHeaders());
            return copy;
        }

        @Override
        public FullHttpRequest duplicate() {
            DefaultFullHttpRequest duplicate = new DefaultFullHttpRequest(
                    getProtocolVersion(), getMethod(), getUri(), content().duplicate());
            duplicate.headers().set(headers());
            duplicate.trailingHeaders().set(trailingHeaders());
            return duplicate;
        }

        @Override
        public FullHttpRequest retain(int increment) {
            super.retain(increment);
            return this;
        }

        @Override
        public FullHttpRequest retain() {
            super.retain();
            return this;
        }

        @Override
        public FullHttpRequest setMethod(HttpMethod method) {
            ((HttpRequest) message).setMethod(method);
            return this;
        }

        @Override
        public FullHttpRequest setUri(String uri) {
            ((HttpRequest) message).setUri(uri);
            return this;
        }

        @Override
        public HttpMethod getMethod() {
            return ((HttpRequest) message).getMethod();
        }

        @Override
        public String getUri() {
            return ((HttpRequest) message).getUri();
        }

        @Override
        public FullHttpRequest setProtocolVersion(HttpVersion version) {
            super.setProtocolVersion(version);
            return this;
        }

        @Override
        public String toString() {
//            return HttpMessageUtil.appendFullRequest(new StringBuilder(256), this).toString();
            return null;
        }
    }


}
