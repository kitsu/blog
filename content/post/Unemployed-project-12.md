+++
categories = ["development"]
tags = ["CSharp", "AspNetCore", "Azure"]
date = "2016-06-30T18:27:41-07:00"
title = "Unemployed project - Published"

+++

Primary features finished, app published to Azure!
<!--more-->
<hr/><br/>
I've been working steadily adding features to my app. I added filtering by week,
interactive search, enabled ssl and added Google & Microsoft Sign in, added
email log reports, and sharable links. It was a lot of work, and I did run into
some problems, but nothing seemed interesting enough to write about. After all
that work, and after a short struggle with Azure itself, my app is now live at
https://joblogger.azurewebsites.net.

{{< highlight text >}}
JobLogger is an application for maintaining job search logs per the Washington
State requirements for receiving unemployment benefits. The logs are not
submitted with benefits claims, but they are subject to audits and must be
provided upon request up to a year after benefits are received. My application
maintains logs in a database, and can share logs either by email or as a hyperlink.
{{< /highlight >}}

I decided to load all log data upfront in the client, and to skip pagination and
demand loading for now. Having all the data in the client made filtering and
searching really easy thanks to Moment and Knockout. I would still like to use
local storage, but the current setup works for now.

Enabling ssl was pretty trivial, and adding Google sign in was really easy.
Adding Microsoft sign in was also easy... eventually. The key is that their sign
in service is only accessible on one somewhat hidden page:
https://apps.dev.microsoft.com. Once there you can get an app id and secret just
like from Google. I also tried adding LinkedIn sign in, but there is no official
library. I know just enough about OAuth2 to know I don't want to implement my
own solution.

Adding email reporting was actually a little interesting. I started by following
[this tutorial](https://docs.asp.net/en/1.0.0-rc2/security/authentication/accconfirm.html)
on enabling email account verification. About half-way down the page though is a
little note stating "SendGrid does not yet support .NET Core". After a little
research on the SendGrid site it seems they have a new API, and a
[C# library on Github](https://github.com/sendgrid/sendgrid-csharp). I got it
working after a little trouble, the key was quote handling per
[this Github issue](https://github.com/sendgrid/sendgrid-csharp/issues/245).

The sharable link was both really simple, and really complicated. All I needed
was a user id and some log ids and I could pull the report together with a
LINQ. It turns out MVC Core controllers don't databind list arguments, so I had
to parse the log ids myself. I also wanted to put the user's name on the report,
but accessing user info added some dependencies that made testing hard, mocking
a `ApplicationDbContext : IdentityDbContext<ApplicationUser>` is really
complicated. Repository pattern to the rescue. One extra layer of abstraction
decouples my controller enough for testing. Also it seems there is no easy way
to use Razor templates to produce strings, so I had to roll my own templates.

I found Azure's web interface really confusing. At first I wasn't even sure how
to setup a simple web app. I ended up using my Github repo for deployment, which
has good and bad points. My first attempt didn't go well though. First my app
wouldn't build (which was my fault), then when I got it built the deployment
failed. It seems it always fails the first time, but a retry sorted that
out. From there I spent hours trying to debug a code 500 - Internal server error.
Everything checked out fine, the build was good, the server was fine, but I
couldn't get anything to serve. It turns out to be a common Azure problem, and
the solution is to just delete the web app instance and rebuild it.

My app is currently just hosted on the free app service tier. I did have to add
a basic SQL database for $4.99 a month. Thus far I have spent about $2.00 of my
$200.00 Azure credit just getting things setup. At this burn rate I'll get about
3 months of hosting. I've added Google AdSense banners in case I get any traffic.

### Edit - TOS

I just re-read the Terms Of Service: the $200.00 credit expires after one
month. Since it will go away if I don't use it I've cranked all my service plans
up a few notches. I do have some Windows developer credits too, so I can keep
the service running past the end of the month, but only if I turn the burn rate
back down. Hopefully I will remember...