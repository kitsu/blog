+++
categories = ["Development"]
tags = ["CSharp", "MonoGame", "Roguelike", "code"]
date = "2016-07-30T08:04:34-07:00"
title = "Roguelike Project - Sim Infrastructure"

+++

Building out map generation and decoupling chunks from generation.
<!--more-->
<hr/><br/>
I spent some time building a skeleton for map generation. The basic organization
is the sim manager has a `ChunkSource` which it can use to request chunks. The
`ChunkSource` holds all the random/noise information and determines which
cell generator will be used for a given chunk. When a chunk is created it is
given a generator instance which it calls to generate cells. The generator knows
how to pick cells given their global coordinates and access to the random/noise
generators in the `ChunkSource`.

It is all just minimally mocked up for now, for instance there's currently only
one generator called `Grassland` which is used for every chunk. By building
these parts I discovered additional requirements for the system I have in
mind. Because of how I am handling generation I would like to have one central
place where all possible terrain types are enumerated, which is then used by all
the generators. To do this I created a `Materials` singleton which can map
between material names and simulation ids:

{{< highlight csharp >}}

class Materials
{
    public Dictionary<string, int> AllIds;
    public Dictionary<int, string> AllNames;
    Dictionary<string, int> _pitIds;
    Dictionary<int, string> _pitNames;
    Dictionary<string, int> _floorIds;
    Dictionary<int, string> _floorNames;
    Dictionary<string, int> _wallIds;
    Dictionary<int, string> _wallNames;

    private static readonly Materials instance = new Materials();

    private Materials()
    {
        int index = 1;
        index = InitOther(index);
        index = InitPits(index);
        index = InitFloors(index);
        index = InitWalls(index);
    }

    int InitOther(int index)
    {
        AllIds = new Dictionary<string, int>
        {
            {"Void", 0 },
            {"Beach", index },
        };
        AllNames = new Dictionary<int, string>();
        int id;
        string name;
        foreach (var pair in AllIds)
        {
            name = pair.Key;
            id = pair.Value;
            AllNames[id] = name;
        }
        return index + 1;
    }

    int InitPits(int index)
    {
        // Increment index for each type, left shift 8+n for each sub-type
        _pitIds = new Dictionary<string, int>
        {
            {"FreshWater.Brick", (index + 0) },
            {"FreshWater.Rock", (index + 0) << 8 },
            {"FreshWater.Smooth", (index + 0) << 9 },
            {"SaltWater.Brick", (index + 1) },
            {"SaltWater.Rock", (index + 1) << 8 },
            {"SaltWater.Smooth", (index + 1) << 9 },
            {"PoisonWater.Brick", (index + 2) },
            {"PoisonWater.Rock", (index + 2) << 8 },
            {"PoisonWater.Smooth", (index + 2) << 9 },
            {"Lava", (index + 3) },
        };
        _pitNames = new Dictionary<int, string>();
        int id;
        string name;
        foreach (var pair in _pitIds)
        {
            name = pair.Key;
            id = pair.Value;
            // Note each pair is added to the All materials maps here
            AllIds["Pit." + name] = id;
            AllNames[id] = "Pit." + name;
            _pitNames[id] = name;
        }
        return index + 4;
    }

    int InitFloors(int index)
    {
    ...
    }

    int InitWalls(int index)
    {
    ...
    }

    static public int GetPit(string name) => 
        instance._pitIds.ContainsKey(name) ? instance._pitIds[name] : 0;
    static public string GetPit(int id) =>
        instance._pitNames.ContainsKey(id) ? instance._pitNames[id] : "";
    static public int GetFloor(string name) => 
        instance._floorIds.ContainsKey(name) ? instance._floorIds[name] : 0;
    static public string GetFloor(int id) =>
        instance._floorNames.ContainsKey(id) ? instance._floorNames[id] : "";
    static public int GetWall(string name) => 
        instance._wallIds.ContainsKey(name) ? instance._wallIds[name] : 0;
    static public string GetWall(int id) =>
        instance._wallNames.ContainsKey(id) ? instance._wallNames[id] : "";
    static public int GetId(string name) => 
        instance.AllIds.ContainsKey(name) ? instance.AllIds[name] : 0;
    static public string GetName(int id) =>
        instance.AllNames.ContainsKey(id) ? instance.AllNames[id] : "";
}

