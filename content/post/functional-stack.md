+++
categories = ["Development"]
tags = ["code", "functional", "python", "lister"]
date = "2016-05-15T18:47:44-07:00"
title = "Composing Function Compositions"

+++

Using a push down automata and functional programming IRL.
<!--more-->
<hr/><br/>
I'm still working on the same project as
[last time]({{< relref "unit-testing-01.md" >}}), but today I implemented the
*special filters* mentioned in the tests. The application lets users filter a
collection of data rows by typing into an input field. Normally users just type
space-separated bits of text that are permissively matched against a default
column. As an advanced feature though I wanted to let users make queries against
any column using regular expressions. My first attempt worked fine, but in
testing on a real world data set it became immediately apparent that a
single independent queries were not enough.

The original special syntax was `(column:pattern)`, and parsing was a simple as
grabbing patterns containing parens and a colon, strip parens, split on colon,
and compile the pattern as a regex. I actually performed the entire match+split
using a regex too. Matching was done by, given a row, checking if the column
existed, and if so returning whether the pattern found a match.

For the new syntax I wanted the user to be able to specify arbitrary
*combinations* of column:pattern pairs. With the old syntax the user was
basically defining sets, and so by adding boolean operators the user could define
unions, intersections, differences, etc. of those sets. After some thought I
decided the new syntax would be `(NOT <col:pat> AND <col:pat> OR <col:pat>)`
where any pair may be NOT'ed and every pair must be connected by AND/OR.

To accomplish this I had to build several parts. First I had to adjust the
parser to collect terms inside parens. As soon as I find a matching paren I
create a `Special` object that tries to build a matcher for the collected expression
and sets a flag on itself if successful. If not successful the parts are treated
as normal patterns (that happen to have a paren in them). The Special object is
replacing a namedtuple, and is effectively a typed data container.

Okay, on to the interesting part. I know that a pushdown automata is an easy way
to process arithmetic expressions. You just use a stack and based on the type of
each new input and the item on top of the stack you perform some action. My
actual code is a little messy (and gives away the next part of the story) so
here is a straight forward calculator example:

{{< highlight python >}}
"""PushDown Automata based arithmetic calculator."""
from operator import add, sub, mul, div
from functools import partial

# Mapping of operator tokens to operator functions
ops = {'+': add, '-': sub, '*': mul, '/': div}

# Executable error message templates
tail = ' in "{}"'
adj_ops_error = partial(("Error: adjacent ops '{}' & '{}'" + tail).format)
no_left_error = partial(("Error: no left operand for '{}'" + tail).format)
no_op_error = partial(("Error: no operator between {} and {}" + tail).format)
number_error = partial(("Error: cannot convert {} to int" + tail).format)

def eval(expr):
    """Attempt to left-fold expression."""
    tokens = expr.strip().split()
    if not tokens:
        return None
    stack = list()
    for token in tokens:
        if token in ops:
            if not stack:
                print no_left_error(token, expr)
                return None
            elif stack[-1] in ops:
                print adj_ops_error(stack[-1], token, expr)
                return None
            # Push op onto stack
            stack.append(token)
        else:
            try:
                token = int(token)
            except ValueError:
                print number_error(token, expr)
                return None
            # Branch based on top of stack
            if stack:
                top = stack.pop()
                if top in ops:
                    # Try to get a left operand to go with top op
                    if not stack:
                        print no_left_error(top, expr)
                        return None
                    left = stack.pop()
                    if left in ops:
                        print adj_ops_error(left, token, expr)
                        return None
                    # Apply operator and push result onto stack
                    stack.append(ops[top](left, token))
                else:
                    print no_op_error(top, token, expr)
                    return None
            else:
                # If stack is empty push operand (seed operand)
                stack.append(token)
    assert len(stack) == 1
    return stack.pop()

if __name__ == '__main__':
    assert eval("1 + 1") == 2
    assert eval("2 * 4") == 8
    assert eval("10 / 2") == 5
    assert eval("2 * 10 + 2 / 2") == 11
    assert eval("-2 * 2 * 4") == -16
    # Calculator REPL
    res = "Type a space-separated expression"
    while res:
        print res
        res = eval(raw_input())
{{< /highlight >}}

Adding sub-expression grouping isn't hard, parsing just gets trickier and you
need recursion or a helper function. A bigger problem with this example is that
everything is eagerly evaluated. Remember I am building a filter predicate that
is tested against rows of a table, so I don't know the values of my operands
until I have a row in hand. Instead of calculating a value what I really want is
the resulting expression tree. To accomplish that I used function composition
and Python's `functools.partial`. Here are my operators and base operand:

{{< highlight python >}}

# Operators
def NOT(matcher, row):
    return not matcher(row)

def AND(match1, match2, row):
    return match1(row) and match2(row)

def OR(match1, match2, row):
    return match1(row) or match2(row)

# Operand (leaf)
def matcher(column, pattern, row):
    if column in row:
        col = row[column]
        match = pattern.match(col)
        if match:
            return True
    return False

{{< /highlight >}}

Each operator accepts the current row as an argument, which is propagated down
to the bottom level of matchers. Now every item on the stack is either one of
`(NOT, AND, OR)` or a partial function waiting for a row. Here is how the
matchers and OP expressions are built:

{{< highlight python >}}

# Matcher leaf node
column, pat = part.strip('<>').split(':')[:2]
pattern = re.compile(pat, re.IGNORECASE)
match = partial(matcher, column, pattern)
stack.append(match)

# OPs become operands
...
if top == NOT:
    # Replace operand with not'ed operand
    operand = partial(NOT, operand)
elif top in (AND, OR) and stack:
    left = stack.pop()
    # Replace operand with op on left and operand
    operand = partial(top, left, operand)
...

{{< /highlight >}}

Once all tokens have been consumed (and the stack squished) without error the
result is a function taking a row dict and returning match/nomatch.
It works too, all tests pass, and exercising it in the UI behaves as expected.

### Edit

While I'm thinking about it I wanted to mention the prettiest *explicit*
Finit-Automata I've ever seen in Python. It was in an answer to a
[LeetCode question](https://leetcode.com/problems/valid-number/) involving
parsing a string and determining if it was a valid number. The problem is
trickier than it sounds, but so is
[this answer](https://leetcode.com/discuss/70510/a-simple-solution-in-python-based-on-dfa).
The author builds a state transition table as a list of dicts, where the state
is an index in the list, and transitions are determined by lookups in the
current state's map. I made a more verbose version while trying to understand
it, which is posted further down the thread. So terse, and such a simple looking
solution to a complex problem.

Additionally, 
[Here is a talk at this year's Clojure West](https://www.youtube.com/watch?v=GglfimrfYn4)
covering another interesting class of Automata and some applications.