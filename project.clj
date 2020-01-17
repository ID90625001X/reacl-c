(defproject reacld "0.1.0-SNAPSHOT"
  :url "http://github.com/active-group/reacld"
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/clojurescript "1.10.238" :scope "provided"]
                 [reacl "2.2.0-SNAPSHOT"]
                 ;;[cljsjs/react "16.4.1-0"]
                 ;;[cljsjs/react-dom "16.4.1-0" :exclusions [cljsjs/react]]
                 ]

  :plugins [[lein-codox "0.10.5"]
            [lein-auto "0.1.3"]]

  :profiles {:dev {:dependencies [[codox-theme-rdash "0.1.2"]
                                  [com.bhauman/figwheel-main "0.2.0"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]
                   :source-paths ["src" "test" "examples"]
                   :resource-paths ["target" "resources"]}}

  :clean-targets ^{:protect false} [:target-path]

  ;; open http://localhost:9500/figwheel-extra-main/auto-testing for the tests.
  ;; open http://localhost:9500/figwheel-extra-main/todo and others for the examples
  :aliases {"fig" ["trampoline" "with-profile" "+dev,+test" "run" "-m" "figwheel.main" "-b" "dev" "-r"]}

  :codox {:language :clojurescript
          :metadata {:doc/format :markdown}
          :themes [:rdash]
          :src-dir-uri "http://github.com/active-group/reacld/blob/master/"
          :src-linenum-anchor-prefix "L"}

  :auto {:default {:paths ["src" "test" "examples"]}}
  )
