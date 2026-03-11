(ns puppetlabs.trapperkeeper.services.webserver.normalized-uri-helpers
  (:require [puppetlabs.i18n.core :as i18n]
            [ring.util.jakarta.servlet :as servlet]
            [schema.core :as schema])
  (:import (com.puppetlabs.trapperkeeper.services.webserver.jetty12.utils
             HttpServletRequestWithAlternateRequestUri)
           (java.util EnumSet)
           (jakarta.servlet DispatcherType Filter)
           (jakarta.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.ee10.servlet FilterHolder ServletContextHandler)
           (org.eclipse.jetty.util URIUtil)))

(schema/defn ^:always-validate normalize-uri-path :- schema/Str
  "Return a 'normalized' version of the uri path represented on the incoming
  request.  The 'normalization' consists of three steps:

  1) URL (percent) decode the path, assuming any percent-encodings represent
     UTF-8 characters.

   An exception may be thrown if the request has malformed content, e.g.,
   partially-formed percent-encoded characters like '%A%B'.

   If a semicolon character, U+003B, is found during the decoding process, it
   and any following characters will be removed from the decoded path.

  2) Check the percent-decoded path for any relative path segments ('..' or
     '.').

   An IllegalArgumentException is thrown if one or more segments are found.

  3) Compact any repeated forward slash characters in a path."
  [request :- HttpServletRequest]
  (let [percent-decoded-uri-path (-> request
                                     (.getRequestURI)
                                     (URIUtil/decodePath))
        canonicalized-uri-path (URIUtil/canonicalPath percent-decoded-uri-path)]
    (if (or (nil? canonicalized-uri-path)
            (not= (.length percent-decoded-uri-path)
                  (.length canonicalized-uri-path)))
      (throw (IllegalArgumentException.
               ^String (i18n/trs "Invalid relative path (.. or .) in: {0}"
                                 percent-decoded-uri-path)))
      (URIUtil/compactPath canonicalized-uri-path))))

(schema/defn ^:always-validate normalized-uri-filter :- Filter
  "Create a servlet filter which provides a normalized request URI on to its
  downstream consumers for an incoming request.  The normalized URI would be
  returned for a 'getRequestURI' call on the HttpServletRequest parameter.
  Normalization is done per the rules described in the `normalize-uri-path`
  function.  If an error is encountered during request URI normalization, an
  HTTP 400 (Bad Request) response is returned rather than the request being
  passed on its downstream consumers."
  []
  (reify Filter
    (init [_ _])
    (doFilter [_ request response chain]
     ;; The method signature for a servlet filter has a 'request' of the
     ;; more generic 'ServletRequest' and 'response' of the more generic
     ;; 'ServletResponse'.  While we practically shouldn't see anything
     ;; but the more specific Http types for each, this code explicitly
     ;; checks to see that the requests are Http types as the URI
     ;; normalization would be irrelevant for other types.
      (if (and (instance? HttpServletRequest request)
               (instance? HttpServletResponse response))
        (if-let [normalized-uri
                 (try
                   (normalize-uri-path request)
                   (catch IllegalArgumentException ex
                     (servlet/update-servlet-response
                      response
                      {:status 400
                       :body (.getMessage ex)})
                     nil))]
          (.doFilter chain
                     (HttpServletRequestWithAlternateRequestUri.
                      request
                      normalized-uri)
                     response))
        (.doFilter chain request response)))
    (destroy [_])))

(schema/defn ^:always-validate
  add-normalized-uri-filter-to-servlet-handler!
  "Adds a servlet filter to the servlet handler which provides a normalized
  request URI on to its downstream consumers for an incoming request."
  [handler :- ServletContextHandler]
  (let [filter-holder (FilterHolder. (normalized-uri-filter))]
    (.addFilter handler
                filter-holder
                "/*"
                (EnumSet/of DispatcherType/REQUEST))))

(schema/defn ^:always-validate
  maybe-add-normalized-uri-filter!
  "If normalize-request-uri? is true, adds a URI normalization filter to
  the given ServletContextHandler."
  [handler :- ServletContextHandler
   normalize-request-uri? :- schema/Bool]
  (when normalize-request-uri?
    (add-normalized-uri-filter-to-servlet-handler! handler)))
