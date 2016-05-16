+++
categories = ["Development"]
tags = ["Code", "Testing", "python", "lister"]
date = "2016-05-14T15:00:55-07:00"
title = "Testing and Thinking About Testing"

+++

Writing bad code differently?!
<!--more-->
<hr/><br/>
As part of my goal of changing my job title to *Developer* I've been on a
whirlwind tour of enterprise tools and practices. I am currently working through
the C# version of 'Uncle' Bob Martin's
[Agile Principles, Patterns, and Practices](https://www.amazon.com/dp/B0051TM4GI).
I just got through the bit where Bob does Test Driven Development (TDD) pair
programming with Bob Koss on a bowling score keeping application. It really
is a pretty picture of TDD - It was test first, and the design was shaped and
simplified by how the tests were framed.

I've never really done TDD, though it sounds like the idea. When I think about
testing a new project unit testing is what comes to mind. I learned unit testing
using Python and it's unittest module. At the time code coverage was also all
the rage. In Python everything is effectively public, and so you can easily
write tests even against code meant to be private. So when I think of tests I
think of deep thorough coverage, even into internals, which is hardly practical
when you don't have any code!

In the last few years I've watched a number of conference talks railing against
test-first, or encouraging people to write tests differently. The thing I've
been drawn to, and that I saw in Uncle Bob's example, was that you should "Write
tests against the interface rather than against the implementation".

I just happen to be making a major revision to a some code that had no tests,
so before I touched the code I worked on some tests. I resisted the urge
to start testing things in file order, and instead thought about what the
module's interface was. Since only a single function was ever used outside the
module I started with that.

{{< highlight python >}}
class Test_Filter(unittest.TestCase):
    """Test filter functions."""

    def test_filter_rows(self):
        """Testing table filtering."""
        # Setup
        config = {'default_filter_field':'num'}
        table = {0: {'num': 1, 'name': 'foo',},
                 1: {'num': 2, 'name': 'bar',},
                 2: {'num': 3, 'name': 'baz',},
                 3: {'num': 4, 'name': 'spam',},
        }
        # Test simple single filter
        fltr = '3'
        res = filter_rows(fltr, config, table)
        self.assertEqual(res, [2],
                         msg="Expected row indices [2], got {}".format(res))
        # Test simple filter on missing value
        fltr = '9'
        res = filter_rows(fltr, config, table)
        self.assertEqual(res, [],
                         msg="Expected empty result, got {}".format(res))
        # Test multiple simple filters (order and whitespace invariant)
        err = "Filter '{}', expected [0, 2], got {}"
        for fltr in ('3 1', '3   1', '1 3', '1  3', '1 3  1', '3 1 3', '1 1 3'):
            res = filter_rows(fltr, config, table)
            self.assertEqual(res, [0, 2],
                             msg=err.format(fltr, res))
        # Test Special filters...
{{< /highlight >}}

As I got ready to write the next set of tests though I was thinking about a talk
I watched the other day [Unselfish Testing by Jay Fields](https://www.youtube.com/watch?v=f9eu4mMOtN4)
where he made some good points about test naming and organization. He recommends
grouping tests into self contained methods which are actually named for what
they are testing. I was at an obvious conceptual break, so I refactored a little:

{{< highlight python >}}
# Details elided...
class Test_Filter(unittest.TestCase):
    """Test filter functions."""

    def test_simple_filters(self):
        # Setup
        config = {'default_filter_field':'num'}
        table = {...}
        # Test single filter
        fltr = '3'
        res = filter_rows(fltr, config, table)
        self.assertEqual(res, [2],
                         msg="Expected row indices [2], got {}".format(res))
        # Test searching to missing value
        ...
        # Test multiple filters (order and whitespace invariant)
        ...

    def test_special_filters(self):
        # Setup (duplicated from above)
        # Test special filter
        ...

    def test_mixed_filters(self):
        # Setup (duplicated from above)
        # Test combinations of simple & special filters
        ...
{{< /highlight >}}

All three tests exercise the same `filter_rows` function, but are using
different features (which use different helper functions/classes in the
module). Now I can change my implementation without changing my tests, and when
things fail I have more information about what exactly is broken. What I don't
have is verification of my backing logic.

I guess I'm okay with that. I can test my code in the repl somewhat like in
Clojure, or make disposable tests just for development. If I keep everything
pure and immutable there shouldn't be any weird interactions. Besides I don't
know what an error looks like that can't be expressed through the public
interface?

**Bonus cool:** Hugo's Markdown renderer is called *BlackFriday*, and hugo exposes
  [some settings](https://gohugo.io/overview/configuration#configure-blackfriday-rendering)
  under a section of the same name. In particular Markdown by default replaces
  normal quotes with *smart* quotes, which cause problems and weren't rendering
  correctly for me. Here is the code to disable them:
  
{{< highlight ini >}}
# In config.toml
[blackfriday]
    smartypants = false
{{< /highlight >}}