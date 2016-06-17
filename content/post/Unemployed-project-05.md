+++
categories = ["development"]
tags = ["CSharp", "AspNetCore", "code", "Typescript"]
date = "2016-06-12T16:03:48-07:00"
title = "Unemployed Project - Frontend"

+++

Fun with Bootstrap, JQuery, Knockout, Typescript, and more.
<!--more-->
<hr /><br />
So I finally spent some time to build the log forms. I started by trying to
build the forms using plain old html with some Bootstrap for layout. It wasn't
working very well though so I stopped to take a deeper read through the Bootstrap
docs. I learned a lot of things along the way. It turns out Bootstrap expects
forms to be structured in a very specific way to participate in grid sizing and
styling.

The keys are:

* Add `class="form-horizontal"` to the form (makes a form act as a grid container).
* Wrap `row`s of controls in `<div class="form-group">`.
* Add a `<label class="control-label">` for every control (mainly for accessibility?).
* Add `class="form-control"` to every control.

Here is the form part of the *Activity Log*:
{{< highlight html >}}

<form id="LogActivity" class="form-horizontal" data-bind="submit: postLog">
    <div class="form-group">
        <label for="LogActDate" class="col-md-1 control-label">Date</label>
        <div class="col-md-4">
            <input type="date" class="form-control" id="LogActDate"
                   data-bind="value: LogDate" />
        </div>
        <label for="LogLoc" class="col-md-1 control-label">Location</label>
        <div class="col-md-6">
            <input type="text" class="form-control" id="LogLoc"
                   maxlength="64" placeholder="e.g. WorkSource"
                   data-bind="value: Location" />
        </div>
    </div>
    <div class="form-group">
        <label for="LogActDesc" class="col-md-1 control-label">Description</label>
        <div class="col-md-11">
            <textarea class="form-control" id="LogActDesc" rows="5"
                      maxlength="256" placeholder="Required"
                      data-bind="value: Description"></textarea>
        </div>
    </div>
    <div class="form-group">
        <div class="col-md-2 col-md-offset-10">
            <button type="submit" class="form-control btn-primary" id="LogActSubmit">
                Add Log
            </button>
        </div>
    </div>
    <!-- This is just for debugging -->
    <pre data-bind="text: ko.toJSON(actModel)"></pre>
</form>

{{< /highlight >}}

I put an ID on every control thinking that on submit I would use them to pull
all the values out. I am posting JSON data back to the server with jquery's
ajax method, and I prototyped the submit function long before I had everything
built. Once I had everything built though I just couldn't see pulling all that
data out manually. I actually have two forms on the page hidden by tabs, and the
second form has nearly a dozen fields.