{{< /highlight >}}

It will be pretty trivial to transition these to JSON data files, but for the
small sample I'm implementing I just hard-coded the material lists. Materials 
can be accessed either by their type (pit/floor/wall) or in the global mapping
by qualified name. Here is how I am currently using them:

{{< highlight csharp >}}

class Grassland: AbstractGenerator
{
    Dictionary<int, string> _mats;

    public Grassland(ChunkSource source): base(source)
    {
    }

    override public IDictionary<int, string> UsedMaterials
    {
        get
        {
            if (_mats == null)
            {
                _mats = new Dictionary<int, string>
                {
                    {0, "Void" }
                };
                // This array may be replaced by a probabilities field
                // if I can figure out a sane way to store probabilities
                var ids = new int[]
                    {
                    Materials.GetPit("FreshWater.Rock"),
                    Materials.GetId("Beach"),
                    Materials.GetFloor("Dirt"),
                    Materials.GetFloor("Dirt.Grass"),
                };
                foreach (var id in ids)
                {
                    _mats[id] = Materials.GetName(id);
                };
            }
            return _mats;
        }
    }

    public override Cell GetCell(Vector coord)
    {
        var local = Chunk.LocalCoord(coord);
        var value = _source.SampleCell(coord);
        var floor = PickFloor(value);
        //var fill = (0.5 <= value && value <= 0.6) ? "wall" : "";
        return new Cell(floor, 0);
    }

    int PickFloor(double value)
    {
        // Given a number [-1.0 1.0] map ranges to floor material
        if (value < -.8)
            return Materials.GetPit("FreshWater.Rock");
        if (value < -.4)
            return Materials.GetId("Beach");
        if (value < -.25)
            return Materials.GetFloor("Dirt");
        if (value >= -.25)
            return Materials.GetFloor("Dirt.Grass");
        return 0;
    }
}

{{< /highlight >}}

I think I have an idea how I can encode the ranges used to pick materials, which
will clean this code up a lot. One challenge I'm going to run into when I expand
this to additional terrain types is how to transition between regions. If I can
make the generation a little more data-driven though I can just create
interpolated terrain types to smooth the transitions. Here is an example of the
generated terrain:

{{< figure src="/images/Random_Map_02.JPG" alt="Random map screen capture" >}}

One of the first problems revealed by this terrain generator was a deficiency
in my graphic tile selection. Note all the extra "Beach" borders between the
dirt and the grass. The dirt's border transitions to sand, but it is used
anywhere a dirt tile is bordered by a non-dirt tile. The grass border
transitions to dirt, so it would merge seamlessly if the dirt weren't adding its
own border. I think I need some kind of affiliate system in the tile selection,
where dirt will consider grass to be a match, but grass will not match dirt.

The next problem I have is the slope of the noise gradients. I had to tweak both
the noise frequency and my material bands repeatedly to get the above result,
and it still isn't satisfactory. My first thought when I discovered the problem
was to apply a function over the noise, in this instance something that will
tighten up the areas closer to -1 and gradually flatten everything above
-0.4. It turns out SharpNoise has a module for just that purpose. Applying a
function to the entire noise field for each generator will exacerbate the
problems in transition areas, so I need to allow interpolation of those
functions in region boundaries too.

I *am* loosing momentum a little with this project. When I can focus on it again I
will probably jump back to the display side. Now that I have infinite expanses
of terrain the ability to move the view would be nice.

GitHub commit [6b0f94325](https://github.com/kitsu/PCGTest/tree/6b0f94325589bb2018b933c1b5e754a32e72f9ca).