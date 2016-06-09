+++
categories = ["development"]
tags = ["c#", "asp.net", "mvc5", "code"]
date = "2016-06-08T19:16:20-07:00"
title = "Unemployment Project - Struggle"

+++

Trying to formulate a design while reeling under a pile of new technologies.
<!--more-->
<hr/><br/>
After going through some more tutorials this morning I felt comfortable jumping
into Visual Studio and starting up my new project. The feeling was short lived
though once I tried to implement my plans. Before starting I had made a
quick sketch of the page organization I wanted, but twisting the default app to
match that shape isn't a straight forward task. My first impulse was to rip out
all the default content, but thinking about it I knew I would want most of that
stuff in the end.

I decided to start instead by defining my data model. I created a new class file
in the models directory and opened up the pdf form to start mapping data fields
to properties. I had problems even with that simple task though. The form for
one entry actually contains fields for two different kinds of events. Thinking
about how to represent that from a database perspective the simplest solution
would be to just have one big table with all the possible columns. The columns
actually used would then be determined based on a type discriminator column.

Because I am using so called *code first* data modeling though it isn't
completely clear to me how that idea translates into code. After doing some more
research it seems like Entity Framework's default behavior given models in a
class hierarchy is to do just that though - create a big table that is the union
of all instance properties.

Here is what I've come up with so far (BTW I don't know if this actually works):

{{< highlight csharp >}}

namespace JobLogger.Models
{
    public enum LogType { Activity, Contact }
    public enum MethodType { Application, Interview, Inquery }
    public enum MeansType { Online, Fax, InPerson, Kiosk, Mail, Telephone }

    public class BaseLog
    {
        [Required]
        public int ID { get; set; }

        [Required]
        public LogType LogType { get; set; }

        [Required]
        [Display(Name = "Log Date")]
        [DataType(DataType.Date)]
        public DateTime LogDate { get; set; }

        [Required]
        [StringLength(64)]
        public string Description { get; set; }
    }

    public class ActivityLog : BaseLog
    {
        [Required]
        [StringLength(64)]
        public string Location { get; set; }
    }

    public class ContactLog : BaseLog
    {

        [Required]
        public MethodType MethodType { get; set; }

        [Required]
        public MeansType MeansType { get; set; }

        [Required]
        [StringLength(64)]
        public string Employer { get; set; }

        [Required]
        [StringLength(64)]
        public string Contact { get; set; }

        [DataType(DataType.PhoneNumber)]
        [StringLength(13)]
        public string Phone { get; set; }

        // Reuse for both web, email, and street address
        [Required]
        [StringLength(64)]
        public string Address { get; set; }

        [Required]
        [StringLength(32)]
        public string City { get; set; }

        [Required]
        [StringLength(32)]
        public string State { get; set; }
    }
}

{{< /highlight >}}

In particular I expect the union of two models containing `[Required]`
properties is a table requiring all properties, which is the opposite of what I
want. What I want is for the data to go through the appropriate model filter,
but the actual DB to be more forgiving. I will probably need a custom validation
layer, but I'll leave this until it becomes a problem.

Speaking of problems, the next one popped up when I tried to map the above into
a view. The application isn't meant to just show some random collection of job
logs, it is intended to store and display the logs of individual users. While I
could probably just add the UserId as a foreign key and mix all the user logs
together that isn't my intension. Here is a Clojure sketch of how I would like
the data model to work:

{{< highlight clojure >}}

(defrecord User [ID Name Password Logs])

(defrecord ActivityLog [ID Date Description Location])

(defrecord ContactLog [ID Date Description Employer Contact ...])

(def some-user (->User 1 "Bob" "salted-hash"
                       [(->ActivityLog 1 date "Training" "work center")
                        (->ContactLog 2 date "Application" "Initech" ...)
                        (->ContactLog 3 date "Interview" "Megacorp" ...)]))

{{< /highlight >}}

So each user would have some kind of collection of logs they had created
associated with their account. In a database I would map that to each user
getting their own logs table. In Element Framework though I'm not sure I need to
care. I am using the auto-populated user model and just defining a collection:

{{< highlight csharp >}}

namespace JobLogger.Models
{
    public class ApplicationUser : IdentityUser
    {
        public virtual ICollection<BaseLog> JobLogs { get; set; }
    }
}

{{< /highlight >}}

Between struggles to define a coherent model I did spend some time messing with
Razor templates and working on look and feel. I have to say, bootstrap is pretty
neat. It provides a simple way to create flexible column layouts, which is
always a pain in html. It also provides some attractive minimalistic UI
elements. Best of all it handles adaptive layout in a sane way with virtually no
tweaking required.

So I didn't get all the way to a form mock-up, but I got some foundations down
to build on. If I can get the model stuff working I am really interested to see
how I can map UI elements to data (without using the canned auto-gen stuff I mean).