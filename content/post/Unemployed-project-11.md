+++
categories = ["development"]
tags = ["CSharp", "AspNetCore", "Chutzpah", "testing"]
date = "2016-06-22T13:14:50-07:00"
title = "Unemployed project - Testing Typescript"

+++

Setting up Typescript unit testing in Visual Studio with Chutzpah.
<!--more-->
<hr/><br/>

    This ended up a little messy and scattered, I will try to come back and fix
    everything when I'm thinking clearer.

I am thinking about how to implement list filtering and searching. One option is
handling pagination or delayed loading via ajax. To support that I'll need to
handle piecewise log loading, but then I can just do the filtering on the
server and pass new log sets to the client on demand. The alternative is to load
*all* the user's logs up front and control log rendering with Javascript. That
approach would work out well if I decide to add offline mode using local
storage. I did a little research and using [Moment](http://momentjs.com/) I
think I can do the kind of date comparison I'll need to do in Javascript.

Either way I'll need to implement additional logic in Javascript, but I already
have more untested client code than I'm really comfortable with, so today I
spent some time getting JS testing up and running. As usual I ran into some
problems, but I got something working in the end.

I found a tool called [Chutzpah](https://github.com/mmanela/chutzpah) that
provides integration with Visual Studio in the form of two addons: a
[test runner](https://visualstudiogallery.msdn.microsoft.com/f8741f04-bae4-4900-81c7-7c9bfb9ed1fe),
and a [context menu](https://visualstudiogallery.msdn.microsoft.com/71a4e9bd-f660-448f-bd92-f5a65d39b7f0).
The tutorials I could find were all old, and the project's docs seemed to be
outdated in places too, but I can confirm it does work with VS2015 SP2. Also note
you do not need the Nuget package, or PhantomJS, or Jasmine (the testing
framework I chose).

The first problem I had getting Chutzpah working was entirely Visual Studio's
fault. To keep my main project clean I would have liked to put the JS testing
code in the separate testing subproject I already made for my C# tests. For
whatever reason though VS does not allow creation of arbitrary file types in a
project. Instead I am limited to the file types VS thinks are relevant to my
project. In the case of my testing project I am limited to only C# file types
and a few config file types.

I actually got quite a ways down the road creating everything manually in the
file system. The problem I saw looming though was how I could tie my
Typescript build into my test project. It felt really wrong to reach across
project boundaries like I would have needed to do to make that configuration
work. Instead I moved everything into a `test` subdirectory of my main project's
`scripts` directory. VS was much more accomodating in the context of an Asp.net
project too.

My next problem had to do with lack of relevant examples. As far as I can tell
no one has successfully setup Chutzpah in a Asp.net Core project. All I found
were stand-alone Javascript examples and a few unresolved questions regarding
Asp.net > v4 setup. Without something like RequireJS, and with your dependencies
living so far from your code, it wasn't clear how to get everything hooked up.

The first place I was mislead by the docs was concerning the need and
construction of an HTML file as a test harness. When using VS at least there is
no need for any harness file.

Next was the guidance on building Typescript files for testing. The method
referenced in the docs and used in the samples is to create a batch file to
compile your sources. Again, with a working VS Typescript setup this is totally
unnecessary.

Next was a very important peice of information that was *missing* from the
documentation: where to put the `chutzpah.json` configuration file. I assumed it
would be placed in the same folder as my source files, just like in all the
samples, and then it would be registered using the run tests context menu.

To this point I have never had the context menu entries do anything useful, and
in most cases they don't provide any indication whether they've done anything at
all.

In order for Chutzpah to do anything you need to have the `chutzpah.json` file
in the root of the project were your tests live.

Once I got that sorted out I had two failures in quick succession: first test
discovery ran on my entire project (finding dozens of tests in my `wwwroot/lib`
folder), then, after discarding irrelevant config files, test discovery found
nothing at all.

I spent a lot of time reading, and thinking, and tweaking my configs, but in the
end what helped me sort out my problems was a somewhat-hidden debug setting. If
in VS you check the Chutzpah options (Tools > Options > Chutzpah) there is an
`Enable Tracing` setting, and at the bottom of the dialog you'll see the
description: "Saves a trace of the Chutzpah execution to
%temp%\chutzpah.log". After enabling tracing and checking the log I could see
exactly where my config was broken.

While fixing my config I:

* pathed to the wrong root directory
* caused an exception in Chutzpah path resolution
* included all my tests twice

Through all that the only hint that anything was wrong was the trace log. IMO trace
output should be enabled by default, and should output to the VS output window.

Here is my final working config:
{{< highlight json >}}

{
  "Framework": "jasmine",
  "Compile": {
    "Extensions": [".ts"],
    "ExtensionsWithNoOutput": [".d.ts"],
    "Mode": "External",
    "UseSourceMaps": true
   },
  "References": [
    {
      "Includes": [ "*/scripts/*.ts",
        "*/wwwroot/lib/knockout/build/knockout-raw.js",
        "*/wwwroot/lib/knockout.mapping/knockout.mapping-2.4.1.debug.js",
        "*/wwwroot/lib/moment/moment.js" ],
      "Excludes": [ "*/scripts/*/*.d.ts" ]
    }
  ],
  "Tests": [
    { "Includes": [ "*/scripts/test/*.ts" ],
      "Excludes": ["*/scripts/test/*.d.ts", "*/wwwroot/*"] }
  ]
}

{{< /highlight >}}

Things to note:

* paths start with `*/`, without this none of my paths resolved
* compile.Mode is set to `External`, because VS builds my code
* my libraries are manually included, because I don't like the comment method

Now I can write my tests in Typescript and they all show up in the Test
Explorer. Running the test is also much quicker than the C# tests.