+++
categories = ["development"]
tags = ["CSharp", "AspNetCore", "xUnit"]
date = "2016-06-13T20:49:22-07:00"
title = "Unemployed project - Testing"

+++

Getting a test config working, and a Github repo setup.
<!--more-->
<hr/><br/>
I spent most of the morning getting my data model in shape, which you can read
about at the end of the previous post. With that piece finally in place I
decided it was time to setup source control. I found a nice `.gitignore` file
[on Github](https://github.com/github/gitignore/blob/master/VisualStudio.gitignore)
that only needed a little tweaking. Then I added a very basic readme and a
license and pushed everything up to a
[new Github repo](https://github.com/kitsu/JobLogger).

Afterward, with my mess now hosted up where anyone can see, I decided to spend
some more time trying to get unit tests working. Referencing a
[blog post](https://ievangelistblog.wordpress.com/2016/02/12/asp-net-core-1-0-unit-testing/)
I found (with a
[Github project link](https://github.com/IEvangelist/Dnx.Xunit.Testing)),
the Xunit docs, and the Asp.net core
testing docs I was able to piece something together that finally worked. I think
the key was adding a "Class library (.NET core)" project for my tests, but I
also have a much better understanding of the `project.js` file after my previous
attempts.

[Here is a link](https://github.com/kitsu/JobLogger/commit/4b9ba7258277f1eb01215268a8c34f3d28de5a17)
to the change-set where I added Xunit. I also added one fairly trivial
controller test to confirm everything really worked.

After that I've just been working on adding content and features. I'm sure I'll
come up with more interesting things to post, but for now it is just so nice to
finally have things working.