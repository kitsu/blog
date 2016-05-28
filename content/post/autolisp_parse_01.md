+++
categories = ["hacks"]
tags = ["cad", "autolisp", "code", "python"]
date = "2016-05-27T18:48:46-07:00"
title = "Analyzing Autolisp with Python - Setup"

+++

Woo, yet another weak parser implementation.
<!--more-->
<hr/><br/>
I'm having a lot of fun porting my old JQuery code to ClojureScript, inspite of
my setbacks. It's nothing very exciting though, so I'm going to write about
something else today. A few weeks ago I was asked by Corp. CAD IS to have a look
at some *stuff*. The stuff was a zip file containing the accumulated AutoCAD®
customization files of an entire firm we acquired recently. The vague task I was
given was to "convert" the files to be compatible with the latest version of
AutoCAD.

The former firm had a CAD standards document that enumerated some of the tools
they had collected, but there wasn't any obvious structure to their collection,
or any central entry point. Since they *had* built a CUIX file I decided that was
the best place to find out what they were actually using. In case you didn't
know, a CUIX file is really just a zip file full of CUI files (xml), images, and
other resource files. The naming convention seems odd, but it aligns with all
the OpenOffice convention (doc -> docx, xls -> xlsx, etc. which are also zipped
XML files).

I started by just browsing some of the XML, and I noticed an interesting
pattern: most of the command macros had the form roughly:
`(if (not c:foo) (load "some.lsp"));foo` which has the effect of demand loading
a lisp file when the user tries to use a command. While I had recently been
using the Python ElementTree XML library to process AutoCAD tool palettes on
another task, for this I just wrote a script to dump any contiguous string
containing ".lsp". I didn't need a parser because I wasn't making modifications
to, or trying to understand the structure of, the CUI files.

I then wrote another script to copy all lisp files in the found list into one
directory (used), and report on any missing files, and copy everything else to
another (unused). About half of the lisp files they had were not *apparently*
used. There still existed the possibility that the unused lisps were libraries
required by the used lisps though.

I decided I needed to have more information about the contents of the lisps, but
there were dozens in each folder ranging in length from a few lines to
hundreds. I decided that I would just write a script to analyze the contents of
all the lisps. First I looked around online for a Python module to either do the
parsing for me or at least make building a parser simple. My main problems where
that I only wanted specific information, and I didn't need the full parse
results. Everything I found was either too complex, or didn't suit my needs.

So I decided to write my own parser. It's only lisp, how hard could it be?

Honestly it wasn't that hard. It probably isn't that good either, but it did
what I wanted. I wrote it in a few hours across two days time, while doing other
work. In this post I'll just show the tokenizer portion, since the parser (like
this post) ran a bit long.

I started out "tokenizing" just by reading a line at a time, doing some text
replacements, then splitting on whitespace:

{{< highlight python >}}

substitutions = (
        # Expand quote literal to quote fn
        ("'(", "(quote "),
        # Expand parens with white-space for splitting
        (')', ' ) '),
        ('(', ' ( '),
        )

def modify(line):
    """Perform replacements listed in global dictionary."""
    for k, v in substitutions:
        if k in line:
            line = line.replace(k, v)
    return line

def tokenize(infile):
    """Cheaty str.split tokenization."""
    tokens = list()
    # Clean and append all lines into one big string
    for line in infile:
        # Strip empty lines and comments
        line = line.strip()
        if not line or line[0] == ';':
            continue
        # Tokenize the line
        tokens.extend([token for token in
                       # AutoLisp is case-insensitive
                       modify(line).lower().split()
                       if token])
    return tokens

{{< /highlight >}}

It was surprisingly fast, and worked well enough on simple examples, but made
garbage out of real code. The problem was that comments don't just appear on
lines by themselves. They can, and in typical Autolisp style usually do, appear
at the ends of lines. The comment character in lisp is the semi-colon `;`, which
is also sometimes used in text. But people also sometimes put text quotes in
comments (especially when commenting out code). What I needed was a more
sophisticated way to pre-process each line to remove _all_ comments.

I built the following function to do the job. Sophisticated would be an
over-statement, but it does use one cute trick. There are three cases I want to
cover:

1. The line is empty or starts with a `;` - skip line
2. The line is non-empty and has a `;` but no quote - trim tail including `;`
3. The line contains quotes, and one or more `;` - do special stuff

Case 2 can actually also handle quotes on the condition that the last quote is
before the first `;`. For special stuff I first break the line into parts on
each `;`, then starting with the first part collect parts until all quotes are
matched. The trick is given an example line:
`(foo "evil;string" "an;other") ; badness` it is broken into
[`(foo "evil`, `string" "an`, `other")`, `badness`]. We start with one quote in
the first part, then add two in the second giving 3 (which is not even). As soon
as the number of quotes does become even whatever is left is the comment.

{{< highlight python >}}

def scrub_comment(line):
    """Carefully clean comments from line."""
    if not line or line[0] == ';':
        # Just skip blank lines and comments
        return
    semi = line.find(';')
    if semi > 0:
        if '"' not in line or line.rfind('"') < semi:
            # Keep everything up to but not including semi
            line = line[:semi]
        else:
            # Find left most semi not in a string literal...
            parts = line.split(';')
            first, rest = parts[0], parts[1:]
            accepted = [first]
            quotes = 0
            while rest:
                quotes += first.count('"')
                # If we have collected an even number of quotes on left
                if quotes%2 == 0:
                    break
                first, rest = rest[0], rest[1:]
                accepted.append(first)
            # Put back all the semis that belonged there
            line = ';'.join(accepted)
    return line

def tokenize(infile):
    """Cheaty str.split tokenization."""
    tokens = list()
    # Clean and append all lines into one big string
    for line in infile:
        # Modified line filter
        line = scrub_comment(line.strip())
        if not line:
            continue
        # Tokenize the line
        tokens.extend([token for token in
                       # AutoLisp is case-insensitive
                       modify(line).lower().split()
                       if token])
    return tokens

{{< /highlight >}}

Don't worry, the "parser" implementation has even more
[wat](http://knowyourmeme.com/memes/wat).