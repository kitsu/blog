+++
categories = ["development"]
tags = ["CSharp", "AspNetCore", "code"]
date = "2016-06-10T08:06:25-07:00"
title = "Unemployed Project - Untested"

+++

Even more ecosystem problems, now with working forms.
<!--more-->
<hr/><br/>
After yesterday's post I *was* going to dig into the UI side straight away but I
got distracted by something shinny instead. I had run into several shallow
guides to testing an asp.net application in my readings, and I thought it seemed
like a good time to start adding tests.

Now I think this project is a perfect example of where Test Driven Development
makes no sense. To write tests you first need a place to put them. You need some
infrastructure to build, run, and report them. Most importantly you need
something coherent to build tests against. Thus far my primary test focus has
been "does all this work", and my testing has consisted of Intellisense prompts
and building/running the app.

Since I am starting to have some structure emerge from the haze though I would
like to have tests inform development moving forward. First though I had some
cleanup to do. I had seen a pattern pop up a number of times in yesterday's
readings, and thinking about testing I could really feel it's motivations. In
the code I posted yesterday I had the controller code coupled directly to the
Entity Framework code, which means in testing I would need to either provide a
fake EF implementation, or provide EF with a fake DB to make anything work.

The pattern I used to decouple the controller from the EF-DB is called
*repository*. It involves wrapping your data access layer in a simple collection
interface. Thus in your application code it seems like you are just using a
basic data type, and a basic data type is all you need in your tests.

This is another case where I really feel handcuffed by static typing. Because I
need to declare what *type* of thing I pass to my controller I then need to pass
something of the correct type in my tests. In Python you don't need all that
ceremony, you just pass in an object that has the expected methods/attributes
and who cares what it is. I do actually like the formal specification provided
by interfaces, but really it is just a list of required members - not a type.

Anyway, here are the changes I made:
{{< highlight csharp >}}

namespace JobLogger.Controllers
{
    public class HomeController : Controller
    {

        // The references to the DbContext and UserManager are
        // replaced by the new repo interface
        private readonly ILogRepository _logRepository;

        public HomeController(ILogRepository logRepository)
        {
            _logRepository = logRepository;
        }
        
        public async Task<IActionResult> Index()
        {
            // The current user is one dependency I couldn't unravel
            // though I think the controller context can be mocked in testing
            return View(await _logRepository.JobLogsAsync(HttpContext.User));
        }
    }
}

// In LogRepository.cs
namespace JobLogger.Models
{
    public interface ILogRepository
    {
        Task<IEnumerable<BaseLog>> JobLogsAsync(IPrincipal user);
    }

    public class LogRepository : ILogRepository
    {
        // The LogRepository implementation now bears the burden of
        // data layer coupling
        private readonly UserManager<ApplicationUser> _userManager;

        public LogRepository(UserManager<ApplicationUser> userManager)
        {
            _userManager = userManager;
        }

        // Here user log access just looks like requesting a list from somewhere
        public async Task<IEnumerable<BaseLog>> JobLogsAsync(IPrincipal user)
        {
            var cuser = await _userManager.GetUserAsync((ClaimsPrincipal)user);
            if (cuser != null)
            {
                var logs = cuser.JobLogs;
                if (logs != null) return logs.ToList();
            }
            return new List<BaseLog>();
        }

        // Other methods will be added for other CRUD operations
        public void Add(BaseLog log, IPrincipal user){}
    }
}

// In Startup.cs
namespace JobLogger
{
    public class Startup
    {
    ...
        // In the Starup.Configureservices method is were the Dependency
        // Injection magic happens, just adding our "service" is enough
        // to get it populated for the controller
        public void ConfigureServices(IServiceCollection services)
        {
            ...
            // Add application services.
            services.AddScoped<ILogRepository, LogRepository>();
            ...
        }
    ...
    }
}

{{< /highlight >}}

I think it cleaned up pretty nicely. I am annoyed at how the user argument is
conjured inside the Index method. Since the user is only known by the context at
runtime though I couldn't really pull it out of the controller. It all looks
pretty simple, but it did take some research and mental gymnastics to get into
shape.

With that sorted I turned to writing tests. Or at least I would have. I spent
hours reading the Internet, and tried no less than six times to get some testing
configuration that would work. All the tutorials (including the MS
Documentation) were wrong, none of the frameworks or templates worked, there was
no configuration that would just work. Even after I restructured the whole
project, rebuilt the whole thing from scratch, nothing worked.

After wasting so much time on that pointless pursuit I only had the late hours
of the night to play with the UI. And oh surprise, despite the lack of test
evidence, every thing did in fact work. Playing with Bootstrap is fun, and the
results are reasonably attractive. It is Friday today, and I still believe I can
have something usable by Monday (first day I need to record my log).

I will have to try again some time, but right now I am tired of wrestling with the
MS morass.