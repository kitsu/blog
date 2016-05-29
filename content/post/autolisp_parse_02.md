+++
categories = ["hacks"]
tags = ["cad", "autolisp", "code", "python"]
date = "2016-05-28T11:04:15-07:00"
title = "Analyzing Autolisp with Python - Parse"

+++

Woo, yet another weak parser implementation - continued.
<!--more-->
<hr/><br/>
My plan was to use `collections.namedtuple` to collect data for different types
of syntax. So for example a function definition would have a name, arguments,
locals, and a body. Once the tree of nested expressions was built I would be
able to distinguish different expression types by their *types*. Under the hood
namedtuple creates a class with `__slots__` for its members, so
`isinstance(foo, Defun)` will return True if foo was created from the Defun
namedtuple.

In the end though the only namedtuples were these:

{{< highlight python >}}

Number = namedtuple('Number', 'value')
String = namedtuple('String', 'value')
Symbol = namedtuple('Symbol', 'value')

{{< /highlight >}}

The rest of my expression data types started growing methods to segregate
different sub-parsers. I eventually pulled the body of the parse function into a
`Program` class that represents an entire lisp file:

{{< highlight python >}}

class Program(object):
    """Base matcher."""
    def __init__(self, tokens):
        self.children = list()
        self.consume(tokens)

    def consume(self, tokens):
        """Consume tokens to build children"""
        body, tokens = consume_body(tokens)
        self.children = body
        if tokens:
            raise SyntaxError("Unmatched ')' in input.")

    def __repr__(self):
        return "Program({})".format(self.children)

{{< /highlight >}}

The shared parsing code are pulled out as consumer functions, most importantly
`consume_body`. Its job is to grab and parse everything inside a pair of
parens. It returns the fully parsed tree of children and the un-consumed tokens:

{{< highlight python >}}

def consume_body(tokens):
    """Consume all expressions until unpaired closing bracket ')' found."""
    children = list()
    while tokens and not tokens[0] == ')':
        # These are in order of specificity
        if Defun.matches(tokens):
            fn = Defun()
            children.append(fn)
            tokens = fn.consume(tokens)
        elif Apply.matches(tokens):
            ap = Apply()
            children.append(ap)
            tokens = ap.consume(tokens)
        elif List.matches(tokens):
            li = List()
            children.append(li)
            tokens = li.consume(tokens)
        else:
            # First token must be one of Symbol, String, Number
            other, tokens = consume_other(tokens)
            children.append(other)
    # Omit last head if it exists (i.e. ')')
    return children, tokens[1:]

{{< /highlight >}}

Each of the composite expression types implements a classmethod `matches` that
looks at the head of the token list and decides if it can consume that
input. If none of the compound expressions match then a catchall function
handles data literals:

{{< highlight python >}}

def consume_other(tokens):
    """Handle Symbol, String, and Number tokens."""
    first = tokens[0]
    try:
        num = float(first)
        return Number(num), tokens[1:]
    except ValueError:
        pass
    if '"' == first[0]:
        return consume_string(tokens)
    return Symbol(first), tokens[1:]

{{< /highlight >}}

Strings require special handling because they can contain anything. I'm actually
loosing all the case and whitespace information in the strings, but that's
fine. I may also still mis-interpret a few special cases, but it didn't cause
problems in the output I was looking for:

{{< highlight python >}}

def consume_string(tokens):
    """Consume tokens between double-quotes."""
    first, rest = tokens[0], tokens[1:]
    if first.count('"')%2 == 0 and '\\"' not in first:
        return String(first.strip('"')), tokens[1:]
    parts = [first]
    while rest:
        first, rest = rest[0], rest[1:]
        parts.append(first)
        quotes = first.count('"') - first.count('\\"') + first.count('\\\\"')
        if quotes%2 == 1:
            break
    else:
        print tokens
        raise SyntaxError("Reached end of input without matching quote.")
    whole = String(" ".join(parts).strip('"'))
    return whole, rest

{{< /highlight >}}

I'm not going to go through every class, but Apply is simple and
representative. An Apply represents a function call, and contains only the
function name and a body:

{{< highlight python >}}

class Apply(object):
    """Match function application."""
    def __init__(self):
        func = None
        body = None

    def __repr__(self):
        return "Apply({0.func}):\n{0.body}".format(self)

    @classmethod
    def matches(cls, tokens):
        """Match '(symbol body)."""
        return tokens[0] == '(' and not tokens[1] == '('

    def consume(self, tokens):
        self.func = tokens[1]
        body, tokens = consume_body(tokens[2:])
        self.body = body
        return tokens

{{< /highlight >}}

In the next and final installment I'll cover the "analysis" phase, and the
result of my survey.