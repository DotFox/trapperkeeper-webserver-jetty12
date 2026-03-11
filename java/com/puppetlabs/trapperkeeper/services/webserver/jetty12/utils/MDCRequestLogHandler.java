package com.puppetlabs.trapperkeeper.services.webserver.jetty12.utils;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.slf4j.MDC;

import java.util.Map;

public class MDCRequestLogHandler extends Handler.Wrapper {
    public static final String MDC_ATTR = "puppetlabs.mdc";

    public MDCRequestLogHandler() {
        super();
    }

    public MDCRequestLogHandler(Handler handler) {
        super(handler);
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        if (mdcContext != null) {
            request.setAttribute(MDC_ATTR, mdcContext);
        }
        return super.handle(request, response, callback);
    }
}
