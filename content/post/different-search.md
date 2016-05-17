+++
categories = ["blog"]
tags = ["hugo", "web"]
date = "2016-05-16T20:52:05-07:00"
title = "Optimizing a Different Filter"

+++

More fun with Hugo, Go templates, and Google services.
<!--more-->
<hr/><br/>
So after re-applying to AdSense I got accepted into phase 1, which means I can
log in, build ad blocks, and maybe get verified. Basically you get some
placeholder code to put on your page that Google will use to verify your
site. It seems odd to me that your site has to be up before they'll give you
code to include in your site to verify you can show ads on your site. I don't
know what the exploit is where you put ads on a page that doesn't exist...

Anyway, I keep writing every day, and I've posted every day too, though I
didn't make that a goal. Given what I have so far I'm not sure I would want
anyone reading this stuff though. I am at least trying to add something useful
in every post, even if they aren't well written or perfectly accurate. Like John
Sonmez said in [that video]({{< relref "hello-hugo.md" >}}) (paraphrasing):
trust in the process, and measure yourself against your old self. I trust that by
writing, and thinking about writing, I will get better at writing. And likewise
the rest.

Since I (theoretically) have ads, and in case I accidentally write something
useful, I guess I should work on making this blog more visible. To accomplish
that I've signed up for
[Google's Search Console](https://www.google.com/webmasters/tools/home), which
involved putting a provided document on my site to prove I had write access...
I'm also reading this
[SEO Guide](http://static.googleusercontent.com/media/www.google.com/en//webmasters/docs/search-engine-optimization-starter-guide.pdf)
I found while reading the Google help pages. Hugo takes care of most of the
stuff they advise, but the template I chose didn't include a description tag,
which leads me to the content portion of this post.

Creating the meta-description tag seemed easy, but I broke my pages dozens of
times trying to get it right. The idea is to add a tag to the html head tag of
the form:

{{< highlight go >}}
<meta name="Description" content="Something descriptive about *this page*">
{{< /highlight >}}

My first lingering mistake was trying to close the meta tag (i.e. `</meta>`).
Chrome failed in interesting ways until I sorted that out. My next problem was
getting something relevant into the content string. For the landing page I
wanted a generic description of the blog, but for posts I wanted the post
summary. I tried using the trick the theme's author used in the title tag:

{{< highlight go >}}
<title>{{ if not .IsHome }}{{ .Title }} | {{ end }}{{ .Site.Title }}</title>
{{< /highlight >}}

I had trouble figuring out how to put the result in quotes though. To debug I
tried the substitutions with static strings. This is what ended up working:

{{< highlight go >}}
<meta name="Description" content={{ if not .IsHome }}"Away"{{ else }}"Home"{{ end }}>
{{< /highlight >}}

From there I had trouble getting variables to show up as strings. I wanted to
add the site wide description to the "config.toml" file (rather than hard code
it in html). I had problems getting a value out of it though. I think I was
having a name collision when I named my var `Description`. Also it seems you
can't put custom vars in the top-level site definitions? lastly it seems that
Go/Hugo up-cases the first letter in identifiers, so the section `[params]`
could only be accessed as `.Params`. Heres how the working config looks:

{{< highlight ini >}}
...
[params]
    Desc = "My site-wide description here."
...
{{< /highlight >}}

Even with that working though I ran into one more problem. The Hugo server was
throwing a bunch of errors about `summary is not a field of struct type
*hugolib.Node in partials/header.html`. The home page and posts were working
fine, but something was still wrong. After re-reading a bunch of the Hugo
template docs I found two variables: `.IsNode` and `.IsPage` with the property
that when one is true the other is false. My guess is that the RSS, sitemap.xml,
and anything else non-html is classified as a node. A little more funky syntax
and it all works:

{{< highlight go >}}
<meta name="Description" content={{ if and (.IsPage) (not .IsHome) }}"{{ .Summary }}"{{ else }}"{{ .Site.Params.Desc }}"{{ end }}>
{{< /highlight >}}

Now search engines will know a little more about my site, and hopefully produce
better looking search results.