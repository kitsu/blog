(ns anim-demo.core
  (:require [cljs.core.async :as async
             :refer [<! >! timeout chan take! put!
                     sliding-buffer close!]]
            [dommy.core :as dommy :refer-macros [sel sel1]])
  (:require-macros [cljs.core.async.macros :as async-macros
                    :refer [go go-loop do-alt alt!]]))

(def fps (/ 1000 60))
(def anim-time 250)

(defn anim-source
  "A continually renewing timer simulating requestAnimationFrame callback."
  [ch]
  (put! ch (.now js/Date))
  (js/setTimeout (partial anim-source ch) fps))

(def frame-chan (let [ch (chan (sliding-buffer 1))]
                  (js/setTimeout (partial anim-source ch) fps)
                  ch))

(defn evt-chan
  "Create channel of tagged events from target element."
  [target kind pred]
  (let [out (chan (sliding-buffer 1))]
    (dommy/listen! target kind
                  (fn [evt]
                    (when (pred evt)
                      (put! out [kind evt]))))
    out))

(defn animate
  "Generic animation go-loop."
  [duration {:keys [initialize! transition! finalize!]}]
  (let [init (initialize!)
        start (.now js/Date)]
    (go-loop [now (<! frame-chan)]
      (let [elapse (- now start)
            percent (/ elapse duration)]
        (if (< elapse duration)
          (do (transition! init percent)
              (recur (<! frame-chan)))
          (finalize!))))))

(defn slide-up
  "Animated hide."
  ([elem] (slide-up elem anim-time))
  ([elem duration]
   (when-not (dommy/hidden? elem)
     (animate duration
              {:initialize! (fn [] (dommy/px elem :height))
               :transition! (fn [height percent]
                              (dommy/set-px! elem :height
                                             (* (- 1 percent) height)))
               :finalize! (fn []
                            (dommy/hide! elem)
                            (dommy/remove-style! elem :height))}))))

(defn slide-down
  "Animated reveal."
  ([elem] (slide-down elem anim-time))
  ([elem duration]
   (when (dommy/hidden? elem)
     (animate duration
              {:initialize! (fn []
                              (dommy/show! elem)
                              (let [height (dommy/px elem :height)]
                                (dommy/set-px! elem :height 0)
                                height))
               :transition! (fn [height percent]
                              (dommy/set-px! elem :height (* percent height)))
               :finalize! (fn [] (dommy/remove-style! elem :height))}))))

(defn main
  "Main entry point of UI code."
  []
  ;; Get handle of target element, if not found exit 
  (when-let [target (sel1 :#doclist)]
    (let [toggle (sel1 :#toggle_button)
          clicks (evt-chan toggle :click some?)]
      (go-loop [pair (<! clicks) shown true]
               (if shown
                 (doseq [elem (sel :div.row)]
                   (slide-up elem))
                 (doseq [elem (sel :div.row)]
                   (slide-down elem)))
               (recur (<! clicks) (not shown))))))

(dommy/listen! js/window :load main)
