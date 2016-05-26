+++
categories = ["development"]
tags = ["code", "clojurescript", "pyqt4", "lister"]
date = "2016-05-20T15:51:30-07:00"
title = "ClojureScript in PyQt4 QWebView - Success"

+++

Leiningen is the best, and Qt is... challenging.
<!--more-->
<hr/><br/>
There, it works. Code is up at
[https://github.com/kitsu/PyQt4_CLJS](https://github.com/kitsu/PyQt4_CLJS)
Commit:
[8bc889e6](https://github.com/kitsu/PyQt4_CLJS/tree/8bc889e641dc90205018e8ba9598ec374521ef16).

The lein setup was super easy, aided by the fact I started with
`lein new figwheel pyqt4_testbed -- --reagent`. First I had to muck with the
path values to get everything output into the `testbed` folder (where the Python
code lives and the QWebView loads resources):

{{< highlight clojure >}}
...
  :clean-targets ^{:protect false} ["testbed/lib"]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]

                :compiler {:main pyqt4-testbed.core
                           :asset-path "testbed/lib"
                           :output-to "testbed/main.js"
                           :output-dir "testbed/lib"
                           :source-map-timestamp true}}
...
{{< /highlight >}}

Trying to run the dev build still didn't work because of the QWebView path
resolution problem, but I'll come back to that. The advanced build did work
except for one small problem: Reagent didn't load, and Figwheel couldn't
connect to its server. I am pretty sure this is again some QWebView resource
restriction, and is probably pretty easy to resolve, just add some "schemes" to
the QWebSettings or something. Since I wasn't that attached to either though I
chose the easier solution: deleting them completely from the project definition.

So yeah, paths, I thought with my new knowledge of the `baseUrl` argument it
would be quick to sort out. That wasn't really the case. Firstly debugging
_where_ exactly webkit was actually looking is a pain. The only place I found
that information was in the inspector resources tab when hovering over the html
file:

{{< figure src="/images/QWebView_inspect_path.JPG" alt="Hover path snip"
caption="No, the title bar text wasn't (always) the same" >}}

From there I had some trouble getting the base path and the resource paths I was
hard-coding to work out. Because of how things worked previously I was being
overly specific. In fact I even had a helper function to *url-ize* resource
paths for the HTML tags, which foreshadows the final solution.

Once I had the right path components in the right places nothing would load at
all! It turned out `QUrl` is "helpful", and sanitizes paths it is
provided. This included removing the ever annoying and oh-so important Windows
drive colon. Since I didn't have a 'c' folder mounted at root '/' nothing
worked. While researching that issue I learned QUrl has a static method just for
that purpose: `QUrl.fromLocalFile(QString path)`. Adding that I promptly got
paths like "file://file://blah". After dropping my url-izer though everything
worked! I could load main.js and get goog/base and all my JS deps resolved.

Final working `setHtml` call:

{{< highlight python >}}

def build_page(self, headers, data):
    """Build table html containing data rows."""
    table = html.doclist(headers, data)
    styles = self.get_styles()
    scripts = self.get_scripts()
    doc = html.html(styles + scripts, table, cls='noselect')
    basepath = os.path.abspath(os.path.dirname(__file__))
    self.setHtml(doc, QtCore.QUrl.fromLocalFile(basepath))

{{< /highlight >}}

Go ahead and play with the code. Remember to kill your `cljsbuild auto` before
trying to build an exe. Next time I'll try to get some cool async event code
working.