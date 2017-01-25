+++
categories = ["development"]
tags = ["PowerShell", "SharePoint"]
date = "2017-01-23T10:54:32-08:00"
title = "SharePoint PowerShell Update Script"

+++

Using PowerShell to make silent updates to a list.
<!--more-->
<hr/><br/>
[Last time]({{< relref "playing-with-sharepoint-rest-api.md" >}}) I wrote about
using SharePoint's Rest API from a C# console app to interact with SP
lists. About the same time as I was experimenting with that I also started a new
assignment: to circumvent the SharePoint
[list view threshold](https://support.office.com/en-us/article/Manage-large-lists-and-libraries-in-SharePoint-b8588dae-9387-48c2-9248-c24122f07c59)
in an older application where the users had started bumping up against the
limit.

My solution after some debugging was to add an additional column to the document
library that was causing the problem. The column would be indexed, and would
mirror the value of a lookup (since even indexed lookups still break the limit).
I had to make a few code changes to maintain this internal field where documents
are added/copied, but it was pretty simple. I also needed a way to maintain the
existing data, i.e. a way to populate the new column for all existing items.

My first draft of the column migration was another console Rest app which walked
the rows copying IDs to the new column. There were some issues getting that all
to work, but in the end I had something that would copy the necessary
data. When I was checking the updated documents though I noticed a problem: all
the documents now had their modified data set to me and now, and they all had a
new version added. Not the end of the world, in production the user doing the
update would be something like `system`, and future documents would be fine. It
*would* have made existing document metadata useless though.

As I proceeded through testing and deployment to the dev server I was also doing
research on the side on this issue. There is actually a Rest method with a flag
for disable version creation, but I'm not sure it would have worked in this
case. I was mostly resigned to the solution I had when I found a
[blog post](http://www.sharepointsapiens.com/blog/update-item-document-properties-without-changing-modified-modified-fields-using-sharepoint-powershell/)
that demonstrated how to disable meta/version update using SSOM through
PowerShell.

Through all the years it has been available I have never used PowerShell. Years
ago I read about it and thought it was an interesting idea: lifting the
string-oriented process communications in Unix/Posix to the object level. I
always had Python or Batch script available though for problems big and
small. I never found a reason for an intermediate-level (Windows specific)
scripting language. Also PowerShell is not a very lovely language IMO. I seem to
have developed a strong dislike for languages that require variable prefixes
somewhere.

This fix was just too good to miss though, so I spent some time going through
video training and messing about, and then adapted the above to my problem:

{{< highlight powershell >}}

# Script to setup the new internal ID column on existing Foo Documents

# Valid lookups look like "5066;#6332.00000000000"
# Invalid lookups look like "5066;#"
# This regex extracts the part between the (#) and the (.)
$matcher = [regex] "(.*\#)([0-9]+)"

# Open web
$url = $Env:appUrl;
$web = Get-SPWeb $url
# Get list
$list = $web.Lists["Foo Documents"]

# Disable event firing 
$myAss = [Reflection.Assembly]::LoadWithPartialName("Microsoft.SharePoint");
$type = $myAss.GetType("Microsoft.SharePoint.SPEventManager");
$prop = $type.GetProperty([string]"EventFiringDisabled",[System.Reflection.BindingFlags] ([System.Reflection.BindingFlags]::NonPublic -bor [System.Reflection.BindingFlags]::Static));
$prop.SetValue($null, $true, $null);

# Disable list threshold
$list.EnableThrottling = $false;
 
$success = $false;
Clear-Host
try {
    $counters = @{};
    $count = $list.ItemCount;
    $progress = 0;
    # For each list item if it has a lookup value copy the FooID to the DocumentsFooId
    foreach ($item in $list.Items) {
        $lookup = $item["FooLookup"];
        $fooId = $matcher.match($lookup).Groups[2].Value;
        $counters[$fooId]++;
        if ($fooId) {
            $item["DocumentsFooId"] = [Double]::Parse($fooId);
            # Update SharePoint (without changing the Modified or Modified By fields)
            $item.SystemUpdate($false);
        }
        $progress++;
        Write-Progress -Activity "Copying Foo Ids" `
        -status "Processing doc(s) w/ Id $fooId$('.'*$counters[$fooId])" `
        -percentComplete(($progress / $count)*100);
    }
    Write-Host "Finished setting up new column on $count documents!";
    $success = $true;
} finally {
    if (!$success) {
        Write-Host "Column setup cancelled/failed after $progress documents!";
    }

    # Re-enable event firing
    $prop.SetValue($null, $false, $null);

    # Re-enable list threshold
    $list.EnableThrottling = $true;
}

{{< /highlight >}}

It grew some nice features as I repeatedly tested it. The PowerShell built-in
progress thing is kinda neat, especially since this is a long running
process. Since there were multiple documents with the same ID referenced I added
a counter to make the status clearer. In Python I would have used
[Collections.Counter](https://docs.python.org/2/library/collections.html#collections.Counter),
but in PowerShell I got the same thing virtually for free:

{{< highlight powershell >}}

# Setup a dictionary for counting found IDs
$counters = @{};

# In mainloop tally each ID
foreach ($item in $list.Items) {
    ...
    $counters[$fooId]++;
    ...
    # Now each step has access to the current count seen so far
}

# And at the end you can see the total per ID

{{< /highlight >}}

And it works - all the IDs are copied but the items are unmodified. I'm sure I
could clean this up a little (e.g. getting the `item.SystemUpdate($false)` out
of the mainloop should speed it up) but it solves my immediate problem and I
now have one more tool for Windows hacking.