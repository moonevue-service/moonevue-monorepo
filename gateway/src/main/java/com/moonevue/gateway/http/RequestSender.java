package com.moonevue.gateway.http;

import org.apache.hc.core5.http.Method;

public interface RequestSender {
    String send(Method mothod, String url, String payload) throws Exception;
}
