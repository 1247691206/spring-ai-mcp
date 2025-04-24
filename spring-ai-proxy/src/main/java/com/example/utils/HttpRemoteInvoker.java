package com.example.utils;

import com.example.bean.MCPToolMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.HeaderGroup;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.ProxySelector;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用的http client 作处理
 */

public class HttpRemoteInvoker {

    private static final Logger logger = LoggerFactory.getLogger(HttpRemoteInvoker.class);

    private static final HeaderGroup ignoreHeaders;
    private ObjectMapper objectMapper = new ObjectMapper();

    static {
        ignoreHeaders = new HeaderGroup();
        String[] headers = new String[]{"Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE", "Trailers", "Transfer-Encoding", "Upgrade", "X-Forwarded-Proto", ""};
        for (String header : headers) {
            ignoreHeaders.addHeader(new BasicHeader(header, null));
        }
    }

    //它是线程安全的
    private CloseableHttpClient httpsClient;
    private CloseableHttpClient httpClient;


    public HttpRemoteInvoker() {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        SystemDefaultRoutePlanner routePlanner = new SystemDefaultRoutePlanner(ProxySelector.getDefault());
        // 由于.setSSLSocketFactory() 会被setConnectionManager()覆盖，所以将SSLSocketFactory set到connectionManager 中配置。
        //注册请求方式，根据URL自动请求  http->PlainConnectionSocketFactory  https->SSLConnectionSocketFactory
        Registry<ConnectionSocketFactory> r = RegistryBuilder.<ConnectionSocketFactory>create().register("http", PlainConnectionSocketFactory.getSocketFactory()).register("https", createTrustAllSSLSocketFactory()).build();

        // 网关http转发策略定为单次单连接，一次请求使用完即关闭（即no keep alive），所以使用NoConnectionReuseStrategy
        httpsClient = httpClientBuilder.setRoutePlanner(routePlanner).setConnectionManager(connectionManager(r)).setConnectionReuseStrategy(new NoConnectionReuseStrategy()).build();
        // 如果想用代理 setRoutePlanner(new DefaultProxyRoutePlanner(new HttpHost("host", 8080)))
        httpClient = httpClientBuilder.setRoutePlanner(routePlanner).setConnectionManager(connectionManager(null)).setConnectionReuseStrategy(new NoConnectionReuseStrategy()).build();
    }

    protected HttpClientConnectionManager connectionManager(Registry<ConnectionSocketFactory> r) {
        PoolingHttpClientConnectionManager connectionManager;
        if (r != null) {
            connectionManager = new PoolingHttpClientConnectionManager(r);
        } else {
            connectionManager = new PoolingHttpClientConnectionManager();
        }
        connectionManager.setMaxTotal(500);//连接池的最大连接数
        connectionManager.setDefaultMaxPerRoute(20); // 每个路由最多支持20个连接
        return connectionManager;
    }

    public static SSLConnectionSocketFactory createTrustAllSSLSocketFactory() {
        SSLContext sslContext;
        try {
            //信任所有
            sslContext = new SSLContextBuilder().loadTrustMaterial(KeyStore.getInstance(KeyStore.getDefaultType()), (chain, authType) -> true).build();
        } catch (Exception e) {
            throw new RuntimeException("createTrustAllSSLSocketFactory failed.", e);
        }
        //NoopHostnameVerifier.INSTANCE 用于设置可以接受不同主机的证书，用于可能有重定向的场景
        return new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
    }

    public String invoke(MCPToolMetadata metaData, Map<String, String> requestBaseData) throws Exception {

        String baseUrl = metaData.getUrl();
        if (baseUrl.length() > 7 && baseUrl.substring(0, 4).equalsIgnoreCase("http")) {
            String serverAddress = baseUrl.substring(0, 5).equalsIgnoreCase("https") ? baseUrl.substring(8) : baseUrl.substring(7);
            logger.info("HTTP-RPC. server address is [{}]. http remoteBaseUrl [{}] ]", serverAddress, baseUrl);
        } else {
            logger.warn("HTTP-RPC. cannot find server address. http remoteBaseUrl [{}] ", baseUrl);
        }
        String url = baseUrl;
        long startTime = System.currentTimeMillis();
        try {
            return doExecute(metaData, url, requestBaseData);
        } catch (Throwable e) {
            throw e;
        } finally {
            long endTime = System.currentTimeMillis();
        }
    }

