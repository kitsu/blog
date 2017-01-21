+++
categories = ["development"]
tags = ["CSharp", "SharePoint", "Rest"]
date = "2017-01-21T12:16:09-08:00"
title = "Playing with the SharePoint Rest APIs"

+++

By way of starting from the outside I try talking to SharePoint via Rest.
<!--more-->
<hr/><br/>
So one good thing about my new job is that they have an expectation that you
will be pursuing training as part of your regular duties. In my case in
particular, with my being new and unfamiliar with much of their tech, I have a
large amount of time allocated to training.

After going through some more general SharePoint training on PluralSight I
started digging into how I can interact with SharePoint programmatically. There
are actually a number of ways available: the old Server-side Object Model
(SSOM), the newer Client-side Object Model (CSOM) and it's JavaScript
counterpart (JSOM), and the very new Rest API. There are some trade-offs picking
any one of these: the SSOM is effectivly depreciated, JSOM requires running in a
served page due to cross origin issues, CSOM is a dotnet assembly that simulates
the SSOM in many ways, and the Rest API doesn't yet have complete feature coverage.

For my first experiments I chose the Rest API, mainly because I like the Rest
idea, and have written some Rest-like code before. I also feel like it is a
little lower-level and puts you in more direct contact with SharePoint's inner
workings. Also knowledge of the Rest API is reusable in deployed JavaScript code
and AFAIK the CSOM/JSOM are primarily hitting the Rest endpoints internally too.

My first attempt didn't go well. I tried at first to create a plain HTML page
and use JQuery to access an internal data source. I expected IE to handle the
auth, and hoped being on the domain would exempt me from cross-origin issues,
but it didn't. Interestingly renaming my HTML to an HTA (Hyper Text Application)
allowed me to circumvent the same-origin rule, but MicroSoft has depreciated
HTAs.

Here is the complete HTML and Javascript:
{{< highlight html >}}

<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="X-UA-Compatible" content="IE=edge">
        <meta http-equiv="content-type" content="text/html;charset=UTF-8" />
        <title>Company Search</title>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.2.4/jquery.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/knockout/3.4.1/knockout-min.js"></script>
        <script src="CompanySearch.js"></script>
    </head>
    <body>
        <h1>Company Search</h1>
        <div id="Content">
            <ol data-bind="foreach: items">
               <li data-bind="text: $data"></li> 
            </ol>
        </div>
    </body>
</html>

{{< /highlight >}}

{{< highlight javascript >}}

// IIFE wrapper
(function() {
    var baseUrl = "http://--omitted--/companies/_api/web"

    var ViewModel = function() {
        this.items = ko.observableArray([]);
        this.addItem = function(item) {
            this.items.push(item);
        }.bind(this);
    };

    $(document).ready(function() {
        // Grab the page content container and build a new ViewModel and bind them.
        var container = $("#Content")[0]
        var vm = new ViewModel();
        ko.applyBindings(vm, container);

        // Kick-off a ajax request to populate the ViewModel.
        $.ajax({
        url: baseUrl + "/lists/getByTitle('companies')/items"
                     + "?$select=Title&$top=20",
            method: "GET",
            headers: {"Accept": "application/json;odata=verbose"}
        })
        .done(function(data) {
            var res = data.d.results;
            for (var i=0;i < res.length; i++) {
                vm.addItem(res[i].Title);
            }
        })
        .fail(function() {vm.addItem("REST request failed!")});
    })
})();

{{< /highlight >}}

I was really looking forward to making some nice interactive graphical UIs for
results view. As a fallback though I moved to a C# console application, mainly
for ease of development. It seems there is some disagreement about which Dotnet
library is best for consuming Rest services. In the end I went with `System.Net`
with `HttpWebRequest`s, which are a little awkward but seemed to work well enough.

After my first successful tests I started factoring out some common code, and
also created a couple of little applications for different things. Without Git
on my work machine though I lost my initial work as it evolved into other
things.

