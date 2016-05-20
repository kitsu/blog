+++
categories = ["development"]
tags = ["clojurescript", "pyqt4", "lister", "fail"]
date = "2016-05-19T10:24:12-07:00"
title = "ClojureScript in PyQt4 QWebView - False start"

+++

Life is hard when you don't follow directions.
<!--more-->
<hr/><br/>
I've started working on a testbed for CLJS embedded in a QWebView, creatively
named "Testbed". I actually made quite a bit of progress, but I've decided to
scrap most of it. First though I'll break down what worked, my stupid mistakes,
and what I'm going to do instead.

The first step of the process was relatively easy. I just grabbed the most of
the Lister table widget and slapped it in a generic QMainWindow, and copied the
build script and spec file. Formatting the dummy data and stubbing out config
values were probably the hardest part.

Getting the ClojureScript build setup correctly on the other hand was much
harder than I expected. A lot has changed over the past months of ClojureScript
development. Creating a minimal ClojureScript build is actually much simpler
than when I last played with it. Several dumb mistakes, and the weird container
I'm shoe-horning it into, really tripped me up though.

I've always used [Leiningen](http://leiningen.org/) for Clojure, and when I
first tried ClojureScript
[lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild) was the only
practical choice. ClojureScript recently had its version synced with Clojure
though, and grew the ability to eval and compile its own code. With all the
recent changes I figured I should start fresh with the new
[Quick Start Guide](https://github.com/clojure/clojurescript/wiki/Quick-Start).
If (unlike me) you manage to follow the directions everything works as
advertised BTW.

So Clojure is hosted on the JVM, and it has inherited some traits from its host,
and ClojureScript kept many of them. Due to mandates Java file structure Clojure
expects the shape of your file-system to match your namespace hierarchy. I
forgot this at first though and just threw my CLJS file in with all my Python
code. Using the Quick Start build instructions raised no errors, and produced an
output Javascript file. All the output was just boiler plate though, and no
executable code was produced. It wasn't until I tried advanced compilation that
I got an error message.

Even after making sub-folders and renaming my file the build still didn't
work. The other peculiarity of the file to namespace parity is that Clojure
prefers hyphens in code, while requiring _under scores_ in the
file-system. Which I knew, and which was clearly stated in the Quick Start.

The next problem I ran into was getting the generated file and all the library
output to load correctly. I played around with my working directory and the
build function's `:output-dir` and `:asset-path` settings, but nothing worked.
I'm pretty sure it had to do with how the embedded WebKit browser was resolving
resource paths. ~~Judging from a
[StackOverflow search](http://stackoverflow.com/search?q=%5Bqwebview%5D+resources)
it seems like it is a common problem with QWebKit (Note [QWebKit is being
replaced in Qt5](http://doc.qt.io/qt-5/portingguide.html)).~~
Edit: [looks like](http://doc.qt.io/qt-4.8/qwebview.html#setHtml) I needed to
provide a second argument to `setHtml` of `baseUrl=QUrl("File://")` so local
resources resolve. Regardless, advanced compilation to the rescue again - when
the only output is the file you're already including manually it is pretty easy
to get right.

And with that it all worked, even when complied to an Windows exe. After
overcoming all that trouble though it just took one small thing to send me
running back to Lein. At the very bottom of the Quick Start is a short section
on *Dependencies*, and after a short preamble about
[CLJSJS](http://cljsjs.github.io/) (which is awesome)
and how to require another namespace in your code, they reveal the default
stand-alone solution for ClojureScript dependency management: String
concatenation onto the build command...

Seriously? Lein, Clojars, Maven, the deps management story in Clojure was one of
my favorite features. I totally expected this magic stand-alone `CLJS.jar` to
just have that stuff baked in. Just one more vector in the build map. Not to
diminish the current state of the magic jar - Getting started with ClojureScript
is so much easier and better now. But, unless you have very limited goals, Lein
(or Boot) should be your first stop after hello world.

So that is my plan for tomorrow. Setup a Lein project using some template
(Figwheel?), and then integrate my Python code into a Lein project instead. I
bet I can make a pretty cool PyInstaller task for Lein too.