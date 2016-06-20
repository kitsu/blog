+++
categories = ["development"]
tags = ["CSharp", "AspNetCore", "xUnit", "code"]
date = "2016-06-17T17:08:03-07:00"
title = "Unemployed project - Serialization"

+++

A Entity Framework serialization issue solved by data sanitization.
<!--more-->
<hr/><br/>
I'm right in the middle of the conversion from serving pages to serving Json. I
was just testing out my post-load ajax request for log data when I found that my
log list wasn't getting serialized correctly. I was getting an array with only
one log item. I tried wrapping the list in an object to no effect, I also
included the list count to confirm what I wasn't getting. I did find though that
I could build a query for log ids and the result serialized correctly.

One troubling thing I *did* notice on the entry that was successfully
transfered: it included the ApplicationUser reference. The user only had an
empty LogList member, but I would still prefer scrubbing that info somehow.
The presence of the user data turned out to be good hint at what the real
problem was.

While searching for similar problems I found a relevant
[StackOverflow post](http://stackoverflow.com/a/34836837/770443). The key though
was actually the related questions in the sidebar - the top question was "Json
and Circular Reference Exception". When I saw that I suddenly realized what the
problem was: the serializer was following the link from the log to the user
which then had the same log as the first element in it's log array.

Unlike the poster of the linked question though I wasn't getting an error. In
fact I don't get any errors or exceptions. I spent some time earlier today
trying to find the "break on exception" setting in VS, but as far as I could
tell I already have all reporting enabled. It would be really helpful if
exceptions were at least reported, especially in debug mode...

Anyway, after adding `ReferenceLoopHandling.Ignore` to my startup.cs file per
the above post everything works as expected. I still have the problem with the
user being included though, and now it includes *all* the user object
information! Not good. With a little more research I found the `[JsonIgnore]`
attribute, which successfully removes the user, and also solves the reference
loop problem:

{{< highlight csharp >}}

public abstract class BaseLog
{
    public Guid Id { get; set; }

    // Reciprocal link to user data (not serialized!)
    [JsonIgnore]
    public virtual ApplicationUser ApplicationUser  { get; set; }

    [Required]
    [Display(Name = "Log Date")]
    [DataType(DataType.Date)]
    public DateTime LogDate { get; set; }

    [Required]
    [StringLength(256)]
    public string Description { get; set; }
}

{{< /highlight >}}