One thing I did play with was using async/await for all the network code, Which
is sort of interesting since console apps don't have a mainloop/UI thread. There
are two places where asynchrony is meaningful in this context: First the ability
to cancel pending requests (i.e. on ctrl-c break), second when starting multiple
blocking requests. It takes a little machinery to setup an async-cancelable
console app. You need to create a cancellation token, and you need a separate
async method since you can't make the entry-point async.

{{< highlight csharp >}}

private static int Main(string[] args)
{
    var source = new CancellationTokenSource();
    // This binds to the break event (ctrl-c)
    Console.CancelKeyPress += (s, e) =>
    {
        e.Cancel = true;
        source.Cancel();
    };

    try
    {
        return MainAsync(args, source.Token).GetAwaiter().GetResult();
    }
    catch (OperationCanceledException)
    {
        return 1223; // Cancelled
    }
}

private static async Task<int> MainAsync(string[] args, CancellationToken token)
{
    // Make async calls...
    Console.ReadLine();
    return 0; // Success
}

{{< /highlight >}}

The next issue was that unlike most FooAsync methods
`HttpWebResponse.GetResponseAsync` doesn't have an overload that accepts a
cancellation token. That is fixed easily enough with an extension method (a
helper method would be fine too):

{{< highlight csharp >}}

// HttpWebResponse.GetResponseAsync with cancellation support
public static class ExtensionMethods {
    public static async Task<HttpWebResponse> GetResponseAsync(this HttpWebRequest request, CancellationToken token)
    {
        using (token.Register(() => request.Abort(), useSynchronizationContext: false))
        {
            try
            {
                var response = await request.GetResponseAsync();
                return (HttpWebResponse)response;
            }
            catch (WebException ex)
            {
                // WebException is thrown when request.Abort() is called,
                // but there may be many other reasons,
                // propagate the WebException to the caller correctly
                if (token.IsCancellationRequested)
                {
                    // the WebException will be available as Exception.InnerException
                    throw new OperationCanceledException(ex.Message, ex, token);
                }

                // cancellation hasn't been requested, rethrow the original WebException
                throw;
            }
        }
    }
}

{{< /highlight >}}

I started out with just a method making GET requests along-side the main method,
but as things evolved I ended up with a little collection of request helper
methods. These aren't super reusable, they mainly reflect what I needed at the
time. Also the error handling is not very helpful, and response handling could
be factored out into its own method too:

{{< highlight csharp >}}

public static class RestHelpers
{
    public static async Task<string> GetJsonResponseAsync(Uri target, CancellationToken token)
    {
        var req = WebRequest.Create(target) as HttpWebRequest;
        req.Credentials = CredentialCache.DefaultCredentials;
        req.Accept = "application/json;odata=verbose";

        try
        {
            var resp = await req.GetResponseAsync(token);
            using (var reader = new StreamReader(resp.GetResponseStream()))
            {
                return await reader.ReadToEndAsync();
            }
        }
        catch (WebException)
        {
            return "";
        }
    }

    // Post with no body (used to get bearer token)
    public static async Task<string> PostJsonResponseAsync(Uri target, CancellationToken token)
    {
        var req = WebRequest.Create(target) as HttpWebRequest;
        req.Method = "POST";
        req.ContentLength = 0;
        req.Credentials = CredentialCache.DefaultCredentials;
        req.Accept = "application/json;odata=verbose";

        try
        {
            var resp = await req.GetResponseAsync(token);
            using (var reader = new StreamReader(resp.GetResponseStream()))
            {
                return await reader.ReadToEndAsync();
            }
        }
        catch (WebException)
        {
            return "";
        }
    }

    // Post with body
    public static async Task<string> PostJsonResponseAsync(Uri target, string contentType, string content, string bearer, CancellationToken token)
    {
        var data = Encoding.ASCII.GetBytes(content);
        var req = WebRequest.Create(target) as HttpWebRequest;
        req.Method = "POST";
        req.ContentType = contentType;
        req.ContentLength = data.Length;
        req.Headers.Add("X-RequestDigest", bearer);
        req.Credentials = CredentialCache.DefaultCredentials;
        req.Accept = "application/json;odata=verbose";

        using (var stream = req.GetRequestStream())
        {
            stream.Write(data, 0, data.Length);
        }

        try
        {
            var resp = await req.GetResponseAsync(token);
            using (var reader = new StreamReader(resp.GetResponseStream()))
            {
                return await reader.ReadToEndAsync();
            }
        }
        catch (WebException)
        {
            return "";
        }
    }

