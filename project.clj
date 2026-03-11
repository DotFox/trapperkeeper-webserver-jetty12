(def jetty-12-version "12.0.16")

(defproject com.puppetlabs/trapperkeeper-webserver-jetty12 "1.0.19-SNAPSHOT"
  :description "A jetty12-based webserver implementation for use with the puppetlabs/trapperkeeper service framework."
  :url "https://github.com/dotfox/trapperkeeper-webserver-jetty12"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :min-lein-version "2.9.1"

  :parent-project {:coords [puppetlabs/clj-parent "7.3.5"]
                   :inherit [:managed-dependencies]}

  ;; Abort when version ranges or version conflicts are detected in
  ;; dependencies. Also supports :warn to simply emit warnings.
  ;; requires lein 2.2.0+.
  :pedantic? :abort
  :dependencies [[org.clojure/clojure]
                 [org.clojure/java.jmx]
                 [org.clojure/tools.logging]

                 [org.flatland/ordered "1.5.9"]

                 [jakarta.servlet/jakarta.servlet-api "6.0.0"]
                 ;; Jetty Webserver
                 [org.eclipse.jetty/jetty-server ~jetty-12-version]
                 [org.eclipse.jetty.ee10/jetty-ee10-servlet ~jetty-12-version]
                 [org.eclipse.jetty.ee10/jetty-ee10-servlets ~jetty-12-version]
                 [org.eclipse.jetty.ee10/jetty-ee10-webapp ~jetty-12-version]
                 [org.eclipse.jetty.ee10/jetty-ee10-proxy ~jetty-12-version]
                 [org.eclipse.jetty/jetty-jmx ~jetty-12-version]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-server ~jetty-12-version]
                 ;; used in pcp-client
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-client ~jetty-12-version]
                 [org.eclipse.jetty.websocket/jetty-websocket-jetty-api ~jetty-12-version]


                 [prismatic/schema]
                 [org.ring-clojure/ring-jakarta-servlet "1.12.2"]
                 [ring/ring-codec]
                 [ch.qos.logback/logback-core]
                 [ch.qos.logback/logback-classic]

                 [puppetlabs/ssl-utils]
                 [puppetlabs/kitchensink]
                 [puppetlabs/trapperkeeper]
                 [puppetlabs/i18n]
                 [puppetlabs/trapperkeeper-filesystem-watcher]

                 [org.slf4j/jul-to-slf4j]]

  :source-paths  ["src"]
  :java-source-paths  ["java"]

  :plugins [[lein-parent "0.3.7"]
            [puppetlabs/i18n "0.8.0"]]

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

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
                      :dependencies [[puppetlabs/http-client]
                                     [puppetlabs/kitchensink nil :classifier "test"]
                                     [puppetlabs/trapperkeeper nil :classifier "test"]
                                     [org.clojure/tools.namespace]
                                     [compojure]
                                     [ring/ring-core]
                                     [hato "0.9.0"]]}
             :dev-only {:dependencies [[org.bouncycastle/bcpkix-jdk18on]]
                        :jvm-opts ["-Djava.util.logging.config.file=dev-resources/logging.properties"]}
             :dev [:shared :dev-only]
             :fips-only {:dependencies [[org.bouncycastle/bcpkix-fips]
                                        [org.bouncycastle/bc-fips]
                                        [org.bouncycastle/bctls-fips]]
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
             :provided {:dependencies [[org.bouncycastle/bcpkix-jdk18on]]
                        :resource-paths ["dev-resources"]}

             :testutils {:source-paths ^:replace ["test/clj"]
                         :java-source-paths ^:replace ["test/java"]}}

  :main puppetlabs.trapperkeeper.main

  :repositories [["puppet-releases" "https://artifactory.delivery.puppetlabs.net/artifactory/list/clojure-releases__local/"]
                 ["puppet-snapshots" "https://artifactory.delivery.puppetlabs.net/artifactory/list/clojure-snapshots__local/"]])

