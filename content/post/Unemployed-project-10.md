+++
categories = ["development"]
tags = ["CSharp", "AspNetCore", "EF", "SQL", "code"]
date = "2016-06-21T11:19:00-07:00"
title = "Unemployed project - Migration"

+++

Learning the hard way not to trust automatic migrations.
<!--more-->
<hr/><br/>
I'm not sure why I thought that would work.

While doing a little code cleanup I renamed my ambiguous enums and the related
members in my data model. VS automatic rename is awesome (though it doesn't work
in Typescript) so the code change was done in seconds. When I launched the site
I wasn't that surprised to get an error - there were pending changes to my
DB. The place I went wrong was trusting the helpful suggestion in the error
message.

The default server error page gives a (somewhat confusing) traceback, and the
advice to run `dotnet ef migrations add [migration name]` followed by `dotnet ef
database update` to fix the problem. To be fair running the first command did
give a warning: `An operation was scaffolded that may result in the loss of
data. Please review the migration for accuracy`. The use of the word *scaffold*
lead me to believe it was just worried about my C# code not matching the new
records.

After running the migration I again tried to run the app, but now it expired
with a different error - One of my LINQ query failed when it hit a null
value. My first though was that I had broken/lost/dropped the entire log table,
so I jumped into the SQL Explorer to poke around. It turns out everything was
still there, but when I scrolled over to check my new columns I found that all
the values were null.

My first step to repair the damage was to write some SQL queries to set the
values to something:
{{< highlight sql >}}

-- Wrote this after the SELECT query was working
UPDATE [dbo].[JobLogs]
SET [ContactType] = 0,
    [ContactMeans] = 0
WHERE [ApplicationUser] NOT NULL AND [Discriminator] = 'ContactLog'
-- Prototyped this first to ensure I was hitting the right rows
SELECT * FROM [dbo].[JobLogs]
WHERE [ApplicationUser] NOT NULL AND [Discriminator] = 'ContactLog'
GO

{{< /highlight >}}

Luckily this is just a LocalDB with only a handful of real logs, and zero is
actually the correct value for anything that matters. I did learn that my
"deleted" logs are still hanging out in the DB even though they are
un-referenced. Much better to find that out now.

My next step was to figure out where the automatically generated migration went
wrong. Here is the original generated code:
{{< highlight csharp >}}

public partial class EnumNameChange : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropColumn(
            name: "MeansType",
            table: "JobLogs");

        migrationBuilder.DropColumn(
            name: "MethodType",
            table: "JobLogs");

        migrationBuilder.AddColumn<int>(
            name: "ContactMeans",
            table: "JobLogs",
            nullable: true);

        migrationBuilder.AddColumn<int>(
            name: "ContactType",
            table: "JobLogs",
            nullable: true);
    }
    ...
}

{{< /highlight >}}

You can see it just dropped all the old columns and added new null columns. Here
is my rewrite that would have saved me a little trouble:
{{< highlight csharp >}}

public partial class EnumNameChange : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.RenameColumn(
            name: "MeansType",
            table: "JobLogs",
            newName: "ContactMeans"
            );

        migrationBuilder.RenameColumn(
            name: "MethodType",
            table: "JobLogs",
            newName: "ContactType"
            );
    }
    ...
}

{{< /highlight >}}

Overall it was a good learning experience. I did want to try some migration
prior to having any real users, although I would have liked to do it under more
controlled circumstances.