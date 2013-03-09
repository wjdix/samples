(ns helloworld-ui.app
  (:require [clojure.string :as string]
            [domina :as dom]
            [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [io.pedestal.app :as app]
            [io.pedestal.app.util.platform :as platform]
            [io.pedestal.app.render :as render]
            [io.pedestal.app.render.push :as push-render]
            [io.pedestal.app.render.events :as events]
            [io.pedestal.app.render.push.templates :as templates]
            ;; repl-client must be required somewhere in order for the
            ;; fresh view to work.
            [io.pedestal.app.net.repl-client :as repl-client])
  (:require-macros [helloworld-ui.html-templates :as html-templates]))

;; Models

(defn word-model [state event]
  (if (= (msg/type event) msg/init) (:value event) (:input event)))

;; Views

(defn backwards [state input-name old new]
  (string/reverse new))

(defn length [state input-name old new]
  (count new))

;; Emitters

(defn word-emitter
  ([views]
     [{:app {:form {:events {:update-word [{msg/topic :word (msg/param :input) {}}]}}}}])
  ([inputs changed-inputs]
     (reduce (fn [a input-name]
               (let [new-value (:new (get inputs input-name))]
                 (conj a (case input-name
                           :word    [:value [:app :word]    new-value]
                           :reverse [:value [:app :reverse] new-value]
                           :length  [:value [:app :length]  new-value]
                           []))))
             []
             changed-inputs)))

;; Rendering

(def templates (html-templates/helloworld-ui-templates))

(defn render-simple-page [r [_ path] d]
  (let [parent (push-render/get-parent-id r path)
        html (templates/add-template r path (:helloworld-ui-page templates))]
    (dom/append! (dom/by-id parent) (html {:title "helloworld-ui"}))))

(defn form-event-enter [r [_ path event-name messages] d]
  (events/collect-and-send :keyup
                           "word-input"
                           d
                           event-name
                           messages
                           {"word-input" :input}))

(defn render-word-fn [k]
  (fn [r [_ path _ v] d]
    (templates/update-parent-t r path {k (str v)})))

(def render-config
  [[:node-create      [:app]          render-simple-page]
   [:transform-enable [:app :form]    form-event-enter]
   [:value            [:app :word]    (render-word-fn :identity)]
   [:value            [:app :reverse] (render-word-fn :reverse)]
   [:value            [:app :length]  (render-word-fn :length)]])

;; Dataflow

(def word-app
  {:models   {:word    {:init "" :fn word-model}}
   :views    {:reverse {:fn backwards :input #{:word}}
              :length  {:fn length :input #{:word}}}
   :emitters {:emitter {:fn word-emitter :input #{:word :reverse :length}}}})

;; Start

(defn ^:export main []
  (let [app (app/build word-app)
        render-fn (push-render/renderer "content" render-config render/log-fn)]
    (render/consume-app-model app render-fn)
    (app/begin app)))