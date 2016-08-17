+++
categories = ["Development"]
tags = ["CSharp", "MonoGame", "Roguelike", "code"]
date = "2016-07-18T19:49:43-07:00"
title = "Roguelike Project - Tile Selection"

+++

Automatically selecting graphic tile variation based on neighborhood.
<!--more-->
<hr/><br/>
Well it took longer than I expected, but I now have automatic tile selection
working. That means the controller can pass the view a map of generic tiles -
this cell is a wall of this material, this is a floor of that material - and the
view can decide which specific sprite goes where - this is a top left corner,
that is a single vertical top.

There are a few basic algorithms needed to get this working, but the biggest
hurdle was the shear number of conditions that needed to be classified. If I had
started with the code rather than the graphics I could have made things more
regular and thus easier on myself. Since the code was motivated by the graphics
though I went with brute-force encoding.

Here is the problem statement: Given a random tile in a map determine how it
relates to adjacent tiles and pick an appropriate sprite. For my map I have 3.5
classes of connecting tiles: Walls, Floors, Pits, and UI. Each class has a
different selection of connecting sprites for different configurations. For
example walls have no field, only edge connections, while floors have a field,
but don't have outside corners. Here is my current
[map sprite-sheet](https://github.com/kitsu/PCGTest/blob/ef2aba9ca04385f4fe68bcdb3ebc6db2ea88f96a/PCGTest/Content/map.png),
and here is the enumeration I'm using for my relations:

{{< highlight csharp >}}

enum TileType
{
    Border,
    Single,
    SingleTop,
    SingleBottom,
    SingleLeft,
    SingleRight,
    TeeTop,
    TeeBottom,
    TeeLeft,
    TeeRight,
    Horizontal,
    Vertical,
    Cross,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
    OutTopLeft,
    OutTopRight,
    OutBottomLeft,
    OutBottomRight,
}

{{< /highlight >}}

Border is a special value, but the others should be self explanatory.

I am choosing variations based on which of a tile's eight neighbors is of the
same type as it. I came up with a way to encode that information into a single
integer value. First I somewhat arbitrarily unwrapped the cells in the order
`[TopLeft, TopMiddle, TopRight, MiddleLeft, MiddleRight, BottomLeft, BottomMiddle, BottomRight]`.
I then assigned each cell a value of 1 if it was of the same type as the center
or 0 otherwise (e.g. `[0,1,1,0,1,0,0,0]`). By adding and using left bitshift
`<<` I merged the values into an 8-bit integer with `TopLeft` as the high bit:
`01101000` = `104`:

{{< figure src="/images/neighborhood.png" alt="Neighborhood" >}}

{{< highlight csharp >}}

static Tuple<int, int>[] offsets = new []
{
    new Tuple<int, int>(-1, -1),
    new Tuple<int, int>(0, -1),
    new Tuple<int, int>(1, -1),
    new Tuple<int, int>(-1, 0),
    new Tuple<int, int>(1, 0),
    new Tuple<int, int>(-1, 1),
    new Tuple<int, int>(0, 1),
    new Tuple<int, int>(1, 1),
};

static int CalculateNeighborhood(int x, int y, int[,] map)
{
    var tile = map[x, y];
    int bits = 0;
    foreach (var pair in offsets)
    {
        bits <<= 1;
        if (map[x + pair.Item1, y + pair.Item2] == tile)
            bits += 1;
    }
    return bits;
}

{{< /highlight >}}

From there the combinatorial problem becomes obvious: eight bits represent 256
possible combinations of like/unlike tiles. Encoding a tiles neighborhood is
cheap, but how do you determine which configuration that pattern matches? In the
end I didn't come up with any clever solution, I just generated all 256
combinations and visually classified each one. Here is part of the Tiled map I
used:

{{< figure src="/images/TileTypes.png" alt="Tile Type Enumeration" >}}

I stored the result as a mapping from `TileType => int[]` where the type matches
when the array contains the pattern under test.

{{< highlight csharp >}}

static Dictionary<TileType, int[]> Patterns = new Dictionary<TileType, int[]>()
{
    { TileType.Border,
        new[] { -1 }},
    {TileType.Single,
            new[] { 0, 1, 4, 5, 32, 33, 36, 37, 128, 129, 132, 133, 160,
                    161, 164, 165 }},
    {TileType.SingleTop,
            new[] { 2, 3, 6, 7, 34, 35, 38, 39, 130, 131, 134, 135, 162, 163,
                    166, 167 }},
    {TileType.SingleBottom,
            new[] { 64, 65, 68, 69, 96, 97, 100, 101, 192, 193, 196, 197,
                    224, 225, 228, 229 }},
    {TileType.SingleLeft,
            new[] { 8, 9, 12, 13, 40, 41, 44, 45, 136, 137, 140, 141, 168,
                    169, 172, 173 }},
    {TileType.SingleRight,
            new[] { 16, 17, 20, 21, 48, 49, 52, 53, 144, 145, 148, 149, 176,
                    177, 180, 181 }},
    {TileType.TeeTop,
            new[] { 26, 27, 30, 31, 58, 59, 62, 63, 154, 155, 158, 159, 186,
                    187, 190, 191 }},
    {TileType.TeeBottom,
            new[] { 88, 89, 92, 93, 120, 121, 124, 125, 216, 217, 220, 221,
                    248, 249, 252, 253 }},
    {TileType.TeeLeft,
            new[] { 74, 75, 78, 79, 106, 107, 110, 111, 202, 203, 206, 207,
                    234, 235, 238, 239, 242, 243 }},
    {TileType.TeeRight,
            new[] { 82, 83, 86, 87, 114, 115, 118, 119, 210, 211, 214, 215,
                    246, 247 }},
    {TileType.Horizontal,
            new[] { 24, 25, 28, 29, 56, 57, 60, 61, 152, 153, 156, 157,
                    184, 185, 188, 189, 230, 231 }},
    {TileType.Vertical,
            new[] { 66, 67, 70, 71, 98, 99, 102, 103, 194, 195, 198, 199,
                    226, 227 }},
    {TileType.Cross,
            new[] { 90, 91, 94, 95, 122, 123, 126, 218, 219, 222,
                    250, 255 }},
    {TileType.TopLeft,
            new[] { 11, 15, 43, 47, 139, 143, 171, 175 }},
    {TileType.TopRight,
            new[] { 22, 23, 24, 54, 55, 150, 151, 182, 183 }},
    {TileType.BottomLeft,
            new[] { 104, 105, 108, 109, 232, 233, 236, 237 }},
    {TileType.BottomRight,
            new[] { 208, 209, 212, 213, 240, 241, 244, 245 }},
    {TileType.OutTopLeft,
            new[] { 10, 14, 42, 46, 138, 142, 170, 174, 251 }},
    {TileType.OutTopRight,
            new[] { 18, 19, 50, 51, 146, 147, 178, 179, 254 }},
    {TileType.OutBottomLeft,
            new[] { 72, 73, 76, 77, 200, 201, 204, 205, 127 }},
    {TileType.OutBottomRight,
            new[] { 80, 81, 84, 85, 112, 113, 116, 117, 223 }},
};

{{< /highlight >}}

I noticed a pattern where types came in pairs. I think if I had picked my bit
order more carefully I could have used a `Trie` to map each bit pattern to a
`TileType` for faster lookup. This works for now though.

One problem with this approach of resolving the graphics at the latest possible
point is that I lose context information. My view's map ends at the edge of the
viewport, but the simulation map doesn't, so how can I pick the right tiles on
the edges of the map? That is what `TileType.Border` is for: cheating. Instead
of showing the wrong tile and having it change when I get additional map
information, I just hide the edge tiles and skip their calculation. I like the
*letterbox* look it gives, and I intend to use most of that area for UI elements
anyway. In the case you didn't want the black border you could instead just load
one tile worth of data all around that isn't shown, though that is sort of the
same solution.

{{< figure src="/images/AutoTiled.JPG" alt="Screenshot" >}}

{{< highlight csharp >}}

static bool IsBorder(int x, int y, int[,] map)
{
    int width = map.GetLength(0);
    int height = map.GetLength(1);
    return x == 0 || y == 0 || x == width - 1 || y == height - 1;
}

public static TileType SolveTile(int x, int y, int[,] map)
{
    if (IsBorder(x, y, map))
    {
        return TileType.Border;
    }
    else
    {
        var hood = (CalculateNeighborhood(x, y, map));
        foreach (var pair in Patterns)
        {
            if (pair.Value.Contains(hood))
                return pair.Key;
        }
    }
    return TileType.Border;
}

{{< /highlight >}}

In the limited manual testing I've done the results look good. The code is in
[commit 8e6cf658](https://github.com/kitsu/PCGTest/tree/8e6cf6583bd0315ce4b9d2702da771fe608b16f9),
along with a bunch of other code. I should have commited earlier, but so many
things were half broken waiting for other parts to catch up, it all ended up
being one big transformation.

Next up I'm at the stage of the project where there is enough structure to start
adding tests. I want to have testing sorted before starting the simulation
module since that part will benefit from being more test-driven. Luckily, unlike
my last project, any old test framework should just work out of the box.

GitHub commit [3bb38a769](https://github.com/kitsu/PCGTest/tree/3bb38a769db79eaa6a84438d408bb1b9c3817e38).