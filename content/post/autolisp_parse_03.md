+++
categories = ["hacks"]
tags = ["cad", "autolisp", "code", "python"]
date = "2016-05-29T07:41:30-07:00"
title = "Analyzing Autolisp with Python - Analyze"

+++

Woo, yet another weak parser implementation - conclusion.
<!--more-->
<hr/><br/>
So far I've taken a list of lines from a file and turned it into a single long
list of tokens, then taken those tokens and broken them up into nested
collections based on their context. I'll start this time by looking at the
results of parsing a few expressions, then describe how I extracted information
from those results.

Given a simple lisp expression:

{{< highlight lisp >}}

(SETQ foo "bar")

{{< /highlight >}}

It is first broken into:

{{< highlight python >}}

['(', 'setq', 'foo', '"bar"', ')']

{{< /highlight >}}

The parser then matches the outer expression to an `Apply`, foo to a `Symbol`,
and "bar" to a `String` with the result:

{{< highlight python >}}

Apply(func='setq',
      body=[Symbol('foo'), String('bar')])

{{< /highlight >}}

Now on a larger example:

{{< highlight lisp >}}

;; Internet example
(defun TEST ( / ANG1 ANG2)
 
	     (setq ANG1 "Monday")
	     (setq ANG2 "Tuesday")
 
	     (princ (strcat "\nANG1 has the value " ANG1))
	     (princ (strcat "\nANG2 has the value " ANG2))
	   (princ)
	);defun

{{< /highlight >}}

It has all kinds of random case, whitespace, blank lines, and trailing comments,
but the tokenizer cleans all that up:

{{< highlight python >}}

['(', 'defun', 'test', '(', '/', 'ang1', 'ang2', ')', '(', 'setq', 'ang1', '"monday"', ')', '(', 'setq', 'ang2', '"tuesday"', ')', '(', 'princ', '(', 'strcat', '"\\nang1', 'has', 'the', 'value', '"', 'ang1', ')', ')', '(', 'princ', '(', 'strcat', '"\\nang2', 'has', 'the', 'value', '"', 'ang2', ')', ')', '(', 'princ', ')', ')']

{{< /highlight >}}

Next the tokens are parsed into an object tree:

{{< highlight python >}}

Defun(name='test', params=[], locals=['ang1', 'ang2']
      body=[Apply(func='setq', body=[Symbol('ang1'), String('monday')]),
            Apply(func='setq', body=[Symbol('ang1'), String('tuesday)]),
            Apply(func='princ', body=[Apply(func='strcat',
                                           body=[String('\nang1 has the value '),
                                                 Symbol('ang1')])]),
            Apply(func='princ', body=[Apply(func='strcat',
                                           body=[String('\nang2 has the value '),
                                                 Symbol('ang2')])]),
            Apply(func='princ', body=[])])

{{< /highlight >}}

Run over an entire lisp source file you get a forest of these deeply nested
data structures. There is a lot of information there, but it isn't very
accessible. Since it is a data structure though I could write a program to
process it further. For every file I processed I created a stats object using
the following code:

{{< highlight python >}}

Stats = namedtuple('Stats', 'funcs, deps, strs, files')

def analyze(tree):
    """Collect info about program tree."""
    extensions = set(('.dcl', '.lsp', '.mnr', '.dvb', '.mnl', '.vlx'))
    stats = Stats(set(), set(), set(), set())
    exprs = tree.children[:]
    while exprs:
        expr = exprs.pop()
        body = None
        if isinstance(expr, Defun):
            stats.funcs.add(expr.name)
            stats.deps.update(expr.deps)
        elif isinstance(expr, String):
            val = expr.value
            stats.strs.add(val)
            if val[-4:] in extensions:
                stats.files.add(val)
        if hasattr(expr, 'body'):
            exprs.extend(item for item in expr.body)
    return stats

{{< /highlight >}}

This is basically a depth-first search through the program to collect a few
pieces of data I was interested in, namely defined functions and their
dependencies, and any strings - especially strings that look like file
names. There is an accompanying function that then outputs the stats. Here is
the output for the function above:

{{< highlight python >}}

Functions:
        test
Dependencies:
        princ
        setq
        strcat
Strings:
        monday
        tuesday
        \nang2 has the value
        \nang1 has the value
Possible file dependencies:
None

{{< /highlight >}}

Now for the disappointing part.

My plan was to use this information to build a
dependency graph, to identify any missing functions, and to make another pass
through the CUI files to identify lost functions. I was also going to compile a
set of used and unused resource files. I was then going to create a new lisp
file as the single entry point that managed all the loading and resource
resolution.

I had spent a couple of days at this point looking through things and building
this tool. I decided before proceeding I should give the people in the original
email a status report. I let them know that I could fix the things that *were*
broken, and that I had a firm grasp of what they had, but also that it was going
to take some effort, and a lot of what was there was old and irrelevant.

And with that the project evaporated.

I did hear back over a week later that someone else was "taking care of it", and
received thanks for my help, the visible total of which was one email reply...

Anyway, I put the code up 
[here as a Gist](https://gist.github.com/kitsu/1184b3caf4c209d67ab3f481de104dc3)
if anyone is interested.