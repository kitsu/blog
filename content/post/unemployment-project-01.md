+++
categories = ["development"]
tags = ["CSharp", "AspNetCore"]
date = "2016-06-07T18:30:21-07:00"
title = "Unemployment Project - Setup"

+++

Picking a project based on desirable qualifications and my current situation.
<!--more-->
<hr/><br/>
I finished my unemployment application and read the unemployment guide this
morning. I also applied for several more local developer positions. It seems the
sweet-spot for developers in my area lies somewhere between C# and the web, with
asp.net figuring prominently in many postings.

Here in Washington there are a number of odd conditions to receiving
unemployment benefits. For instance your application doesn't go into effect
until Sunday of the week you applied, which is *great* since I applied on a
Monday. You also have to apply to at least three jobs a week (or perform
alternative activities) and file a report stating that you did so. You don't
actually have to tell them what jobs you applied for, but you are expected to
keep a detailed log, and you are subject to random audits. You also have to
go through the whole process the first week without getting paid, you know -
just for fun.

Almost the entire process can be taken care of online. The one notable exception
is the job search log. You are expected to keep a log, but the only resource they
provide is a non-editable pdf, which you are presumably supposed to print and
fill out manually. The form is pretty simple, it has three sub-forms for each
event, check boxes for the type of event, and some additional fields to fill in
given the chosen type. You aren't actually required to use their form though,
and I was planning on just doing something in plain text or maybe emacs
org-mode. When I looked at the form though the thought occurred to me that it
would make a wonderful little application.

My immediate impulse was to write something in Clojure, maybe using the
[Hoplon](https://github.com/hoplon/hoplon) stack. Given the disposition of local
job offerings though I know it would be more beneficial to get some experiance
using the Microsoft tech stack. I thought for a moment about building a desktop
app using xaml+WPF, but web tech just has so much going for it that I can't
justify writing something that isn't served as html (except maybe a Xamarin app).

So to warm up for the project I started with some research. I read some primer
material on asp.net, found a promising tutorial I intend to follow, and started
watching some free Lynda training included with my LinkedIn premium trial. It all
seems pretty trivial, some basic routing, an html templating language, and
Microsoft's take on an ORM. I also read up on "T-SQL" because it kept popping up
in postings - it is just MS flavored SQL. I also watched some videos on
TypeScript and read the
[entire handbook](https://www.typescriptlang.org/docs/handbook/basic-types.html). Seems
like a nice choice for a compiles-to-JS language if you are comfortable with C#
and want to stick close to future ECMAscript standards.

So hopefully by this time tomorrow I'll have the beginnings of a logging
application, even if it's just a styled cshtml file and a boilerplate
project. If I can get something usable this week I'll try pushing it up to Azure
for hosting. It will be fun getting auth and analytics and all the other website
things working for myself.