+++
categories = ["development"]
tags = ["PowerShell", "SharePoint"]
date = "2017-01-31T13:56:34-08:00"
title = "Miscellaneous MVC 5 Details"

+++

Some Asp.Net MVC details and other thoughts.
<!--more-->
<hr/><br/>
We are preparing to start a new project at work - the replacement of an old
Lotus Notes "app" with a SQL DB and a web front end. I've exerted what little
influence I have in the pre-design meeting to push us toward more modern tech
(still in the MS ecosystem): MVC with Entity Framework and hopefully a thick
client-side app pulling data via AJAX requests.

Since the meeting I've spent some time researching what we picked and thinking
about the application structure. The interesting parts fall in two categories:

+ [EF Codebase Structure (architecture)]({{< relref "#EntityFramework" >}})
+ [Using Windows Auth in MVC]({{< relref "#WindowsAuth" >}})

I have used EF a couple of times now, and done some additional reading and
training on it, but I don't think the rest of the dev team are that familiar
with it. MVC will also be a new experience for some of them, especially pushing
work from the server to the clients (no more `runat=server`). Since I would
prefer to use these technologies, and to use them well, I am trying to be an
advocate for them. I can see already how easy it would be for this team to get
under a little pressure and decide to just chuck the whole thing for Webforms.

### Using Windows Auth in MVC {#WindowsAuth}

