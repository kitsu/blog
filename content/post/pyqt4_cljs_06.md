+++
categories = ["development"]
tags = ["code", "clojurescript", "generative", "testing", "lister"]
date = "2016-05-25T17:02:54-07:00"
title = "ClojureScript in PyQt4 QWebView - More testing"

+++

Because testing is more fun than real work?
<!--more-->
<hr/><br/>
Okay, I really should have set this down and gotten on to integrating what I
learned into my actual application. I just had a couple of things I still wanted
to play with, and it is much quicker to use what I have on hand already working.

After getting devcards working yesterday I was really hating the long reload
time every time I wanted to see updated test results. So the first thing I
wanted was Figwheel working in test. It was really painless to add, but I ran
into some problems with `devcards/deftest`.

Specifically having a non-boolean result in an `is` expression breaks things,
and then Figwheel stops updating. Also having a macro in an `is` expression
breaks things, and then Figwheel stops updating. The problem on the test side
seems to be some missed error checking/macro expansion in devcards' replacement
`deftest` macro. The problem on the Figwheel side seems to be with React-id
resolution with broken macro code. It was fairly easy to work around these
problems though, and the code turned out a little cleaner with the workarounds
anyway.

The next thing I wanted to try was *clojure.test.check*, Clojure's generative
testing library. I figured I could replace my hard-coded test data with some
generator and get all the fancy minimum failing case analysis for free. That
didn't work out though. First problem was that I was piling too many unfamiliar
technologies with questionable interop all together. Second was that I couldn't
figure out how to get generator data shaped just how I wanted. Even if I got
something working I'm not sure how I would have structured the tests...

So instead I built some test data generators in plain old Clojure. And, as the
complexity of my test machinery approached the complexity of my actual code, I
threw in some test tests:

{{< highlight clojure >}}

(defn input-filler
  "Generate some number of pre-events chosen from #{move leave up}."
  [size init]
  (loop [cnt 0 acc []]
    (if (< cnt size)
      (recur (inc cnt)
             (conj acc (vector (rand-nth [:mousemove
                                          :mouseleave
                                          :mouseup])
                               (+ init cnt))))
      acc)))

(defn input-drag
  "Generate events starting with down and ending with #{up leave}"
  [size init]
  (loop [cnt 1 acc [[:mousedown init]]]
    (if (< cnt (dec size))
      (recur (inc cnt)
             (conj acc (vector :mousemove (+ init cnt))))
      (conj acc (vector (rand-nth [:mouseup :mouseleave])
                        (+ init cnt))))))

(defn input-data
  "Generate random data simulating an input stream."
  [evt-num evt-size]
  (loop [num evt-num
         id 0
         events []
         drags []]
    (if (> num 0)
      (let [pre (input-filler (rand-int 4) id)
            id (+ id (count pre))
            drag (input-drag evt-size id)
            id (+ id evt-size)
            post (input-filler (rand-int 4) id)]
        ;; repeat start/end generation evt-num times
        (recur (dec num)
               (+ id (count post))
               (concat events pre drag post)
               (conj drags (conj (mapv second drag) nil))))
      [events drags])))

{{< /highlight >}}

These functions build fake event streams like I had hard-coded previously. Next
I exercise them a little to validate the output. I did catch a number of
problems too:

{{< highlight clojure >}}

(dc/deftest build-test-data
  "###Testing random test data generation
   These are *meta-tests*, test to confirm the testing apparatus
   are working correctly."
  (testing "Test input-filler bounds"
    (let [pairs (input-filler 4 0)]
      (is (= (map second pairs) [0 1 2 3])
          "Event ids should be sequential starting from provided (0).")
      (is (empty? (clojure.set/difference (into #{} (map first pairs))
                                          #{:mousemove :mouseup :mouseleave}))
          "Filler events should be subset of expected."))
    (let [pairs (input-filler 4 5)]
      (is (= (map second pairs) [5 6 7 8])
          "Event ids should be sequential starting from provided (5).")
      (is (empty? (clojure.set/difference (into #{} (map first pairs))
                                          #{:mousemove :mouseup :mouseleave}))
          "Filler events should be subset of expected.")))
  (testing "Testing input-drag has correct shape"
    (let [pairs (input-drag 5 0)]
      (is (= (map second pairs) [0 1 2 3 4])
          "Event ids should be sequential starting from provided (0).")
      (is (= (first (first pairs)) :mousedown)
          "Drag must start with mousedown.")
      (is (contains? #{:mouseup :mouseleave} (first (last pairs)))
          "Drag must end with mouseup or mouseleave.")
      (is (= (set (map first (butlast (rest pairs)))) #{:mousemove})
          "Every event between first and last is a mousemove."))
    (let [pairs (input-drag 5 5)]
      (is (= (map second pairs) [5 6 7 8 9])
          "Event ids should be sequential starting from provided (5).")
      (is (= (first (first pairs)) :mousedown)
          "Drag must start with mousedown.")
      (is (contains? #{:mouseup :mouseleave} (first (last pairs)))
          "Drag must end with mouseup/mouseleave.")))
  (testing "Testing input-data is shaped correctly"
    (let [[events drags] (input-data 2 5)
          fdrag (first drags)
          sdrag (second drags)]
      (is (= (count drags) 2)
          "Produced drag count should match provided (2).")
      (is (= (map second events) (range 0 (count events)))
          "Event ids should be sequential starting from 0.")
      (is (= (first (nth events (first fdrag))) :mousedown)
          "Drag should start with mousedown.")
      (is (contains? #{:mouseup :mouseleave}
                     ;; event key of event at last non-nil index in first drag
                     (first (nth events (nth fdrag 4))))
          "Drag should end with mouseup/mouseleave.")
      (is (and (= (count fdrag) 6)
               (= (count sdrag) 6))
          "Drags length should match provided (5)."))))

{{< /highlight >}}

Then I modified the old test code to use the new random generation:

{{< highlight clojure >}}

(defn get-input-chan
  "Build fake mouse event stream channel."
  [data]
  (let [input-chan  (chan 12)]
    (go-loop [head (first data) tail (rest data)]
      (when head
        (>! input-chan head)
        (recur (first tail) (rest tail))))
    input-chan))

(dc/deftest build-drag-chan
  "###Testing drag event grouping"
  (testing "Drag event stream behaves correctly"
    (async done
           (go (let [[events expected-drags] (input-data 2 5)
                     drag-chan (testbed/build-drag-chan (get-input-chan events))
                     drag1 (<! (consume-channel drag-chan))
                     drag2 (<! (consume-channel drag-chan))
                     drag3 (<! (consume-channel drag-chan))]
                 (is (= drag1 (first expected-drags))
                     "First set of drag events should match expected.")
                 (is (= drag2 (second expected-drags))
                     "First set of drag events should match expected.")
                 (is (= drag3 nil)
                     "There should be no more drag events.")
                 (done))))))

{{< /highlight >}}

Code is up at
[https://github.com/kitsu/PyQt4_CLJS](https://github.com/kitsu/PyQt4_CLJS)
Commit:
[1e3b2627](https://github.com/kitsu/PyQt4_CLJS/tree/1e3b2627171de1190190281f37439852efb7262d).