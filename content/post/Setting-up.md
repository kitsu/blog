+++
categories = ["Blog"]
tags = ["meta"]
date = "2016-05-13T11:20:23-07:00"
draft = false
title = "Setting Everything Up"

+++

In which I setup Github hosting, Disqus Comments, Google Analytics and AdSense.
<!--more-->
<hr/>

After spending some time yesterday customizing the
[Polymer](http://themes.gohugo.io/polymer/) theme I changed my mind and switched
to a theme called [Geppaku](http://themes.gohugo.io/hugo-theme-geppaku/).
Both the style and the structure of Geppaku are simpler, which makes it easier
to understand and change. I darkened the colors a little, and added some
accents, but the Geppaku code is mostly unchanged.

**First cool thing:** Geppaku uses something called
[Font Awesome](http://fontawesome.io/) for its icons. I had to customize the
images that came with Polymer, but with FA I had everything I needed.

With the visual styling basically setup I had to turn to the technical -
actually getting this thing online. I'm still not that confident in how to setup
the Git and Github parts though, so instead I played around with some
extras. Hugo claims to support easy integration with Google Analytics and Discus
Comments, and the Geppaku theme comes with Google AdSense integration (sorry).

**Second cool thing:** Signing up for
[Google Analytics](https://www.google.com/analytics/) and
[Disqus Comments](https://publishers.disqus.com/engage) were both easy.
With that account info I just added a couple strings to my Hugo config and
everything worked. [AdSense](https://www.google.com/adsense/) on the other
hand seems to have some (lengthy) approval process, so I slapped some dummy
templates in until I get the AdSense code.

With nothing else left to futz with I had to decide how to push to Github. By
now I've read all the Hugo Github tutorials at least a couple of times. Most of
the material is on creating *project pages* which use a specially named branch
for content. I'm going with a normal *personal page* though, which just serves
from the root of the main branch. I would prefer to keep the whole thing in one
repo, but any way I come up with just seems awkward. Instead I'm setting
up one [repo](https://github.com/kitsu/blog) of the source code, and a
[sub-repo](https://github.com/kitsu/kitsu.github.io) for the content.

I might still try to automate building/pushing the site, but at this point I've
spent far too long getting this working.

### Edit

Well, the deploy didn't go as smooth as it could have. It seems I was using a
really old version of Git (MSYS Git 1.7.1), and something had broken since the last time I pushed
to Github. Every time I tried to push I got `fatal: authentication failed` or
worse. The solution was to install the latest
[Git for Windows](https://git-for-windows.github.io/) which comes with real auth
support.

As a result though some paths got broken, the Git vim became default and broke
my vimrc, I then broke Vundle when doing `:BundleUpgrade`. It was a cluster, but
everything is working now.

On the AdSense front - They did in fact get to my application same day. They
promptly rejected it because the site wasn't up yet. Which works out really well
if you were waiting to setup AdSense before going live<span>..</span>.

I did test the site on a few computers, my Note III, and an old Samsung
tablet. It adapts well enough, but the main div gets crowded on the right margin
on narrow screens.
