+++
categories = ["development"]
tags = ["CSharp", "AspNetCore", "code"]
date = "2016-06-09T12:22:09-07:00"
title = "Unemployment Project - Breakthrough"

+++

Surmounting the challenge of API churn in the MS ecosystem.
<!--more-->
<hr/><br/>
I think I've figured out enough to actually get to work on this project. The
last piece was very tricky though, so I want to post about it before moving
on. First though some corrections:

* Firstly it seems I am actually using MS MVC 5+, maybe 6? I'm not really sure.
* Second it seems I am using asp.net "vNext" or "Core" i.e. "Extreemly Beta".
* Finally similarly I am using whatever the latest incarnation of Entity
  Framework is as well.

It seems my version of VS (community 2015 latest update) defaults to all
bleeding edge tech for new projects. Not that I have a problem with that, but it
does make research harder, especially when information on the last three
versions still proliferates.

I spent some more time this morning reading up on the latest version of
[Entity Framework](https://ef.readthedocs.io/en/latest/modeling/index.html). The
official docs were a much better source of information that what I found
yesterday, but in the end I only had to make a few changes:

{{< highlight csharp >}}

    // First make the base class abstract
    public abstract class BaseLog
    {
        // Drop the manual descriptor, EF will create one automatically

        // Reciprocal link to user data (not strictly required)
        public virtual ApplicationUser ApplicationUser  { get; set; }
        ...
    }

    // Next add a DbSet(table) to the DB context
    public class ApplicationDbContext : IdentityDbContext<ApplicationUser>
    {
        public DbSet<BaseLog> Logs { get; set; }
        ...

        protected override void OnModelCreating(ModelBuilder builder)
        {
            base.OnModelCreating(builder);
            // These are required because BaseLog is not instantiable
            builder.Entity<ActivityLog>();
            builder.Entity<ContactLog>();
        }
    }

{{< /highlight >}}

Adding that `DbSet` has an unfortunate implication - I will have one big table
of all logs for all users. It's not what I had in mind, but it shouldn't be a
problem. EF deals with all the data association, and whatever DB backend I use
should deal with persisting/replicating the table.

The next step is to somehow get the user log data so it can be displayed and
edited. This turned out to be a major roadblock. The first problem is that
virtually no tutorials cover applications with per-user data. It seems asp.net
applications only support users that consume content, not produce it? The other
problem is that user handling seems to be Microsoft's fastest evolving API. I
ran into a great deal of conflicting advice, and none of it was applicable to my
situation. 

It seems that in previous versions there was a method called `GetUserId`. It
seems to have had many homes but, when you could find it, you could use it to
get your user model from the DB context. As far as I can tell that method has
been obliterated from all current asp.net/mvc/ef code.

After several hours reading and vetting code with Intellisense I effectively
circled back to where I already was. I ended up at
[this repo](https://github.com/aspnet/Identity) looking at the sample
controllers, which happen to look exactly like the autogen code in my
project. The key to understanding what was going on though was a bullet I
caught while reading through the
[asp.net core overview](https://docs.asp.net/en/latest/conceptual-overview/aspnet.html):
*Built-in dependency injection*.

Here is the example that got me excited:
{{< highlight csharp >}}

    public class ManageController : Controller
    {
        private readonly UserManager<ApplicationUser> _userManager;
        ...

        public ManageController(
        UserManager<ApplicationUser> userManager, //<- Where does this come from?
        ...)
        {
            _userManager = userManager;
            ...
        }

        [HttpGet]
        public async Task<IActionResult> Index(...)
        {
            ...
            var user = await GetCurrentUserAsync(); //<- This is what I need
            var model = new IndexViewModel
            {
                HasPassword = await _userManager.HasPasswordAsync(user),
                PhoneNumber = await _userManager.GetPhoneNumberAsync(user),
                ...
            };
            return View(model);
        }

        private Task<ApplicationUser> GetCurrentUserAsync()
        {
            // Using the thing from nowhere to get the thing I need
            return _userManager.GetUserAsync(HttpContext.User);
        }
    }

{{< /highlight >}}

Here is what my controller looked like before:
{{< highlight csharp >}}

    public class HomeController : Controller
    {
        private readonly ApplicationDbContext _context;

        public HomeController(ApplicationDbContext context)
        {
            _context = context;
        }
        ...
    }

{{< /highlight >}}

Despite the difference in shape between the example and my code I decided to try
patching in what I needed:

{{< highlight csharp >}}

    public class HomeController : Controller
    {
        private readonly ApplicationDbContext _context;
        private readonly UserManager<ApplicationUser> _userManager;

        public HomeController(ApplicationDbContext context,
                              // Still don't know where it comes from:
                              UserManager<ApplicationUser> userManager)
        {
            _context = context;
            _userManager = userManager;
        }
        ...
    }

{{< /highlight >}}

I added the helper method and used it in my "Action" method. I didn't get any
intellisense warnings, and I was able to reference user.JobLogs, so I built the
project, and VS reported "Built successfully"!

It all sounds very promising, I was even able to run (Ctrl-F5) the server and
exercise the page a little. Now to see if I can build the UI on it.