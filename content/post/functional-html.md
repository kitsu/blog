+++
categories = ["development"]
tags = ["code", "functional", "python", "lister"]
date = "2016-05-18T14:43:46-07:00"
title = "Generating HTML on the fly in Python"

+++

More like _data as code_ than _code as data_.
<!--more-->
<hr/><br/>
Due to questionable advice from a random online article last night I decided to
sign up for LinkedIn. As can be expected I've accomplished very little of use
since then. As painful as it is, cranking through all the work experience and
self promotion stuff, it is also pretty cool to play celebrity chaser and follow
people like [Rich Hickey](https://www.linkedin.com/in/richhickey)
or [Erik Demaine](https://www.linkedin.com/in/erik-demaine-88aab35).

Anyway, the plan today was to implement a click & drag selection mode in the
document list of my application *Lister*. I did find some
[awkward JQuery](https://jsfiddle.net/kitsu_eb/agsb0h86/) code on
[StackOverflow](http://stackoverflow.com/questions/22550424) that I could fix
enough to be usable, but I've been thinking about moving my JS code (all 200
lines) over to ClojureScript. Then I could use core.async to sanely manage the
app state across events. I could also clean up a lot of the ugliness I've let
live just because its Javascript. I'm not sure it will be worth it to add
another (big!) dependency and additional build steps though. I'll probably try a
smaller experiment first and see how it goes.

As a supplement for today though I'll show off this little module I wrote
to do HTML generation. The main widget in Lister is a QTabWidget containing
one [QWebView](http://doc.qt.io/qt-4.8/qwebview.html) widget per tab. Since I
didn't want to include an extra library to populate those web views I rolled my
own super simple generator instead. It is inspired by libraries
like [Hiccup](https://github.com/weavejester/hiccup)
and [Hoplon](https://github.com/hoplon/hoplon),
and takes advantage of the Python syntax to do most of the heavy lifting.

The key function is `tag`, which produces an arbitrary tag containing its args
as its innerHTML and kwargs as attributes:

{{< highlight python >}}

from functools import partial
from itertools import chain

self_closing = ["area", "base", "br", "col", "command", "embed", "hr", "img",
                "input", "keygen", "link", "meta", "param", "source", "track",
                "wbr"]

def tag(kind, *args, **kwargs):
    """Generic tag-producing function."""
    kind = kind.lower()
    result = list()
    result.append("<{}".format(kind))
    for key, val in kwargs.items():
        if key in ('kind', '_type'):
            key = 'type'
        elif key in ('klass', 'cls'):
            key = 'class'
        result.append(" {}=\"{}\"".format(key, val))
    if kind in self_closing:
        result.append(" />")
    else:
        result.append(">{}</{}>\n".format("".join(args), kind))
    return "".join(result)

{{< /highlight >}}

(Note that no attempt is made to sanitize or encode anything)

I then use that to build some basic tags as partial functions, and some
specific convenience functions:

{{< highlight python >}}

# Misc simple tags
style = partial(tag, 'style')
div = partial(tag, 'div')
span = partial(tag, 'span')

# Form related tags
label = partial(tag, 'label')
textbox = partial(tag, 'input', _type='text')
button = partial(tag, 'button', _type='button')

# Helper functions
def html(head='', body='', **body_attrs):
    """Root document element."""
    return tag('html', tag('head', head), tag('body', body, **body_attrs))

def css(url):
    """A css link tag."""
    return tag('link', href=url, rel='stylesheet', _type='text/css')

js = partial(tag, 'script', _type='text/javascript')
def script(url=None, script=None):
    """A script tag."""
    if url:
        return js(src=url)
    elif script:
        return js(script)

{{< /highlight >}}

From there I build some application specific template functions:

{{< highlight python >}}

# Doclist filter input controls
docfilter = div("&nbsp", label('Filter:'), "&nbsp",
                textbox(id='filter_input', autofocus='autofocus'),
                button("Clear", id='filter_clear'),
                id='docfilter')

bar = span('|', cls='bar noselect')

def cell(idx, contents):
    """Create html representing a cell in a row."""
    return span("&nbsp{}".format(contents),
                cls="col{} noselect".format(idx))

def add_bars(data):
    """Transform column data into cell tags, and add separating bars."""
    cells = [(cell(i, c), bar) for i, c in enumerate(data)]
    cells = list(chain(*cells))
    cells.pop() # Remove last vertical bar
    return cells

def row(idx, data):
    """Create html representing a row of cells."""
    cells = add_bars(data)
    # Setup row classes
    classes = "row noselect"
    if idx%2 == 1:
        classes += " zebra"
    return div(*cells, id="row{}".format(idx), cls=classes)

def header(columns):
    """Create html for column headers."""
    cells = add_bars(columns)
    return div(docfilter, div(*cells, cls='noselect'), id='header')

def doclist(headers, data):
    """Create a complete doclist from headers and 2d data."""
    rows = [header(headers)]
    rows.extend([row(i, r) for i, r in enumerate(data)])
    return div(*rows, id='doclist')

{{< /highlight >}}

A lot of this is messy, and not necessarily how I would do this in a web page,
but since it is hidden inside a desktop application I did what was expedient.

You can see the full code
[as a Gist here](https://gist.github.com/kitsu/7e25aee3ee95e904af6e406e56827642).