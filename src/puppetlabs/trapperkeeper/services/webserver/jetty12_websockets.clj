(ns puppetlabs.trapperkeeper.services.webserver.jetty12-websockets
  (:import (clojure.lang IFn)
           (org.eclipse.jetty.websocket.api Session Session$Listener$AutoDemanding Callback)
           (org.eclipse.jetty.websocket.server ServerUpgradeRequest ServerUpgradeResponse ServerWebSocketContainer WebSocketCreator)
           (java.security.cert X509Certificate)
           (java.time Duration)
           (java.util.concurrent CountDownLatch TimeUnit)
           (java.nio ByteBuffer))

  (:require [clojure.tools.logging :as log]
            [puppetlabs.trapperkeeper.services.websocket-session :refer [WebSocketProtocol]]
            [schema.core :as schema]
            [puppetlabs.i18n.core :as i18n]))

(def WebsocketHandlers
  {(schema/optional-key :on-connect) IFn
   (schema/optional-key :on-error) IFn
   (schema/optional-key :on-close) IFn
   (schema/optional-key :on-text) IFn
   (schema/optional-key :on-bytes) IFn})

(defprotocol WebSocketSend
  (-send! [x ws] "How to encode content sent to the WebSocket clients"))

(defrecord WebSocketSession [^Session session certs request-path ^CountDownLatch closure-latch])

(extend-protocol WebSocketSend
  (Class/forName "[B")
  (-send! [ba ws]
    (-send! (ByteBuffer/wrap ba) ws))

  ByteBuffer
  (-send! [bb ws]
    (.sendBinary ^Session (:session ws) ^ByteBuffer bb Callback/NOOP))

  String
  (-send! [s ws]
    (.sendText ^Session (:session ws) ^String s Callback/NOOP)))

(extend-protocol WebSocketProtocol
  WebSocketSession
  (send! [this msg]
    (-send! msg this))
  (close!
    ([this]
     (.close (:session this))
     (.await (:closure-latch this) 30 TimeUnit/SECONDS))
    ([this code reason]
     (.close (:session this) code reason Callback/NOOP)
     (.await (:closure-latch this) 30 TimeUnit/SECONDS)))
  (disconnect [this]
    (.disconnect (:session this)))
  (remote-addr [this]
    (.getRemoteSocketAddress (:session this)))
  (ssl? [this]
    (.isSecure (:session this)))
  (peer-certs [this]
    (:certs this))
  (request-path [this]
    (:request-path this))
  (idle-timeout! [this ms]
    (.setIdleTimeout (:session this) (Duration/ofMillis ms)))
  (connected? [this]
    (.isOpen (:session this))))

(defn no-handler
  [event & args]
  (log/debug (i18n/trs "No handler defined for websocket event ''{0}'' with args: ''{1}''"
                       event args)))

(def client-count (atom 0))
(defn extract-CN-from-certs
  [x509certs]
  (when (not-empty x509certs)
    (.getSubjectX500Principal (first x509certs))))

(schema/defn ^:always-validate proxy-ws-adapter
  [handlers :- WebsocketHandlers
   x509certs :- [X509Certificate]
   requestPath :- String
   closureLatch :- CountDownLatch]
  (let [client-id (swap! client-count inc)
        certname (extract-CN-from-certs x509certs)
        ws-session-atom (atom nil)
        {:keys [on-connect on-error on-text on-close on-bytes]
         :or {on-connect (partial no-handler :on-connect)
              on-error   (partial no-handler :on-error)
              on-text    (partial no-handler :on-text)
              on-close   (partial no-handler :on-close)
              on-bytes   (partial no-handler :on-bytes)}} handlers]
    (reify Session$Listener$AutoDemanding
      (onWebSocketOpen [_this session]
        (log/tracef "%d on-connect certname:%s uri:%s" client-id certname requestPath)
        (let [ws (->WebSocketSession session x509certs requestPath closureLatch)]
          (reset! ws-session-atom ws)
          (let [result (on-connect ws)]
            (log/tracef "%d exiting on-connect" client-id)
            result)))
      (onWebSocketText [_this message]
        (log/tracef "%d on-text certname:%s uri:%s" client-id certname requestPath)
        (let [result (on-text @ws-session-atom message)]
          (log/tracef "%d exiting on-text" client-id)
          result))
      (onWebSocketBinary [_this payload offset len]
        (log/tracef "%d on-binary certname:%s uri:%s" client-id certname requestPath)
        (let [result (on-bytes @ws-session-atom payload offset len)]
          (log/tracef "%d exiting on-binary" client-id)
          result))
      (onWebSocketClose [_this statusCode reason]
        (log/tracef "%d on-close certname:%s uri:%s" client-id certname requestPath)
        (.countDown closureLatch)
        (let [result (on-close @ws-session-atom statusCode reason)]
          (log/tracef "%d exiting on-close" client-id)
          result))
      (onWebSocketError [_this cause]
        (log/tracef "%d on-error certname:%s uri:%s" client-id certname requestPath)
        (let [result (on-error @ws-session-atom cause)]
          (log/tracef "%d exiting on-error" client-id)
          result)))))

(schema/defn ^:always-validate proxy-ws-creator :- WebSocketCreator
  [handlers :- WebsocketHandlers]
  (log/trace "proxy-ws-creator")
  (reify WebSocketCreator
    (createWebSocket [_this ^ServerUpgradeRequest req ^ServerUpgradeResponse _res ^Callback cb]
      (let [x509certs (vec (or (.getCertificates req) []))
            requestPath (str (.getRequestURI req))
            closureLatch (CountDownLatch. 1)]
        (.succeed cb)
        (proxy-ws-adapter handlers x509certs requestPath closureLatch)))))

(defn configure-websocket-container
  [context-handler server handlers]
  (let [container (ServerWebSocketContainer/ensure server context-handler)]
    (.addMapping container "/*" (proxy-ws-creator handlers))))