I just saw [Knockout.js](http://knockoutjs.com/) for the time the other day. It
seemed like a neat idea, kind of reminds me of Clojure atom watches, or
[Javelin](https://github.com/hoplon/javelin) cells. It lets you make declarative
data bindings between a data structure and the DOM. It also provides an easy way
to hook up to form submission, and serialize a bound data structure to json. It
turns out to be a perfect fit for this application. Because of the way the
bindings work I don't need to figure out what was submitted, each form just has
it's own model that does the right thing.

While reading through the Knockout docs I was thinking how nicely it would go
with [Typescript](https://www.typescriptlang.org/docs/tutorial.html). Setting up
Typescript in my VS project wasn't too hard, but it was a little messy. You need
to tweak your project.js, edit your Gulp build, you need to use npm to install a
commandline tool to use Javascript modules, etc. The docs make it pretty clear
but it's a lot of hoops. Initially I thought I could use Typescript's simplified
inheritance syntax to let the forms share common information, but that didn't
work out. I still think the typing helped me out a little.

Here are my models:
{{< highlight typescript >}}

class ActLogModel {
    static AjaxOptions: any = {
        url: "/LogLists/AddActivity",
        type: "POST",
        contentType: "application/json",
        processData: false,
        data: "{}",
        dataType: "json"
    };
    LogDate: KnockoutObservable<string>;
    Description : KnockoutObservable<string>;
    Location: KnockoutObservable<string>;

    constructor() {
        let date: string = moment().format("YYYY-MM-DD");
        this.LogDate = ko.observable(date);
        this.Description = ko.observable("");
        this.Location = ko.observable("");
    }

    postLog(form: Element): void {
        let data: string = ko.toJSON(this);
        ActLogModel.AjaxOptions.data = data;
        console.log("Posting: ", data);
        $.ajax(ActLogModel.AjaxOptions).done(this.renderResult);

    }

    renderResult(data: JSON): void {
        console.log(data);
    }
}

class ConLogModel {
    static AjaxOptions: any = {
        url: "/LogLists/AddContact",
        type: "POST",
        contentType: "application/json",
        processData: false,
        data: "{}",
        dataType: "json"
    };
    LogDate: KnockoutObservable<string>;
    Description: KnockoutObservable<string>;
    MethodType: KnockoutObservable<string>;
    MeansType: KnockoutObservable<string>;
    Employer: KnockoutObservable<string>;
    Contact: KnockoutObservable<string>;
    Phone: KnockoutObservable<string>;
    Address: KnockoutObservable<string>;
    City: KnockoutObservable<string>;
    State: KnockoutObservable<string>;

    constructor() {
        let date: string = moment().format("YYYY-MM-DD");
        this.LogDate = ko.observable(date);
        this.Description = ko.observable("");
        this.MethodType = ko.observable("0");
        this.MeansType = ko.observable("0");
        this.Employer = ko.observable("");
        this.Contact = ko.observable("");
        this.Phone = ko.observable("");
        this.Address = ko.observable("");
        this.City = ko.observable("");
        this.State = ko.observable("");
    }

    postLog(form: Element): void {
        let data: string = ko.toJSON(this);
        ConLogModel.AjaxOptions.data = data;
        console.log("Posting: ", data);
        $.ajax(ConLogModel.AjaxOptions).done(this.renderResult);
    }

    renderResult(data: JSON): void {
        console.log(data);
    }
}

// setup all the models and bind them to the view
let actModel: ActLogModel = new ActLogModel();
ko.applyBindings(actModel, $("#LogActivity")[0]);
let conModel: ConLogModel = new ConLogModel();
ko.applyBindings(conModel, $("#LogContact")[0]);

{{< /highlight >}}

Due to how `<input type="date" />` values work I couldn't use a `<Date>`, and I
had to add [Moment.js](http://momentjs.com/) to handle date formatting because
of insufficiencies in Javascript date handling. I also didn't bother casting my
enums to numbers. The good news is that asp.net correctly handles the
conversions on post!

One thing that didn't work was Knockout binding to radio buttons. I am using
Bootstrap to convert `<input type="radio" />` into proper buttons. In doing so
Bootstrap consumes the click event and thus Knockout never sees updates. I did
find a library that bridges the gap (and more). The
[Knockstrap](https://faulknercs.github.io/Knockstrap/) library allows a number
of interesting interactions with Bootstrap UI components, but the library isn't
hosted on any CDN.

Rather than hast the whole library myself I chose to port just the relevant code
to Typescript:

{{< highlight typescript >}}

// Note: Typescript doesn't allow assigning new members to a type
ko.bindingHandlers["radio"] = {
    init: function (elem: Element, valueAccessor: KnockoutObservable<any>): void {
        if (!ko.isObservable(valueAccessor())) {
            throw new Error("A radio binding should only be used with observable values.");
        }

        $(elem).on("change", "input:radio", function (evt: Event): void {
            // add handler to event queue for defered execution
            setTimeout(() => {
                let radio: JQuery = $(evt.target);
                let value: any = valueAccessor();
                let newValue: string = radio.val();

                if (!radio.prop("disabled")) {
                    // this sets the observable
                    value(newValue);
                }
            }, 0);
        });
    },

    update: function (elem: Element, valueAccessor: KnockoutObservable<any>): void {
        let value: string = ko.unwrap(valueAccessor()) || "";
        let selector: string = 'input[value="' + value.replace(/"/g, '\\"') + '"]';
        let radioButton: JQuery = $(elem).find(selector);
        let radioButtonWrapper: JQuery; // the radio grouping label

        if (radioButton.length) {
            radioButtonWrapper = radioButton.parent();
            radioButtonWrapper.siblings().removeClass("active");
            radioButtonWrapper.addClass("active");
        } else {
            radioButtonWrapper = $(elem).find(".active");
            radioButtonWrapper.removeClass("active");
            radioButtonWrapper.find("input").prop("checked", false);
        }
    }
};

{{< /highlight >}}

And the html:
{{< highlight html >}}

<div class="btn-group" data-toggle="buttons"
     data-bind="radio: MethodType">
    <label class="btn btn-primary active">
        <input type="radio" name="method" id="methodApply"
               value="0" autocomplete="off" checked /> Application
    </label>
    <label class="btn btn-primary">
        <input type="radio" name="method" id="methodInter"
               value="1" autocomplete="off" /> Interview
    </label>
    <label class="btn btn-primary">
        <input type="radio" name="method" id="methodInquiry"
               value="2" autocomplete="off" /> Inquery
    </label>
</div>

{{< /highlight >}}

Handling the submitted json in the controller was easy:
{{< highlight csharp >}}

// POST: /LogLists/AddActivity
[HttpPost]
public async Task<JsonResult> AddActivity([FromBody] ActivityLog log)
{
    if (ModelState.IsValid)
    {
        await _logRepository.AddAsync(log, HttpContext.User);
        // Return a status and the processed data
        return Json(new {good = true,
                         data = Json(log)
        });
    }
    return Json(new { good = false });
}

{{< /highlight >}}

With that my forms are done. I can build a pretty form, pull all the data out,
send it up to the server, and convert it into a EF POCO.

Once I got that far I of course tried to go the last yard and get the data
persisted into the database. Unfortunately it seems what I was doing with the
`ApplicationUser` doesn't actually work. Because of how I decoupled from the
data store though, I can just swap in something else without any changes to my
controller or frontend code. I'm thinking of just adding my own user data store
keyed off the user ID I already found.

### Edit - Scratch that

I just now got the per user lists working!

The last problem I ran into was a `ClassCastException` casting an `IPrincipal`
instance to an `ApplicationUser` while calling `_userManager.UpdateAsync`. All I
needed to do was use the actual user I had acquired earlier in the method. While
figuring that out though I also learned how to get access to the `HttpContext`
in my repo object, removing the need to pass in the user from the controller.

That fixed JobLog storage (confirmed in the database explorer), but I still
couldn't see a user's log list. It
[turns out](https://msdn.microsoft.com/en-us/data/jj574232) EF7 doesn't
rehydrate "navigation properties" automatically. Since I am getting
access to the user object via the UserManager I also can't force child data
inclusion. By including a db context in my repo object though I can query to get
the user logs

Updated LogRepository class:

{{< highlight csharp >}}

public class LogRepository : ILogRepository
{
    private readonly IHttpContextAccessor _httpContextAccessor;
    private readonly UserManager<ApplicationUser> _userManager;
    private readonly ApplicationDbContext _context;

    public LogRepository(IHttpContextAccessor httpContextAccessor,
                         UserManager<ApplicationUser> userManager,
                         ApplicationDbContext context)
    {
        _httpContextAccessor = httpContextAccessor;
        _userManager = userManager;
        _context = context;    
    }

    private async Task<ApplicationUser> GetCurrentUser()
    {
        var user = _httpContextAccessor.HttpContext.User;
        return await _userManager.GetUserAsync(user);
    }

    public async Task<IEnumerable<BaseLog>> JobLogsAsync()
    {
        var cuser = await GetCurrentUser();
        if (cuser != null)
        {
            var uid = cuser.Id;
            var logs = from log in _context.JobLogs
                       where log.ApplicationUser.Id == uid
                       orderby log.LogDate
                       select log;
            return logs.ToList();
        }
        return new List<BaseLog>();
    }

    public async Task AddAsync(BaseLog log)
    {
        var cuser = await GetCurrentUser();
        if (cuser != null)
        {
            if (cuser.JobLogs == null)
            {
                 cuser.JobLogs = new List<BaseLog>();
            }
            var logs = cuser.JobLogs;
            log.Id = new Guid();
            logs.Add(log);
            await _userManager.UpdateAsync(cuser);
        }
    }
}

{{< /highlight >}}

With that I have all the key moving parts working and can finally start working
on features.