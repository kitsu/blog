+++
categories = ["development"]
tags = ["CSharp", "AspNetCore", "Knockout", "code"]
date = "2016-06-19T11:58:12-07:00"
title = "Unemployed project - Knockout Templates"

+++

Building modular chunks of HTML and reusing them with Knockout.
<!--more-->
<hr/><br/>
After deciding my app would need to render new log data on the fly, and after
reading some
[interesting](http://www.onebigfluke.com/2015/01/experimentally-verified-why-client-side.html)
[posts](https://medium.com/google-developers/tradeoffs-in-server-side-and-client-side-rendering-14dad8d4ff8b#.x755manz0)
on client-side rendering, I decided to replace my
server-side Razor templates with [Knockout templates](http://knockoutjs.com/documentation/template-binding.html).
This has simplified the shape of my application, and enabled me to do things
that were going to be messy in Razor, but getting there was not without
complications.

First I made some models that would contain the state for groups of logs. Here
are the important bits of the ListModel:
{{< highlight typescript >}}
...
class ListModel {
    Logs: KnockoutObservableArray<ActLogModel | ConLogModel>;
    Count: KnockoutComputed<number>;

    constructor() {
        this.Logs = ko.observableArray([]);
        this.Count = ko.computed(() => { return this.Logs().length });
    }

    updateList = (result: any): void => {
        // Callback for ajax log request
        if (result.success === true) {
           console.log(`Got ${result.data.length} Logs!`);
            for (let log of result.data) {
                if (log.hasOwnProperty("Location")) {
                    this.addAct(log);
                } else {
                    this.addCon(log);
                }
            }
        }
    }

    addAct(log: any): void {
        // Build new model for this log
        let actModel = new ActLogModel(log.LogDate.slice(0, 10));
        ...
        this.Logs.push(actModel);
    }

    addCon(log: any): void {
        // Build new model for this log
        let conModel = new ConLogModel(log.LogDate.slice(0, 10));
        ...
        this.Logs.push(conModel);
    }

    logTemplate(log: ActLogModel | ConLogModel): string {
        // Used to determine template name for existing logs
        if (log instanceof ActLogModel) {
            return 'ActLogTemp';
        }
        return 'ConLogTemp';
    }

    editTemplate(log: ActLogModel | ConLogModel): string {
        // Used to determine edit form template for existing logs
        if (log instanceof ActLogModel) {
            return 'LogActTemp';
        }
        return 'LogConTemp';
    }
}
...
{{< /highlight >}}

The model is then bound in a script tag at the bottom of the page:
{{< highlight html >}}
<script type="text/javascript">
    $(function () {
        ...
        // Bind list model to view
        window.listModel = new ListModel()
        $.getJSON("/LogLists").done(listModel.updateList)
        ko.applyBindings(listModel, $("#LogList")[0]);
    });
</script>
{{< /highlight >}}

Here is where the model is bound:
{{< highlight html >}}
...
<div id="LogList">
    <h4 class="text-center">
        <!-- There will be some filtering controls here -->
        <span data-bind="text: Count"></span> Logs
    </h4>
    <div data-bind="template: {name: logTemplate, foreach: Logs}"></div>
</div>
...
{{< /highlight >}}

I thought it was interesting that you can actually include html in a script tag!
Here is the entire Activity Log Template:
{{< highlight html >}}

<script type="text/html" id="ActLogTemp">
    <div data-bind="attr: {id:'log-' + Id()}" class="panel panel-default">
        <div class="panel-heading" role="tab"
             data-bind="attr: {id: 'head-' + Id()}">
            <h4 class="panel-title pull-left">
                <a role="button" data-toggle="collapse"
                   data-bind="attr: {href: '#body-' + Id(),
                                     'aria-controls': 'body-' + Id()}"
                   aria-expanded="false"
                   class="btn">
                    <span class="label label-default"
                          data-bind="text: LogDate">
                    </span>
                    &nbsp; Activity at
                    <span data-bind="text: Location"></span>
                    <span class="glyphicon glyphicon-menu-down"></span>
                </a>
            </h4>
            <!-- Nested template! -->
            <div data-bind="template: {name: 'DeletePanel'}"></div>
            <div class="clearfix"></div>
        </div>
        <div class="panel-collapse collapse" role="tabpanel"
             data-bind="attr: {id: 'body-' + Id(),
                               'aria-labelledby': 'head-' + Id()}">
            <div class="panel-body">
                <dl class="dl-horizontal">
                    <dt>Description:</dt>
                    <dd><pre data-bind="text: Description"></pre></dd>
                </dl>
            </div>
        </div>
    </div>
</script>

{{< /highlight >}}

Both of the log types share a common UI component with the edit & delete
buttons. The delete button is a toggle for a collapsed panel to confirm
deletion. I had a lot of trouble getting the DeletePanel template to work, it
seemed like the model wasn't getting bound. I was blaming the nested template
for quite a while, but when I added some bound elements to debug I found the
model was fine. I was just about to paste the template back into both log
templates when I figured it out:
{{< highlight html >}}

<script type="text/html" id="DeletePanel">
    <div class="btn-group pull-right" data-toggle="buttons">
        ...
        <!-- Note the missing brace in the data-bind! -->
        <a class="btn btn-sm btn-danger" role="button"
           data-toggle="collapse" aria-expanded="false" aria-controls="DeletePrompt"
           data-bind="attr: {'data-target': '#delete-' + Id()">
            Delete
        </a>
    </div>
</script>

{{< /highlight >}}

I have no idea how the code worked before I factored it into its own template,
it must not have. After adding the missing `}` everything worked.

In the nested DeletePanel template I added even another nested template for the
edit form. Actually the edit form *is* the log add form. It took several tries
to make the form templates reusable, but after my DeletePanel success I knew it
would work. At first I just reused the log form templates unchanged, and they
actually worked - the fields all populated from the model and clicking the
submit button did *something*. That something was re-adding the edited log as a
new entry.

The add forms use a Knockout `submit` binding to tie the submit event to the add
method of a log. To allow setting the submit callback I had to move the form
tags outside of the template. To fix the button text I had to change the data
member of the template data-bind to include the action name. Because setting the
data member replaces the binding context I also had to pass the model explicitly.

The next problem I had was that when the submit callback is called `this` isn't
the model. I tried using a Typescript fat arrow `=>`, but then `this` was bound
to the container - probably because of where the method was defined. The
solution was to use the method's bind method in the data-bind. Here are the
bits:

{{< highlight html >}}

<!-- In the page html for add form -->
<!-- Note here the model is the AdditionModel which contains the act & con -->
<form class="form-horizontal" data-bind="submit: actModel.addLog.bind(actModel)">
    <div data-bind="template: {name: 'LogActTemp',
                               data: {actionName: 'Add Log',
                                      model: actModel}}"></div>
