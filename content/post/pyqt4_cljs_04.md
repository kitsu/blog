+++
categories = ["development"]
tags = ["code", "clojurescript", "pyqt4", "lister"]
date = "2016-05-23T13:15:39-07:00"
title = "ClojureScript in PyQt4 QWebView - Fix Dragging"

+++

Higher order event channels for consistent interactions.
<!--more-->
<hr/><br/>
This morning I let my boss try out the online quick-marking demo I posted
yesterday. He had a little trouble with my warning about mouse-up events being
lost outside the list frame, even after I explained what was happening. I told
him it was just a symptom of having the list in an iframe, and wouldn't effect
the actual application. I totally believed that at the time too, but I decided
to verify it and found that leaving the application window has the exact same
effect.

The first problem with yesterday's code is that it only exits the inner loop on
mouse up, so I decided to add another channel for mouseleave events. To cut down
on the clutter I used async/merge to create an `end-chan` that combined up and
leave events:

{{< highlight clojure >}}

...
  (let [target (sel1 :#doclist)
        lmb-down-chan (evt-chan target :mousedown 0)
        lmb-move-chan (evt-chan target :mousemove 0)
        lmb-up-chan (evt-chan target :mouseup 0)
        left-chan (evt-chan target :mouseleave 0)
        end-chan (async/merge [lmb-up-chan left-chan])]
...

{{< /highlight >}}

I thought having that extra source of end events would fix the problem, but
instead the app behavior just got weirder. I started doubting my sanity trying
to debug the problem, when the cause suddenly occurred to me: All those
channels are laying around collecting input even when there aren't any
consumers! This means that when you finally get a `down` event you most likely
already have a `move` or `leave` event queued!

The solution is something I specifically avoided doing from the beginning. Bruce
Hauman, the guy behind Figwheel, had a really interesting series of blog posts
([Post 1](http://rigsomelight.com/2013/07/18/clojurescript-core-async-todos.html),
[Post 2](http://rigsomelight.com/2013/08/12/clojurescript-core-async-dots-game.html),
[Post 3](http://rigsomelight.com/2013/08/22/channels-of-channels-dots-game-refactor.html))
back when core.async was still brand new. One trick he used was aggregating
*tagged* events all into one channel. I thought it was really clever, he even
starts building higher-order channels in the last post. When I was trying to
set up my event channels his stuff was the first thing I thought of, but I
decided that it was overkill for my application.

That turns out to be exactly the solution to this problem. Join all the channels
together and consume all events, ignoring some based on current state. First I
had to modify the event channelizer to tag the events:

{{< highlight clojure >}}

(defn evt-chan [target kind btn]
  ;; The sliding buffer probably isn't needed anymore?
  (let [out (chan (sliding-buffer 1))]
    (dommy/listen! target kind
                  (fn [evt]
                    (when (= (.-button evt) btn)
                      ;; Note this now puts a vector
                      (put! out [kind evt]))))
    out))

{{< /highlight >}}

Next I created a function that provides a channel of drag events, where a drag
event is every mouse event from `down` until either an `up` or `leave` event:

{{< highlight clojure >}}

(defn build-drag-chan [target]
  ;; Setup event source channels and start producing drag sub-channels
  (let [output-chan (chan)
        input-chan (async/merge [(evt-chan target :mousedown 0)
                                 (evt-chan target :mousemove 0)
                                 (evt-chan target :mouseup 0)
                                 (evt-chan target :mouseleave 0)])
        terminals #{:mouseup :mouseleave}]
    (go-loop [[tag evt] (<! input-chan)]
      (when (= tag :mousedown)
        (let [drag-chan (chan)]
          ;; Give new drag channel to consumers
          (>! output-chan drag)
          (>! drag-chan evt)
          ;; Consume move events until we get an up event
          (loop [[tag evt] (<! input-chan)]
            ;; Output events regardless of type
            (>! drag-chan evt)
            ;; Close drag on terminal event else recur
            (if (contains? terminals tag)
              (close! drag-chan)
              (recur (<! input-chan))))))
      ;; Wait for next LMB down
      (recur (<! input-chan)))
    output-chan))

{{< /highlight >}}

Finally I rewrote main to consume drag events:

{{< highlight clojure >}}

(defn main []
  ;; Get handle of target element and create drag channel
  (let [target (sel1 :#doclist)
        drag-chan (build-drag-chan target)]
    ;; Loop forever pulling channels of drag events from drag-chan
    (go-loop [drag-set (<! drag-chan)]
      ;; Store the initial row and all highlight states
      (let [row (get-row-num (<! drag-set))
            highlight (get-highlight target)]
        ;; Consume events from drag-set until it is closed
        (loop [evt (<! drag-set)]
         (when evt
           ;; update highlighting...
           (toggle-highlight highlight row (get-row-num evt))
           (recur (<! drag-set)))))
      (recur (<! drag-chan)))))

{{< /highlight >}}

And here is that demo again with better behavior:

<iframe src="/async_highlight_02/index.html"
        width="100%" height="100px" style="border: 1px solid black"></iframe>

<br/>
The only annoying behavior now is that your drag is terminated the second you
leave the highlight zone, which means drag-scrolling also terminates
highlighting. You can still use the scroll wheel to highlight beyond the initial
view though, so its not that big of a problem.