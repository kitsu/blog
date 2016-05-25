+++
categories = ["development"]
tags = ["code", "clojurescript", "async", "lister"]
date = "2016-05-21T13:12:37-07:00"
title = "ClojureScript in PyQt4 QWebView - Code"

+++

*Actually* coding the ClojureScript prototype.
<!--more-->
<hr/><br/>
Firstly a follow up to the last statement in the previous post. It turns out
Leiningen/cljsbuild is smarter than me, but in a very unhelpful way. Lein
apparently knows when your code changes and, if the artifacts are up to date
with the source, it skips building anything. Unfortunately Lein doesn't care if
you've changed build profiles. Compiling with advanced optimizations looks the
same as a dev build. I fixed `build.cmd` to delete the target folder and
testbed/main.js before running Lein, so now you get a build that isn't dependent
on the testbed/lib folder.

I've decided to use [Dommy](https://github.com/plumatic/dommy) to interact with
the DOM and setup event feeds. Speaking of event feeds, I had to remember how to
hook an event callback up to a channel. I've made it overly complicated in the
past, but it is really simple: You just need a channel accessible in the scope
of a lambda that is the event handler.

{{< highlight clojure >}}

(defn get-click-chan [target]
  ;; Connect an event stream to a channel, return channel
  (let [out (chan)]
    (dommy/listen! target :mousedown
                  (fn [evt] (put! out evt)))
    out))

{{< /highlight >}}

The behavior I want is, when the user clicks and drags, the cells between the
initial click location and the current mouse location have their highlight state
*toggled*. The selection is updated as the cursor is moved until the mouse
button is released. All updates during one composite event are relative to the
initial condition at the time of clicking.

Note: Dragging past the edge of the frame looses `up` events.
<iframe src="/async_highlight_01/index.html"
        width="100%" height="100px" style="border: 1px solid black"></iframe>

I came up with a bunch of ideas about what events to actually capture, and how
to map those events back onto the DOM. At first I was going to track enter/exit
in each row div decide to toggle based on if the cursor passed through the top
or the bottom, which would have been really complicated. Next I tried using the
difference in clientY to get a pixel range and then... IDK, use the row hight to
calculate the end row? Using pixel values would be bad since with a scroll bar
the start coord has to change, and the end coord would stop changing at the edge
of the window. In the end I cheated and used the excessive ID attributes I added
when generating the HTML. I think you could use nth-child or
`target.children.indexOf` instead, but since I'm adding the IDs anyway.

I'll start at the end and work toward the specific. First the document needs to
be ready before event listers can be attached:

{{< highlight clojure >}}

(dommy/listen! js/window :load main)

{{< /highlight >}}

The main function reads pretty close to the behavior description. After some
setup I start a loop waiting for Left Mouse Button (LMB) down events. When I get
one I start a nested loop to update the highlight range that exits on the first
LMB up event.

{{< highlight clojure >}}

(defn main []
  ;; Setup some event input channels and start a process to watch them.
  (let [target (sel1 :#doclist)
        lmb-down-chan (evt-chan target :mousedown 0)
        lmb-move-chan (evt-chan target :mousemove 0)
        lmb-up-chan (evt-chan target :mouseup 0)]
    ;; Wait for initial LMB down
    (go-loop [evt (<! lmb-down-chan)]
      (let [row (get-row-num evt)
            highlight (get-highlight target)]
        ;; Consume move events until we get an up event
        (loop [[evt ch] (alts! [lmb-up-chan lmb-move-chan] {:priority true})]
          ;; update highlighting...
          (toggle-highlight highlight row (get-row-num evt))
          (when-not (= ch lmb-up-chan)
            (recur (alts! [lmb-up-chan lmb-move-chan] {:priority true})))))
      ;; Wait for next LMB down
      (recur (<! lmb-down-chan)))))

{{< /highlight >}}

The duplication of code in the loop binding and recur is annoying, but I can't
think of a good way to avoid it. The channel builder is a refactoring of
separate constructor functions into one. I played around with producing tagged
events or preprocessed event data, but in the end I just had it filter on mouse
button.

{{< highlight clojure >}}

(defn evt-chan [target kind btn]
  (let [out (chan (sliding-buffer 1))]
    (dommy/listen! target kind
                  (fn [evt]
                    (when (= (.-button evt) btn)
                      (put! out evt))))
    out))

{{< /highlight >}}

The `get-row-num` function just gets the number part of the row id. It also
handles the case when the event target is a child of the containing row.

{{< highlight clojure >}}

(defn get-row-num [evt]
  ;; Extract the row number from a mouse event
  (let [target (.-target evt)
        id (.-id target)
        kind (subs id 0 3)
        num (int (subs id 3))]
    (if-not (= kind "row")
      (int (subs (.-id (.-parentElement target)) 3))
      num)))

{{< /highlight >}}

On mouse down I store the row number of the initial event target, and generate a
vector of the highlight state of all rows using `get-highlight`.

{{< highlight clojure >}}

(defn get-highlight [target]
  ;; Return vector of bools where true means highlighted
  (mapv #(dommy/has-class? % "marked") (sel target "div.row")))

{{< /highlight >}}

In the inner loop I use `alts!` to get the next event and its source channel
from either `up` or `move`. Fortunately all mouse events have the same members,
so regardless of type I update the highlighting. The `toggle-highlight` function
is flaky, but it works for now. First, since the user can drag up or down the
list I have to ensure start is before end. From there I map over `[index bool]`
pairs for *all* rows. I need to ensure that rows outside the selected range
still have their initial value, and rows inside have the opposite.

{{< highlight clojure >}}

(defn toggle-highlight [initial start end]
  ;; Ensure all rows have correct highlight.
  (let [[start end] (if (> start end) [end start] [start end])]
    (dorun
      (map-indexed (fn [id high]
                     (let [row (sel1 (str "#row" id))
                           marked (dommy/has-class? row "marked")]
                       (if (and (>= id start) (<= id end))
                         ;; This row is in selected range
                         (when (= marked high)
                           (dommy/toggle-class! row "marked"))
                         ;; This row is outside selected range
                         (when-not (= marked high)
                           (dommy/toggle-class! row "marked")))))
                   initial))))

{{< /highlight >}}

And that's it.

It performs reasonably well. I can operate on a list of 100 rows
comfortably. The performance degrades as the list length increases though
because `toggle-highlight` iterates over the entire list on every event. I wont
get a backlog of events because I am using a `sliding-buffer` of size one, but
the UI stops responding in the body of `toggle-highlight`. This is problematic
though because Lister will handle lists with thousands of rows.

The obvious solution is to avoid even looking at rows that haven't been touched
yet. You could keep track of the total range around the initial row that has
been visited and somehow align that with the initial highlight data. Another
thing to notice is that once a row falls out of range and is returned to initial
state it doesn't need to be visited again. Since the real problem is lag inside
the update loop another option is to use another async channel to hold queued
updates. The update queue could then be cleared by another `go-loop` without
blocking user input.
