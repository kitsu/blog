+++
categories = ["Development"]
tags = ["C#", "MonoGame", "Roguelike"]
date = "2016-07-08T10:10:08-07:00"
title = "Roguelike Project - Tentative Start"

+++

Starting to start a new project.
<!--more-->
<hr/><br/>
After an extra long fourth of July break I'm am thinking about starting a new
project. I always feel like I wait to long to start posting about these things,
so this time I'm getting a head start. I'm thinking about building a 2D tile
based [roguelike](http://www.roguebasin.com/index.php?title=Main_Page)... thing.
My main interests are playing around with tile based pixel graphics, playing
around with [Procedural Generation](http://pcg.wikidot.com/), and building some
kind of interesting simulation.

Over the past couple of days I've been researching and experimenting with game
frameworks programmable with C#. I really liked [CocosSharp](https://github.com/mono/CocosSharp)
and was going to go with it, but it doesn't provide an easy way to build a
desktop app. I would like whatever I make to run on Android, but running on
Windows desktop is my first priority (since that is my dev environment). I have
settled on [MonoGame](http://www.monogame.net/) even though I don't particularly
like it. It is both a heavy weight framework, and very low level. It also has
practically no documentation. I thought a number of times that I would rather
just use the HTML5 canvas but I really want to get some more experience with C#.

I also spent some time looking for assets. I found a really awesome and huge set
of sprites: [DawnLike - 16x16](http://opengameart.org/content/dawnlike-16x16-universal-rogue-like-tileset-v181).
It apparently started as a tileset for NetHack, but now contains all kinds of
things. I actually don't like the wall tiles, but it will be great to get
started with. I also downloaded [Tiled](http://www.mapeditor.org/) the map
editor. I don't plan on using pre-built maps, but it has been handy for
experimenting with the tileset, and I used it to reorganize everything into a
few large sprite sheets.

After installing MonoGame last night and creating a new empty project I spent
quite a while staring at the default template trying to decide where to start. I
really wanted to just jump in and get some graphics on the screen, but even that
will require some infrastructure. The first problem though is where to put
stuff. I spent a little time this morning at the white board sketching out the
system I'm going to aim for. Afterward I wrote up a little readme file to record
and solidify my ideas.

Here is my white board sketch:
{{< figure src="/images/20160708_123914.jpg" >}}

I want something sort of like MVVM. I changed the names to better reflect the
intent of each module:

+ Simulation - This is the model, in charge of game objects and their interactions.
+ Display - This is the view, in charge of rendering graphics to the screen.
+ Director - This module connects the simulation to the display.

I want the display and simulation to be completely isolated from each other. The
display will tick at 60hz and manage all the animation and raw input. The
simulation will be ticked manually by the director. I also want to play around
with custom events for communicating changes to the director. The structure
isn't completely clear to me yet, but I at least have a place to start building
the rendering code.

Here is a neat MVVM graphic I found [in this Microsoft article](https://msdn.microsoft.com/en-us/library/ff798384.aspx)
{{< figure src="/images/IC416621.png" >}}