    private String doExecute(MCPToolMetadata serviceMetaData, String targetUrl, Map<String, String> requestBaseData) {
        //返回HTTP请求方法
        String method = serviceMetaData.getMethod();
        CloseableHttpClient proxyClient = httpClient;
        String proxyRequestUri = rewriteUrlFromRequest(targetUrl, serviceMetaData, requestBaseData);
        logger.info("call proxyRequestUri: {}", proxyRequestUri);
        RequestBuilder requestBuilder = RequestBuilder.create(method);
        HttpUriRequest httpRequest = null;
        CloseableHttpResponse response = null;
        try {
            requestBuilder.setUri(proxyRequestUri);
            copyRequestHeader(requestBuilder, serviceMetaData);
            addEntity(requestBuilder, serviceMetaData, requestBaseData);
            if (targetUrl.startsWith("https")) {
                proxyClient = httpsClient;
            }
            // 单位ms
            int timeout = serviceMetaData.getTimeout();
            requestBuilder.setConfig(buildRequestConfig(timeout));

            HttpHost targetHost = HttpHost.create(getDomain(proxyRequestUri));
            // http网关必须重写header中的Host， 因为用户请求携带的host是网关自身
            requestBuilder.setHeader("Host", targetHost.toHostString());
            try {
                httpRequest = requestBuilder.build();
                response = proxyClient.execute(targetHost, httpRequest);
            } catch (IOException e) {
                throw e;
            }
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println(String.format("[%s],%s", statusCode, targetUrl));
            return EntityUtils.toString(response.getEntity());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            HttpClientUtils.closeQuietly(response);

            if (httpRequest != null) {
                httpRequest.abort();
            }
        }

    }


    private RequestConfig buildRequestConfig(int timeout) {
        RequestConfig.Builder builder = RequestConfig.custom().setRedirectsEnabled(true).setCookieSpec(CookieSpecs.IGNORE_COOKIES) // we handle them in the servlet instead
                .setConnectTimeout(timeout).setSocketTimeout(timeout);
        return builder.build();
    }

    private void addEntity(RequestBuilder requestBuilder, MCPToolMetadata mcpToolMetadata, Map<String, String> requestBaseData) throws Exception {

        Map<String, String> requestBodyMap = new HashMap<>();
        for (Map.Entry<String, String> entry : mcpToolMetadata.getRequestBodyResolver().entrySet()) {
            String paramName = entry.getValue();
            if (paramName.startsWith("body")) {
                paramName = paramName.substring("body".length());
            }
            String paramValue = requestBaseData.get(entry.getKey().substring("map.".length()));
            if (paramValue == null) {
                continue;
            }
            requestBodyMap.put(paramName, paramValue);
        }

        String contentTypeStr = mcpToolMetadata.getHeaders() == null ? null : mcpToolMetadata.getHeaders().get("Content-Type");
        contentTypeStr = contentTypeStr == null ? MimeTypeUtils.APPLICATION_JSON_VALUE : contentTypeStr;
        if (MimeTypeUtils.parseMimeType(contentTypeStr).isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)) {
            //如果是application/x-www-form-urlencoded
            //httpclient 代理请求
            // 如果连接超时 有时需要补足host header  因为多个域名会路由到同一台机器，默认不会传递。导致路径错误
            List<NameValuePair> params = new ArrayList<>();
            requestBodyMap.forEach((key, value) -> {
                NameValuePair pair = new BasicNameValuePair(key, value);
                params.add(pair);
            });
            HttpEntity reqEntity = new UrlEncodedFormEntity(params, StandardCharsets.UTF_8);
            requestBuilder.setEntity(reqEntity);
        } else {
            String body = objectMapper.writeValueAsString(requestBodyMap);
            HttpEntity reqEntity = new StringEntity(body, StandardCharsets.UTF_8);
            requestBuilder.setEntity(reqEntity);
        }
    }

    private void copyRequestHeader(RequestBuilder proxyRequest, MCPToolMetadata serviceMetaData) {
        for (Map.Entry<String, String> entry : serviceMetaData.getHeaders().entrySet()) {
            proxyRequest.addHeader(entry.getKey(), entry.getValue());
        }
    }

    //根据配置的信息和过来的请求参数，还原成http 请求
    private String rewriteUrlFromRequest(String targetUrl, MCPToolMetadata serviceMetaData, Map<String, String> requestBaseData) {

        Map<String, String> paramResolver = serviceMetaData.getRequestParamResolver();
        for (Map.Entry<String, String> entry : paramResolver.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            StringBuilder paramBuilder = new StringBuilder("");
            // uri param 参数
            //param value
            String paramValue = requestBaseData.get(key.substring("map.".length()));
            if (paramValue == null) {
                continue;
            }
            if (value.startsWith("query")) {
                //param key name
                String paramName = value.substring("query.".length());
                paramBuilder.append(paramName).append("=").append(paramValue).append("&");
            }
            //path param参数
            if (value.startsWith("path")) {
                //param key name
                String paramName = value.substring("path.".length());
                paramName = "{" + paramName + "}";
                targetUrl = targetUrl.replace(paramName, paramValue);
            }
            String fullParam = paramBuilder.toString();
            if (fullParam.length() > 1) {
                fullParam = fullParam.substring(0, fullParam.length() - 1);
                targetUrl = targetUrl + "?" + fullParam;
            }
        }
        return targetUrl;
    }


    public static String getDomain(String url) {
        String domain = url;
        final int schemeIdx = url.indexOf("://");
        String scheme;
        if (schemeIdx > 0) {
            scheme = url.substring(0, schemeIdx + 3);
            domain = url.substring(schemeIdx + 3);
        } else {
            scheme = "http://";
        }

        int i = domain.indexOf("/");
        if (i > 0) {
            return scheme + domain.substring(0, i);
        }
        return scheme + domain;
    }

}