</form>

<!-- The inserted Template -->
<script type="text/html" id="LogActTemp">
    <div class="form-group">
        <label for="LogActDate" class="col-md-1 control-label">Date</label>
        <div class="col-md-4">
            <input type="date" class="form-control" id="LogActDate"
                   data-bind="value: model.LogDate" />
        </div>
        ...
    </div>
    <div class="form-group">
        <div class="col-md-2 col-md-offset-10">
            <button type="submit" class="form-control btn-primary"
                    id="LogActSubmit" data-bind="text: actionName">
            </button>
        </div>
    </div>
</script>

{{< /highlight >}}

All together it looks like a lot, but with the templates stored away in a Razor
partial view and the Typescript in its own file everything cleaned up
nicely. Adding the edit forms was just a couple lines of code once the template
was sorted.

I had to do a little work to get the editing working on the server side. The
main problem was controller data binding fails when passing a log id in
json. The solution was passing the id in the url:
{{< highlight csharp >}}
...
// POST: /LogLists/EditContact/{id}
[HttpPost]
public async Task<JsonResult> EditContact(Guid Id, [FromBody] ContactLog log)
{
    if (ModelState.IsValid)
    {
        log.Id = Id;
        var success = await _logRepository.UpdateAsync(log);
        if (success)
        {
            return Json(new { success = true });
        }
    }
    return Json(new { success = false });
}
...
{{< /highlight >}}
