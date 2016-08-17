+++
categories = ["Development"]
tags = ["CSharp", "MonoGame", "Roguelike", "code"]
date = "2016-07-11T10:37:56-07:00"
title = "Roguelike Project - Adding Abstractions"

+++

Going from a tile renderer to a map provider.
<!--more-->
<hr/><br/>
Expanding on what I posted last time I have created a manually encoded map. The
exercise let me see what the tiles look like assembled (outside of Tiled), and
also helped me think about how the larger system will work. To create the map I
built an array of strings where each string is a row and each char is a tile. I
then created an index from char to tile name for selecting tile sprites:

{{< highlight csharp >}}

class MapView : IView
{
...
 public void Draw(GameTime gameTime)
    {
        var key = new Dictionary<char, string>() {
            { '~', "BrickPit.CyanWater.Middle" },
            { '0', "BrickPit.CyanWater.OutTopRight" },
            { '1', "BrickPit.CyanWater.BottomMiddle" },
            { '2', "BrickPit.CyanWater.OutTopLeft" },
            { '3', "BrickPit.CyanWater.MiddleRight" },
            { '4', "BrickPit.CyanWater.MiddleLeft" },
            { '5', "BrickPit.CyanWater.OutBottomRight" },
            { '6', "BrickPit.CyanWater.TopMiddle" },
            { '7', "BrickPit.CyanWater.OutBottomLeft" },
            { '{', "BrickPit.CyanWater.TopLeft" },
            { '}', "BrickPit.CyanWater.TopRight" },
            { '[', "BrickPit.CyanWater.BottomLeft" },
            { ']', "BrickPit.CyanWater.BottomRight" },
            { 'g', "BrickFloor.Gray.TopLeft" },
            { 'h', "BrickFloor.Gray.TopMiddle" },
            { 'i', "BrickFloor.Gray.TopRight" },
            { 'j', "BrickFloor.Gray.MiddleLeft" },
            { 'k', "BrickFloor.Gray.MiddleRight" },
            { 'l', "BrickFloor.Gray.BottomLeft" },
            { 'm', "BrickFloor.Gray.BottomMiddle" },
            { 'n', "BrickFloor.Gray.BottomRight" },
            { '.', "BrickFloor.Gray.Middle" },
            { '#', "BrickFloor.Gray.Single" },
            { 'a', "BrickWall.LiteBlue.TopLeft" },
            { 'b', "BrickWall.LiteBlue.Horizontal" },
            { 'c', "BrickWall.LiteBlue.TopRight" },
            { 'd', "BrickWall.LiteBlue.Vertical" },
            { 'e', "BrickWall.LiteBlue.BottomLeft" },
            { 'f', "BrickWall.LiteBlue.BottomRight" },
            { 'v', "BrickWall.LiteBlue.BottomCap" },
        };
        var map = new string[] {
            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~011111111111112~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~3ghhhhhhhhhhhi4~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~3j.abbbbbc...k4~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~3j.dghhhid...k4~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~3j.dj...kd...k4~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~3j.dlmmmnd...k4~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~3j.eb#bbbf...k4~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~3j...........k4~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~3lmmmmmmmmmmmn4~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~566666666666667~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~~~~~~~~~~~~~~~~~~~01111111111111111111111112~~~~",
            "~~~~~~~~~~~~~~~~~~~~3ghhhhhhhhhhhhhhhhhhhhhhi4~~~~",
            "~~~~~~~~~~~~~~~~~~~~3j......................k4~~~~",
            "~~~~~~~~~~~~~~~~~~~~3j.............ebbbc....k4~~~~",
            "~~~~~~~~~~~~~~~~~~~~3j..{66666}........d....k4~~~~",
            "~~~~~~~~~~~~~~~~~~~~3j..4~~~~~3..abbbbbf....k4~~~~",
            "~~~~~~~~~~~~~~~~~~~~3j..4~~~~~3..d..........k4~~~~",
            "~~~~~~~~~~~~~~~~~~~~3j..4~~~~~3..d.abbbbbbc.k4~~~~",
            "~~~~~~~~~~~~~~~~~~~~3j..4~~~~~3..d.v......d.k4~~~~",
            "~~~~~~~~~~~~~~~~~~~~3j..[11111]..d........d.k4~~~~",
            "~~~~~~~~~~~~~~~~~~~~3j...........ebbbbbbbbf.k4~~~~",
            "~~~~~~~~~~~~~~~~~~~~3lmmmmmmmmmmmmmmmmmmmmmmn4~~~~",
            "~~~~~~~~~~~~~~~~~~~~56666666666666666666666667~~~~",
            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
            };
        Rectangle dest = tileRect;
        spriteBatch.Begin(SpriteSortMode.Immediate, BlendState.Opaque);
        string row;
        string tile;
        for (var y = 0; y < 30; y++)
        {
            row = map[y];
            dest.X = 0;
            for (var x = 0; x < 50; x++)
            {
                tile = key[row[x]];
                mapSprites.Draw(spriteBatch, tile, dest);
                dest.X += 16;
            }
            dest.Y += 16;
        }
        spriteBatch.End();
    }
...

}

{{< /highlight >}}

{{< figure src="/images/manual_tile_map.jpg" alt="Screen capture" >}}

For the next step I want to add the next layer of abstraction that will then
provide the map data to the map view. The simplest thing that would work would
be creating another class that either contains and calls methods on the view, or
that is contained and provides methods called by the view. That would be a very
tight coupling though. In the MVVM paradigm my `MapView` should bind to my
View-Model, and it will then receive update events when the data changes.

My View-Model for the map will provide a way to register a viewport which
defines a rectangle in simulation coordinates. The viewport will then generate
signals when anything within its bounds changes, or when the bounds themselves
change. The `MapView` can listen for those signals and update appropriately. The
`MapView` will also emit signals based on user input that the View-Model can
listen to. I'm planning on using events for signaling, which means either the
View and View-Model need to have handles to each other, or something at a higher
level needs handles for both and needs to know how to connect them together...

This is where the director comes in. The Director will create both the
`MapViewModel` and the `MapView` instances and wire up the events. The Director
manages what is created and when. Building things this way lets the Director
manage the overall game state and its transitions. To do this the Director will
need a handle for the `ViewManager` and the Simulation container, but these can
be provided by constructor injection to reduce coupling.

I've done a lot of whiteboard scribbling thinking about how to structure things.
Here is my current idea about data flow between modules:

{{< figure src="/images/20160712_104728.jpg" >}}

I'm still working on building the pieces, but the idea is:

+ The Simulation tracks cells that have some properties and optional contents.
  Change events are forwarded to various listeners: player control, simulation
  stats, map viewports, etc.
  
+ The map controller's viewports get cell information from the simulation and
  transform it into arrays of simplified data (i.e. what kind of thing goes
  where). The viewports then forward data to their listeners
  
+ The MapView then preprocesses the map data to determine which specific tile to
  show at each location. For example a floor tile with all tile neighbors to the
  SouthEast and other tiles elsewhere should become a TopLeft corner tile.

By building the view module first I've started at the wrong end of the
dependency graph. As I'm building out I keep finding I need some earlier
dependency in place before I can proceed. I'm trying hard to build things in the
smallest working chunks though. I know from experience that starting at the
other end, where constraints are few, you can rapidly grow the independent systems
to intractable complexity.

GitHub commit [ef2aba9ca](https://github.com/kitsu/PCGTest/tree/ef2aba9ca04385f4fe68bcdb3ebc6db2ea88f96a).