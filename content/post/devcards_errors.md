+++
categories = ["development"]
tags = ["code", "clojurescript", "lister"]
date = "2016-05-26T20:02:47-07:00"
title = "ClojureScript lows as low as the highs"

+++

Dependency resolution quirks bring progress to a halt, temporarily.
<!--more-->
<hr/><br/>
I started rolling all the code from the testbed into my actual application
today. getting started was easy enough, just copy in the project.clj and some
folders, fix up some paths and names. Porting my old JQuery code was easy, as
expected, and I was able to structure the code much nicer.

Once I had things rearranged and got everything to build I remembered one thing
I didn't try in the testbed. In order for my Python code to interact with the
Javascript VM I need to be able to call js functions. In advanced compilation
mode though all the functions and var names are mangled. There are two ways to
expose ClojureScript symbols in the js runtime:

{{< highlight clojure >}}

;; Export interop symbols on js global object (window)
(set! js/set_history set-history)

;; Add the :export metadata to the functions you want to access
(defn ^:export set-history
  "Set this page's filter history"
  [new-hist]
  (reset! history (js->clj new-hist)))

{{< /highlight >}}

The second method is recommended, but the names exported must be fully
qualified: `lister_cljs.filter.set_history`. Exporting on the window risks name
collisions, but it aligns closer to how I had things before. To test that all
works though I had to build everything together and ensure the symbols really
exist. That is where I ran into trouble.

When I tried to build with advanced compilation I got some warning, and then a
big green message saying the code was successfully compiled. When I check in the
app though I can't see the symbol I exported. I messed around for a while trying
to get anything to show up, until I noticed that *none* of my code was running.
In fact the body of the compiled Javascript file was an empty function!

You may have guessed, the warning I ignored was actually important. It was
actually an error reported by the Google Closure compiler. And while the
compiler died with the error the lein process continued to completion and
reported *"success"*. The error code was `ERROR:
JSC_CONSTANT_REASSIGNED_VALUE_ERROR. constant reactDOMserver assigned a value
more than once.` which was weird since I wasn't using any react in my code.

What followed was successively banging my head against the cljs compiler docs,
the lein-cljsbuild docs, the Google Closure compiler docs, and searching high and low
for similar problems. I did eventually find similar problems, but for some
reason Google was no help. I figured out the problem was a dependency collision,
and the only things I was using that involved React were Figwheel and
Devcards. Since I had the latest versions of both, and there were no documented
incompatibilities, I resorted to reading through old Github issues.

I found that Devcards had similar issues for other people, and confirmed that
removing my Devcards dep allowed the build to complete. The odd thing though was
that I don't use any of the Devcards code in my dev or min build profiles. I
expected the Google Closure compiler to just drop the unused code, but it seemed
to be loading all the test code too.

My first attempted solution was to separate the test code from the normal code,
and only add the test code path to the testcards build:
`:source-paths ["src" "test"]`. This had no effect. Because I had Devcards in my
project-wide dependencies map the offending code was loaded into every build. In
the end the final solution was so simple, but so painful to come up with -
Remove Devcards from the project deps and add it only to the testcards build:

{{< highlight clojure >}}

{:id "test"
 :source-paths ["src" "test"]
 :dependencies [[devcards "0.2.1-7"]] ;<-- This Here!
 :figwheel {:devcards true}
 :compiler {:main lister-cljs.test.core
            :asset-path "lib"
            :output-to "testcards/main.js"
            :output-dir "testcards/lib"
            :source-map-timestamp true}}

{{< /highlight >}}