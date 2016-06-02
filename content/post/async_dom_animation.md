+++
categories = ["development"]
tags = ["code", "clojurescript", "async", "lister"]
date = "2016-05-31T14:11:32-07:00"
title = "Home Grown DOM Animations"

+++

Using core.async to animate DOM properties.
<!--more-->
<hr/><br/>
Before I start, to be clear, this is a bad idea. CSS3 has a
[specification](https://www.w3.org/TR/css3-animations/) for defining
animations, please use it. In this case though I have a number of excuses for
rolling my own solution, and besides it was a fun exercise.

So previously when I needed to hide/reveal rows in my Lister application I used
JQuery's `slideUp` and `slideDown`, just because they were there. It did look
really nice though, and I wanted to keep the effect in my replacement
ClojureScript code. My first thought was to use an async go-loop to execute the
animation, but before going off on that tangent I had a look around for
pre-built libraries. Once again it looks like if I were using React my problems
would be solved, but otherwise I didn't find anything attractive.

I was able to eliminate CSS3 animations pretty quickly. First I wasn't
completely clear on how to apply the animation from code. More importantly the
version of Webkit in Qt4 doesn't support modern CSS, along with many other
things I am finding.

The first thing you need for animation is a source of timing events. In modern
web development you are advised to use `Window.requestAnimationFrame(callback)`,
so I started by writing a function to populate a channel with frame events. This
is actually pretty tricky - registered callbacks are only called once, so they
must re-register themselves in the body of the callback.
Here is what I came up with:

{{< highlight clojure >}}

(defn anim-source
  "A continually renewing requestAnimationFrame callback."
  [ch time]
  (put! ch time)
  (js/requestAnimationFrame (partial anim-source ch)))

(def frame-chan (let [ch (chan (sliding-buffer 1))]
                  (js/requestAnimationFrame (partial anim-source ch))
                  ch))
;
{{< /highlight >}}

This would have been fine if it had worked, but it turns out Qt4 Webkit also
doesn't have `requestAnimationFrame` or even `webkitRequestAnimationFrame`.
Minor setback. I searched around a little and found some advice from
old-fashioned HTML game development. If you use `setTimeout` with your expected
frame rate you can sort of simulate `requestAnimationFrame`. You don't get any
guaranty that your event will fire before a screen paint, but you can hope the
browser figures out what you want. Here is the revised code:

{{< highlight clojure >}}

(def fps (/ 1000 60))

(defn anim-source
  "A continually renewing timer simulating requestAnimationFrame callback."
  [ch]
  (put! ch (.now js/Date))
  (js/setTimeout (partial anim-source ch) fps))

(def frame-chan (let [ch (chan (sliding-buffer 1))]
                  (js/setTimeout (partial anim-source ch) fps)
                  ch))
;
{{< /highlight >}}

I am of the opinion that it would be preferable to wrap all this in a go-loop
that managed *all* queued animations on each tick. The way this works, having
only one frame source, with lots of animations some might be starved for
frames. Another option would be to use an async
[`mult`](https://clojuredocs.org/clojure.core.async/mult) with taps for each
animation. So far though this code has worked well enough.

Next I started writing the actual effect functions. Starting with `slide-up!` I
implemented the animation in a straight-forward way in a go-loop:

{{< highlight clojure >}}

(defn slide-up!
  "Animated hide."
  [elem duration]
  (let [height (dommy/px elem :height)
        start (.now js/Date)]
    (go-loop [now (<! frame-chan)]
      (let [elapse (- now start)
            percent (/ elapse duration)]
        (if (< elapse duration)
          (do (dommy/set-px! elem :height (* (- 1 percent) height))
              (recur (<! frame-chan)))
          (do (dommy/hide! elem)
              (dommy/remove-style! elem :height)))))))
;
{{< /highlight >}}

When I started writing the `slide-down!` effect though I started noticing some
common elements. I went through several passes factoring out the common code,
and I think I came up with something pretty neat:

{{< highlight clojure >}}

(def anim-time 250)

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

(defn slide-up!
  "Animated hide."
  ([elem] (slide-up! elem anim-time))
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

(defn slide-down!
  "Animated reveal."
  ([elem] (slide-down! elem anim-time))
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
;
{{< /highlight >}}

Each effect is really just a sentinel expression, and a map of functions passed
to the `animate` function. The individual effects are tied via Dommy to the DOM,
but the animate function is reasonably pure and somewhat testable. ~~I've tried
this out with nearly 2000 rows and it still looks reasonable, which is good
enough for me.~~

**Demo:**
<iframe src="/async_DOM_animation/index.html"
        width="100%" height="225px" style="border: 1px solid black"></iframe>

<br/>

### Edit - Corrections

I was finalizing my changes today, and I found that using the above code caused
some really odd behavior with long lists (1200+ rows). The total animation time
was the sum of all simultaneous animation durations, and Ii ran into a limit on
the number of blocking takes on one `async/chan` (1024 BTW). My first attempt to
convert to a `mult` caused my app to hang inescapably. The animations bothered
me more and more though, so I took another shot at it. Here is the revised code:

{{< highlight clojure >}}

(defn anim-source
  "A continually renewing timer simulating requestAnimationFrame callback."
  [ch]
  (put! ch (.now js/Date))
  (js/setTimeout (partial anim-source ch) fps))

(def frame-mult (let [ch (chan (sliding-buffer 1))]
                  (js/setTimeout (partial anim-source ch) fps)
                  (async/mult ch))) ; <- Only store the mult wrapping the channel

(defn animate
  "Generic animation go-loop."
  [duration {:keys [initialize! transition! finalize!]}]
  (let [frame-chan (chan) ; <- Channel created in each animation
        init (initialize!)
        start (.now js/Date)]
    (async/tap frame-mult frame-chan) ; <- Add tap to module-level frame-mult
    (go-loop [now (<! frame-chan)]
      (let [elapse (- now start)
            percent (/ elapse duration)]
        (if (< elapse duration)
          (do (transition! init percent)
              (recur (<! frame-chan)))
          (finalize!)))
      ;; This was initially outside the go-loop, resulting in *no* animation
      (async/untap frame-mult frame-chan))))
;
{{< /highlight >}}

Now all animations should update on each frame, and all the animations should
complete near where their duration elapses. There are still some visual
glitches, but without access to `requestAnimationFrame` that is to be expected.

As an additional optimization I factored the row hiding/showing code out of all
the exported functions. I then chose the function to use for hiding depending on
the number of rows acted upon - `slide-up!`/`slide-down!` if the count is less
than 100 else `dommy/hide!`/`dommy/show!`.