One of the first problems I think they will run into is the MVC default template
and its deep dependence on the newish
[OWIN middleware](https://www.asp.net/aspnet/overview/owin-and-katana), and
[Identity Framework](https://www.asp.net/identity/overview/getting-started/introduction-to-aspnet-identity)
for auth. There's nothing wrong with either of these if you need what they
provide, but our new app is going to be strictly intranet with auth handled by
the domain. So to simplify our application I created a dummy project and set to
work cutting out all the unnecessary bits.

I admit I got most of this information from
[a](http://stackoverflow.com/questions/23260283/windows-authentication-not-working-in-asp-net-mvc-5-web-app)
[collection](http://stackoverflow.com/questions/12552950/windows-authentication-in-mvc)
[of](http://stackoverflow.com/questions/12243699/mvc-website-forwards-to-account-login-with-only-windows-authentication-enabled?rq=1)
[StackOverflow](http://stackoverflow.com/questions/28483745/http-error-404-15-not-found-because-the-query-string-is-too-long)
[Answers](http://stackoverflow.com/questions/31665196/asp-net-mvc-5-without-owin)
, and don't fully grasp the intent or necessity of all of them. Someone
with more interest and time feel free to test all the possible combinations.

#### Enable

Firstly I enabled Windows Auth. This required changes to the *web.config* file,
changes to the *project* properties, and editing some code in *startup*. Here
are the changes to web.config:

{{< highlight xml >}}

<appSettings>
    <!-- Add the following -->
    <add key="autoFormsAuthentication" value="false"/>
    <add key="enableSimpleMembership" value="false"/>
</appSettings>

<system.web>
    <!-- Add the following -->
    <authentication mode="Windows" />
    <authorization>
      <deny users="?" />
    </authorization>
</system.web>


<system.webServer>
    <modules>
        <!-- Add the following -->
        <remove name="FormsAuthenticationModule" />
    </modules>
</system.webServer>

{{< /highlight >}}

Next select your MVC project in the Solution Explorer and in properties ensure
Windows Authentication is enabled and Anonymous Authentication is disabled.

Finally edit Startup.Auth.cs (you can skip this step if you are going to remove
the builtin auth system entirely):

{{< highlight csharp >}}

public partial class Startup
{
    public void ConfigureAuth(IAppBuilder app)
    {
        app.CreatePerOwinContext(ApplicationDbContext.Create);
        app.CreatePerOwinContext<ApplicationUserManager>(ApplicationUserManager.Create);
        app.CreatePerOwinContext<ApplicationSignInManager>(ApplicationSignInManager.Create);

        // ----- Start deleting here -----
        app.UseCookieAuthentication(new CookieAuthenticationOptions
        {
            AuthenticationType = DefaultAuthenticationTypes.ApplicationCookie,
            LoginPath = new PathString("/Account/LogIn"),
            Provider = new CookieAuthenticationProvider
            {
                // There might be different things in here...
            }
        });            
        app.UseExternalSignInCookie(DefaultAuthenticationTypes.ExternalCookie);
        // ----- End deleting here -----

        app.UseTwoFactorSignInCookie(DefaultAuthenticationTypes.TwoFactorCookie,
        TimeSpan.FromMinutes(5));

        etc.
    }
}

{{< /highlight >}}

Now when you run your app you should see your logon name on the Home.Index page.

#### Remove

The next step for me was to remove everything that wasn't Windows Auth. I
started by deleting `Startup.cs`, `App_startup/Startup.Auth.cs`, and
`App_startup/IdentityConfig.cs` since they are only used by OWIN. I also deleted
all `Account` and `Manage` views and controllers since they are for Identity
management. In my case I also deleted the entire `Models` folder since my
[DAL](https://en.wikipedia.org/wiki/Data_access_layer) lives in another project
in the solution and everything in `Models` is there to support Identity.

Next comes package removal, which is made complex by the inter-package
depednacies: You can only uninstall a package once all its dependents are
uninstalled. I didn't really figure out the order, I just kept trying to
uninstall each package in order until they were all gone. Here is the list of
what was unnecessary (I also took the opportunity to remove appInsights):

+ microsoft.aspnet.identity.owin 
+ microsoft.owin.host.systemweb
+ microsoft.owin.security.cookies 
+ microsoft.owin.security.facebook 
+ microsoft.owin.security.google
+ microsoft.owin.security.microsoftaccount
+ microsoft.owin.security.twitter
+ microsoft.aspnet.identity.entityframework
+ (anything with application insights in the name)

The only thing I left was `microsoft.aspnet.identity.core`, which gives you
access to Windows Auth info (e.g. `Microsoft.AspNet.Identity` and
`User.Identity.GetUserName` in `_loginPartial.cshtml`. Next I had to make more
changes to web.config:

{{< highlight xml >}}

<appSettings>
    <!-- Add the following -->
    <add key="owin:AutomaticAppStartup" value="false" />
</appSettings>

<runtime>
    <AssemblyBinding>
        <!-- Delete any of these that referece a removed package -->
        <dependantAssembly>
        <!-- Blah Blah OWIN Blah -->
        </dependantAssembly>
    </AssemblyBinding>
</runtime>

{{< /highlight >}}

Now when you run the app you should be recognized, and you should still be able
to use auth annotations like `[Authorize(Roles = "Client, Administrator")]` on
controller methods (untested). With the project cleaned up it is much easier to
focus on the application logic instead of the Microsoft baggage.

### EF Codebase Structure (architecture) {#EntityFramework}

While I was playing with the above I was also having deep thoughts about Entity
Framework and the structure of the DAL. By this point I've seen a lot of
techniques and *best practices* for EF. I've found some things I like but I
wanted a way to justify those preferences. I also wanted to limit the number of
extraneous layers of abstraction I've seen recommended by the *enterprise minded*.

In my test solution I've spun the DAL off into its own project, because that
seems like a good idea. I am treating EF as the data persistence layer, i.e. I
want to interact with it as little as possible in DAL code and not at all
outside the DAL. The layer above that seems to be where all the controversy is:

+ Do you create *Repositories* around your DBContexts, or are they already repos?
+ Do you create *Generic* repos, or do you get specific?
+ Does any *business logic* live in the POCOs used by EF, or do you create
mirrors/wrappers for them, or something else?
+ If something else then *were does* the business logic live?

As I see it there are two clear ends of the spectrum here, on one you have a
large complex infrastructure were everything is heavily decoupled, and on the
other you have the minimum pretense to CRUD. I definitely feel the allure of
architecting that large system, but in the end it feels like it is justifying
its own existence by its complexity. I want to start with as few moving parts a
necessary, and abstract from there as needed.

The sketch I have in mind is POCOs are just data (pretend they are structs). A
repo is created for each perspective/use-case. What little business logic there
is in this app will live in the repo methods, or in helper classes (pure static
methods). In other words the repos are closer to Business Objects, controlling
the what and how of data access. As for justifying my choices I depend mainly on
the SRP (Single Responsibility Principle) - That things should have only one reason to
change:

+ *DBContexts* represent the DB in code, giving access to tables, views, etc.
    + Only changing when new collections need to be accessed.
+ *POCOs* are the object representation of entities (rows) in the DB.
    + Only changing to reflect the shape of data in their table.
+ *Repos* provide a higher level Command/Query interface over previous two.
    + Only changing in response to interaction requirements.

I think that balances separation of concerns against complexity pretty well, at
least for a start. With my repeated deep-dives into the legacy code at work I am
continually re-motivated to simplify as much as possible. So many examples of
architecture run amok, senseless designs, aimless decoupling, and general
blind adherence to design rules letter rather than their intent.

I'm sure I will run into problems with my design, but hopefully simple things
will be easier to change.