    // Post/Patch
    public static async Task<string> PatchJsonResponseAsync(Uri target, string contentType, string content, string bearer, CancellationToken token)
    {
        var data = Encoding.ASCII.GetBytes(content);
        var req = WebRequest.Create(target) as HttpWebRequest;
        req.Method = "POST";
        req.ContentType = contentType;
        req.ContentLength = data.Length;
        req.Headers.Add("X-RequestDigest", bearer);
        req.Headers.Add("X-HTTP-Method", "PATCH");
        req.Headers.Add("If-Match", "*");
        req.Credentials = CredentialCache.DefaultCredentials;
        req.Accept = "application/json;odata=verbose";

        using (var stream = req.GetRequestStream())
        {
            stream.Write(data, 0, data.Length);
        }

        try
        {
            var resp = await req.GetResponseAsync(token);
            using (var reader = new StreamReader(resp.GetResponseStream()))
            {
                return await reader.ReadToEndAsync();
            }
        }
        catch (WebException)
        {
            return "";
        }
    }

    public static async Task<string> GetBearerTokenAsync(string target, CancellationToken token)
    {
        var uri = new Uri(target + "_api/contextinfo");
        var json = await PostJsonResponseAsync(uri, token);
        var data = JObject.Parse(json);
        return (string)data.SelectToken("$.d.GetContextWebInformation.FormDigestValue");
    }
}


{{< /highlight >}}

I am unsurprisingly using Json.Net for json processing. I've played with both
XPath style data access and object mapping style, but haven't developed a
preference. Here is an example of pulling paged data from a large list:

{{< highlight csharp >}}

private static string apiUri = "_api/lists/getbytitle('companies')/items";

// Yes that is an awkward method signature
private static async Task<List<KeyValuePair<string, bool>>> GetStatusListAsync(CancellationToken token)
{
    var queryUri = "?$select=Title,RegulatoryStatus";
    var uri = new Uri(Endpoints.PreCompanies + apiUri + queryUri);

    var nameAndStatus = new List<KeyValuePair<string, bool>>();
    string title, stat;
    JObject data;
    string next;
    var json = await RestHelpers.GetJsonResponseAsync(uri, token);
    while (!string.IsNullOrWhiteSpace(json))
    {
        data = JObject.Parse(json);
        foreach (var d in data.SelectToken("$.d.results"))
        {
            Console.Write(".");
            title = (string)d["Title"];
            stat = (string)d["RegulatoryStatus"];
            nameAndStatus.Add(new KeyValuePair<string, bool>(
                title.ToLower(),
                stat.ToLower() == "active"));
        }
        next = (string)data["d"]["__next"];
        if (string.IsNullOrWhiteSpace(next))
            break;
        uri = new Uri(next);
        json = await RestHelpers.GetJsonResponseAsync(uri, token);
    }
    return nameAndStatus;
}

{{< /highlight >}}

I needed an ordered list of pairs for subsequent processing steps, and
`KeyValuePair` IMO has a nicer interface than generic `Tuple`s, at least in
C#. The items endpoint provides records a hundred at a time by default with an
href pointing to the next page of results. Although it does require extra
processing the paging is convenient for accessing lists that would otherwise be
throttled by SharePoint due to result size.

At this point I've written several of these little utilities for misc tasks I've
been working on, including posting files and modifying existing items. They have
worked well, but at this point I would recommend the CSOM if you are writing
C#. It was a fun experiment though, and gave good practical experience with
SharePoint's Rest implementation.

Due to a requirement of my next task I had to use the SSOM, and I decided to
finally dig into PowerShell, but I'll cover that next time.
