+++
categories = ["hacks"]
date = "2016-05-17T14:31:01-07:00"
tags = ["cad", "autolisp"]
title = "AutoLisp and AutoCAD® Command Script Hackery"

+++

Working around AutoCAD quirks using AutoCAD quirks.
<!--more-->
<hr/><br/>
I got pulled away from the project I was working on today to try and automate
around a CAD content problem. At work we have three simultaneous projects with
the same client. They, because of their internal politics, are doing all the
civil survey work in-house. They do try, and they are willing to work with us
when their product is unsatisfactory, but we've gotten a lot of odd and broken
data from them.

In this particular case we received a file containing additional right-of-way and
property ownership information. Since the surveyors prepared the file with only
their own drawings in mind (as they usually do) all the text and line-work were
tweaked and manually overridden. In particular the property tags and owner
information were placed at a size and orientation that fit the survey plans, but
not ours.

After getting go ahead to fix the issue my boss came and asked if I could help
out. His plan was to somehow adjust the text and then make it *annotative*,
which just means it can be sized/oriented differently in different views.
We talked about it and played around with some solutions and decided on fixing
and decorating the text and then turning everything into an *annotative block*.
Annotative blocks allow arbitrary collections of geometry to be automatically
sized and rotated.

I only had an hour to bang something together, and although I ran into some
problems, I ended up with something that worked and was even relatively nice to
use. Since it is short here is all the code, I'll break down some of the what
and why next:

`File saved at "d:\pown_wrap.scr"`
{{< highlight scheme >}}
;; Preamble
(defun c:pown_block ( / dummy)
   (setq title (car (entsel  "Select title text"))
         name (cdr (assoc 1 (entget title)))
         dummy (princ "\n")
         note (car (entsel  "Select note text")))
   (command "script" "d:\\pown_wrap.scr")
   (princ))
;; Conditional break
(if (not title) (command "invalid"))
;; Modifications (note: bg is a custom lisp command)
select !note ;
justifytext p ;
tc;
bg  p ;
select !title ;
justifytext p ;
mc;
bg  p ;
(setq insert (cdr (assoc 10 (entget title))))
;; Express tools Lisp command
select !title ;
tcircle p ;

slots



;; Block creation and re-insertion
-block !name annotative yes yes !insert l !title !note ;
;; The insert command tried to interpret `!name` as a file path...
(command "-insert" name insert "1" "" "0")
;; Clean up
(setq title nil note nil name nil insert nil)
{{< /highlight >}}

Above is an AutoCAD script file. All the white-space is significant, especially
at the end of lines. I went with a script instead of just writing the whole
thing in lisp because AutoCAD's lisp interpreter has an odd restriction: it is
prevented from executing any command that requires use of the interpreter. This
means AutoLisp cannot automate a command that is written in AutoLisp.

In this case I want to use my own `bg` command (which turns on text masking),
and a built-in command `tcircle` which wraps text in decorative shapes. The only
way I can run those commands without user mediation is in a script, but scripts
must be a file on disk, *and* they don't allow any user interaction. So I still
need the user to run a lisp command so I can gather their input to use in the
script...

{{< highlight scheme >}}
;; Preamble
(defun c:pown_block ( / dummy)
   (setq title (car (entsel  "Select title text"))
         name (cdr (assoc 1 (entget title)))
         dummy (princ "\n")
         note (car (entsel  "Select note text")))
   (command "script" "d:\\pown_wrap.scr")
   (princ))
{{< /highlight >}}

The preamble actually defines the lisp command used to collect property titles
and ownership notes. In AutoLisp a function prepended by `c:` becomes an AutoCAD
command. Since no AutoCAD command starts with `pow` those three letters
auto-complete to this command. After collecting the DB entities the command then
runs the same script file it is defined in...

{{< highlight scheme >}}
;; Conditional break
(if (not title) (command "invalid"))
{{< /highlight >}}

The `conditional break` line is a neat hack. There is no way to conditionally
terminate script execution in AutoCAD, but it does terminate automatically when
an invalid command is executed, which "invalid" is.

So when the user drags the script into the application window the `pown_block`
command is defined, and if the lisp var `title` is not defined then execution
stops. The user then runs the `pow` command which initializes `title` and
executes the same script, which is now able to reach the "Modifications" section.

{{< highlight scheme >}}
;; Modifications (note: bg is a custom lisp command)
select !note ;
justifytext p ;
tc;
bg  p ;
select !title ;
justifytext p ;
mc;
bg  p ;
(setq insert (cdr (assoc 10 (entget title))))
;; Express tools Lisp command
select !title ;
tcircle p ;

slots



;; Block creation and re-insertion
-block !name annotative yes yes !insert l !title !note ;
;; The insert command tried to interpret `!name` as a file path...
(command "-insert" name insert "1" "" "0")
{{< /highlight >}}

From there everything is pretty standard. Note that I cannot use the `!var`
syntax to access lisp variables, again because of the re-entrance restriction.
Fortunately Autodesk fixed a bug in earlier versions of the select command so
it can now make selections! All the trailing `p`s are shorthand for previous
selection (since those commands clear the active selection for some reason).

I actually wait to grab the title's coordinate until after changing the
justification to middle center, so the block insertion point is nicely
centered. Once the block is made I clear all the vars in case the lisp has to
be manually redefined.

Now instead of a 20+ steps to convert each set of annotation you can just type
`pow` and pick a couple of pieces of text!

```html
Autodesk, and AutoCAD are registered trademarks or trademarks of Autodesk,
Inc., and/or its subsidiaries and/or affiliates in the USA and/or other
countries.

I have no affiliation with them except the use of their products.
```