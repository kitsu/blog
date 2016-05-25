+++
categories = ["development"]
tags = ["code", "clojurescript", "async", "testing", "lister"]
date = "2016-05-24T19:03:27-07:00"
title = "ClojureScript in PyQt4 QWebView - Testing"

+++

Setting up basic ClojureScript testing using devcards.
<!--more-->
<hr/><br/>
One thing I have never gotten working nicely in ClojureScript is testing.
Running cljs tests requires a special build setup and a Javascript runtime.
Tests run in text-mode in the console, but setting up Node.js used to be a pain,
and running a browser for its text output seems like a waste. With the new
reader-conditionals you could probably test your code under regular Clojure, but
only if you don't need Javascript interop.

[Devcards](https://github.com/bhauman/devcards), another tool by Bruce Hauman,
provides an easy way to expose parts of your ClojureScript code in a webpage. It
is mainly intended to be used to unroll your application so you can see and
interact with any component all in one place. It also comes with support for
exposing test suites in a webpage.

I went with a Figwheel-free setup, but just in playing around a little I am
already missing auto-reload. The setup is pretty simple, the only problem I ran
into was putting the `:devcards true` setting in the wrong part of my testing
build profile. I didn't have any resource loading problems since I'm using
Chrome to open the testcard page. Here's how it looks:

{{< figure src="/images/testbed-devcards.JPG" alt="Devcard output" >}}
<br/>

I am not too familier with `core.test`, the built in Clojure test framework,
since I usually use [Midje](https://github.com/marick/Midje) in normal
Clojure. While I'm not going to say what I came up with is idiomatic, but I did
have some fun figuring things out. The first testable function is `get-row-num`,
which is pretty straight forward:

{{< highlight clojure >}}

;; Build some fake evt->target->id hierarchies on random input
(defn row [r] (clj->js {:id (str "row" r)}))
(defn col [r] (clj->js {:id "col1" :parentElement (row r)}))
(defn row-evt [r] (clj->js {:target (row r)}))
(defn col-evt [r] (clj->js {:target (col r)}))

(dc/deftest get-row-num
  "Test row extraction from event using target & id"
  (testing "Returns correct number given row event"
    (let [r (rand-int 999)]
      (is (= (testbed/get-row-num (row-evt r)) r)
          "Row number should be number after row prefix")))
  (testing "Returns correct number given col event"
    (let [r (rand-int 999)]
      (is (= (testbed/get-row-num (col-evt r)) r)
          "Row number should be from column parent"))))

{{< /highlight >}}

The next bit was a little tricky. Channel consumers don't really know or care
where items on a channel come from, so if you can mock the input you can
verify the external behavior of a channel->channel function. Once I factored out
the input-chan creation I was able to test the `build-drag-chan` function:

{{< highlight clojure >}}

(defn get-input-chan
  "Build fake mouse event stream channel."
  []
  (let [input-chan  (chan 12)
        data [[:mousemove  0]
              [:mousemove  1]
              [:mousedown  2]
              [:mousemove  3]
              [:mousemove  4]
              [:mousemove  5]
              [:mouseleave 6]
              [:mousemove  7]
              [:mouseup    8]
              [:mousedown  9]
              [:mousemove 10]
              [:mousemove 11]
              [:mouseup   12]]]
    (go-loop [head (first data) tail (rest data)]
      (when head
        (>! input-chan head)
        (recur (first tail) (rest tail))))
    input-chan))

(def expected-drags [[2 3 4 5 6 nil] [9 10 11 12 nil]])

(defn consume-channel
  "Try to get a channel from source, return vector of channel's values."
  [source-chan]
  (go
    (let [ch (poll! source-chan)]
      (when ch
        (loop [val (<! ch) acc []]
          (if val
            (recur (<! ch) (conj acc val))
            (conj acc val)))))))

(dc/deftest build-drag-chan
  "Test drag event grouping"
  (testing "Drag event stream behaves correctly"
    (async done
           (go (let [drag-chan (testbed/build-drag-chan (get-input-chan))
                     drag1 (<! (consume-channel drag-chan))
                     drag2 (<! (consume-channel drag-chan))
                     drag3 (<! (consume-channel drag-chan))]
                 (is (= drag1 (first expected-drags)))
                 (is (= drag2 (second expected-drags)))
                 (is (= drag3 nil))
                 (done))))))

{{< /highlight >}}

I actually had a lot of trouble getting all that working. It started feeling
like my test code was going to need its own tests. I found two interesting
things out. First it appears `poll!` only works in a go block. Second was what
all that async non-sense was about in the cljs.test documentation. Turns out
this code doesn't work outside a go block, and adding a go block breaks the
tests. I went a ways down the rabbit hole trying to unwrap a channel before I
remembered reading about cljs.test/async (which was mentioned both in the
cljs.test docs, and again in the devcards testing docs).