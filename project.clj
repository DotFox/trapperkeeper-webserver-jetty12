(def deploy-releases-url
  (or (System/getenv "DEPLOY_RELEASES_URL")
      "https://clojars.org/repo"))

(def deploy-snapshots-url
  (or (System/getenv "DEPLOY_SNAPSHOTS_URL")
      deploy-releases-url))

(def deploy-username
  (or (System/getenv "DEPLOY_USERNAME")
      :env/clojars_username))

(def deploy-password
  (or (System/getenv "DEPLOY_PASSWORD")
      :env/clojars_password))

(defproject dev.dotfox/trapperkeeper-webserver-jetty12 "2.0.3-SNAPSHOT"
  :description "A jetty12-based webserver implementation for use with the puppetlabs/trapperkeeper service framework."
  :url "https://github.com/dotfox/trapperkeeper-webserver-jetty12"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :min-lein-version "2.9.1"

  ;; Abort when version ranges or version conflicts are detected in
  ;; dependencies. Also supports :warn to simply emit warnings.
  ;; requires lein 2.2.0+.
  :pedantic? :abort
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/java.jmx "1.0.0"]
                 [org.clojure/tools.logging "1.2.4"]

                 [jakarta.servlet/jakarta.servlet-api "6.0.0"]
                 ;; Jetty Webserver
                 [org.eclipse.jetty/jetty-server "12.1.7"]
                 [org.eclipse.jetty.ee10/jetty-ee10-servlet "12.1.7"]
                 [org.eclipse.jetty.ee10/jetty-ee10-webapp "12.1.7"]
                 [org.eclipse.jetty.ee10/jetty-ee10-proxy "12.1.7"]
                 [org.eclipse.jetty/jetty-jmx "12.1.7"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-server "12.1.7"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-client "12.1.7"]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-api "12.1.7"]

                 [prismatic/schema "1.1.12"]
                 [org.ring-clojure/ring-jakarta-servlet "1.12.2"]
                 [ring/ring-codec "1.2.0"]
                 [ch.qos.logback/logback-core "1.3.14"]
                 [ch.qos.logback/logback-classic "1.3.14"]

                 [puppetlabs/ssl-utils "3.5.2"]
                 [puppetlabs/kitchensink "3.2.5"]
                 [puppetlabs/trapperkeeper "4.0.0"]
                 [puppetlabs/i18n "0.9.2"]
                 [puppetlabs/trapperkeeper-filesystem-watcher "1.2.2"]

                 [org.slf4j/jul-to-slf4j "2.0.7"]]

  :source-paths  ["src"]
  :java-source-paths  ["java"]

  :plugins [[puppetlabs/i18n "0.8.0"]]

  :deploy-repositories [["releases" {:url ~deploy-releases-url
                                     :username ~deploy-username
                                     :password ~deploy-password
                                     :sign-releases false}]
                        ["snapshots" {:url ~deploy-snapshots-url
                                      :username ~deploy-username
                                      :password ~deploy-password}]]

  ;; By declaring a classifier here and a corresponding profile below we'll get an additional jar
  ;; during `lein jar` that has all the code in the test/ directory. Downstream projects can then
  ;; depend on this test jar using a :classifier in their :dependencies to reuse the test utility
  ;; code that we have.
  :classifiers [["test" :testutils]]

  :test-paths ["test/clj"]

  :profiles {:shared {:source-paths ["examples/multiserver_app/src"
                                     "examples/ring_app/src"
                                     "examples/servlet_app/src/clj"
                                     "examples/war_app/src"
                                     "examples/webrouting_app/src"]
                      :java-source-paths ["examples/servlet_app/src/java"
                                          "test/java"]
                      :resource-paths ["dev-resources"]
                      :dependencies [[puppetlabs/http-client "2.1.2" :exclusions [commons-io]]
                                     [puppetlabs/kitchensink "3.2.5" :classifier "test"]
                                     [puppetlabs/trapperkeeper "4.0.0" :classifier "test"]
                                     [org.clojure/tools.namespace "0.2.11"]
                                     [compojure "1.5.0"]
                                     [ring/ring-core "1.12.2"]
                                     [hato "0.9.0"]]}
             :dev-only {:dependencies [[org.bouncycastle/bcpkix-jdk18on "1.74"]]
                        :jvm-opts ["-Djava.util.logging.config.file=dev-resources/logging.properties"]}
             :dev [:shared :dev-only]
             :fips-only {:managed-dependencies [[org.bouncycastle/bc-fips "1.0.2.6"]]
                         :dependencies [[org.bouncycastle/bcpkix-fips "1.0.7"]
                                        [org.bouncycastle/bc-fips "1.0.2.6"]
                                        [org.bouncycastle/bctls-fips "1.0.19"]]
                         ;; this only ensures that we run with the proper profiles
                         ;; during testing. This JVM opt will be set in the puppet module
                         ;; that sets up the JVM classpaths during installation.
                         :jvm-opts ~(let [version (System/getProperty "java.version")
                                          [major minor _] (clojure.string/split version #"\.")
                                          major-int (java.lang.Integer/parseInt major)]
                                      (if (>= major-int 17)
                                        ["-Djava.security.properties==dev-resources/jdk17-fips-security"]
                                        (throw (ex-info "Unsupported major Java version. Expects 17+."
                                                        {:major major :minor minor}))))}
             :fips [:shared :fips-only]

             ;; per https://github.com/technomancy/leiningen/issues/1907
             ;; the provided profile is necessary for lein jar / lein install
             :provided {:dependencies [[org.bouncycastle/bcpkix-jdk18on "1.74"]]
                        :resource-paths ["dev-resources"]}

             :testutils {:source-paths ^:replace ["test/clj"]
                         :java-source-paths ^:replace ["test/java"]}}

  :main puppetlabs.trapperkeeper.main

  :repositories [["puppet-releases" "https://artifactory.delivery.puppetlabs.net/artifactory/list/clojure-releases__local/"]
                 ["puppet-snapshots" "https://artifactory.delivery.puppetlabs.net/artifactory/list/clojure-snapshots__local/"]])
