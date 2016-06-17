+++
categories = ["development"]
tags = ["C#", "AspNetCore", "xUnit"]
date = "2016-06-16T20:33:04-07:00"
title = "Unemployed project - Progress"

+++

Slow but steady progress toward a usable application.
<!--more-->
<hr/><br/>
I haven't had much to write about because things have been going so smoothly. At
this point I have several frontiers I can push forward on. I can play with the
back end code - models, repos, controllers. I now have testing working, so I can
make progress jumping back and forth between tests and implementation. And I
have the front end code which I can tweak endlessly.

I did discover one interesting detail the other day that is worth mentioning.
When I started writing tests for the LogListController I ran into a problem - I
couldn't access the `JsonResult` values to confirm behavior. The problem was
that I was using adhoc objects to define the json, which are marked internal by
default. In order to access them in my tests (which are in a separate
sub-project) I had to make the tests a *friend assembly* to my main project.

As usual all the information on the Internet is either wrong or outdated. The
guidance I found was that I just needed to add an attribute to my
`properties > AssemblyInfo.cs` file. It seems though that an Asp.net Core
application doesn't have assembly info. Visual Studio also wouldn't let me
create any files under properties. Even when I manually created the file with
the correct settings my tests still wouldn't run... until they did. I tried
several times before getting the builds to go off in just the right way so

[Here is a link](https://github.com/kitsu/JobLogger/blob/e8e0436140e67edfd369942793a8bbed3cc3920f/src/JobLogger/Properties/AssemblyInfo.cs)
to the file in question.

The other thing I've been doing is refining the UI. I just decided today to
scrap all the *LogList* pages I've built and replace them with a single unified
page. Since I was already using ajax for all my interactions it felt really
silly to have them in different places. The initial motivation was that the edit
forms for existing logs were going to be complete ripoffs of the add log form. I
also wanted to reuse the Knockout models I built for the add page on the list
page. I also couldn't figure out what to do upon successful submission on the
add page. Now I don't have any of those problems.

My design has basically morphed into a Single Page Application (SPA), modulo a
few ancillary pages. I am seriously debating having Knockout run the entire
front end, and making the back end primarily serve json to ajax requests. There
is no point building all the elements and Javascript code in Razor when I still
have to support live updates in the client anyway. Just push all the rendering
logic to the client and serve data ala-cart.
