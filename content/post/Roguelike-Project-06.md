+++
categories = ["Development"]
tags = ["CSharp", "MonoGame", "Roguelike", "code"]
date = "2016-07-22T12:18:18-07:00"
title = "Roguelike Project - Chunks"

+++

Building the simulation side and hooking it up to the MapViewController.
<!--more-->
<hr/><br/>
I spent some time setting up unit testing and writing some initial tests. I went
with the Nunit framework, and added [FluentAssertions](http://www.fluentassertions.com/)
to clean things up a little. I picked Nunit because this project is cross
platform, and may be built by people without VS, and Nunit seems like the most
platform agnostic framework for C#.

Next I started working on building the simulation structure and map
generation. I want to allow arbitrarily large maps, which means the world map
will be larger than memory. Chunking is the standard approach to handling large
map data, just pick a width and height for the chunks and use a little modular
math:

{{< highlight csharp >}}
...
public Vector GlobalCoord(int x, int y) => GlobalCoord(new Vector(x, y));
public Vector GlobalCoord(Vector loc) => (Index * ChunkSize) + loc;

public static Vector LocalCoord(int x, int y) => LocalCoord(new Vector(x, y));
public static Vector LocalCoord(Vector coord) => coord % ChunkSize;

public static Vector Coord2Chunk(int x, int y) => Coord2Chunk(new Vector(x, y));
public static Vector Coord2Chunk(Vector coord) => coord / ChunkSize;
...
{{< /highlight >}}

The next key to pseudo-infinite maps is the ability to generate a chunk's
contents on demand. This can be achieved by using [perlin/simplex noise](http://flafla2.github.io/2014/08/09/perlinnoise.html)
for map generation. Simplex noise can be sampled with a coordinate, and
generates smooth transition gradients between coordinates. By sampling the noise
using global coordinates patterns will be consistent across chunk
boundaries. This is a pretty standard approach,
[here is a nice explanation](https://spin.atomicobject.com/2015/05/03/infinite-procedurally-generated-world/).

I am using the [SharpNoise library](https://github.com/rthome/SharpNoise),
mainly because it has a Nuget package. It has a bunch of features I don't need,
but the noise modules are really neat. You can tweak and combine noise sources
in chains or trees. I plan to have multiple noise sources controlling generation
at different scales. One low frequency source could pick biomes, while a
finer-grained noises could provide view-scale detail, and yet another could
determine the location of structures. For now I am just using a "water-line" to
pick between two tiles, and to determine where to place walls:

{{< highlight csharp >}}
...
void GenerateCells()
{
    double value;
    Vector local;
    Cell cell;
    string floor, fill;
    foreach (var global in Rect.Coordinates())
    {
        local = LocalCoord(global);
        value = _noise.GetValue(global.X, global.Y, 0);
        floor = (value <= 0.1) ? "Water" : "Brick";
        fill = (0.5 <= value && value <= 0.6) ? "Wall" : "";
        cell = new Cell(floor, fill);
        Cells[local.X, local.Y] = cell;
        _cellUpdate.OnNext(new KeyValuePair<Vector, Cell>(global, cell));
    }
}
...
{{< /highlight >}}

I am using a new random seed on every run, here is one generated map (2 chunks):

{{< figure src="/images/Random_Map_01.JPG" alt="Random map screen capture" >}}

The random content helped me fix a few bugs in my data definitions. I may still
need to specialize tile picking per feature type though. There are some problems
that are particular to the water pits.

Anyway, the tricky part was connecting the simulation data to the view. Data has
to flow in two directions, first the visible area needs to trigger chunk-loading,
then loaded chunks need to signal their contents and any changes. I already had
a `Viewport` in the control layer which knows where the view is looking in
simulation coordinates. I didn't want the viewport to know anything about chunks
though, so I added a `LoadArea` method to the `SimulationManager` that
determines which chunks to load.

Going the other direct I at first was publishing a `WhenChunkUpdates` observable,
but again I didn't want the Controller to know anything about chunks. Also
sending entire chunks on every update (even if only a single cell changed)
seemed pretty wasteful. Instead I exposed a `WhenCellUpdates` observable on the
`SimulationManager` that aggregates events from similar observables in each
loaded chunk. The viewport then filters through the cell updates and processes
any that overlap with it.

I have the viewport pushing update events every time a change is successfully
processed. This flow is optimal for single cell updates (i.e. when something
moves in the simulation) but is really bad for initial loading. Especially since
I am still pushing the entire viewport map on every update. I was going to do
by-tile updates to the view, but I'm not storing intermediate tile data so I
can't do piecewise tile resolution. It isn't that bad though because ReactiveX
provides a `Throttle` extension method that ignores observable changes until
update speed falls below a set frequency:

{{< highlight csharp >}}
...
public IView CreateMapView(MapViewController ctrl)
{
    var view = new MapView(_screen, Width, Height);
    Add(view);
    // Setup controller viewport
    var vp = ctrl.AddViewport(new Rect(0, 0, view.Width, view.Height));
    // Bind events
    ctrl.WhenAddTileKey.Subscribe(view.AddTileKey);
    ctrl.Initialize();
    vp.WhenMapChanges
        .Throttle(new TimeSpan((1/60)*1000))
        .Subscribe(view.UpdateMap);
    vp.Initialize();
    return view;
}
...
{{< /highlight >}}

The next steps are to elaborate the cell data structure, create chunk generation
infrastructure, and add simulation entities.

GitHub commit [e741b36f3](https://github.com/kitsu/PCGTest/tree/e741b36f3ffb5965ac7e8af8a28d707bf09d40ca).