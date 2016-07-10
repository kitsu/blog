+++
categories = ["Development"]
tags = ["C#", "MonoGame", "Roguelike", "code"]
date = "2016-07-09T16:39:32-07:00"
title = "Roguelike Project - Tile Rendering"

+++

Loading sprite maps and rendering animations.
<!--more-->
<hr/><br/>
I've gotten quite a way building the display module. I started by defining the
`IView` interface, and building a `ViewManager` to contain the view
collection. From there I started building the `MapView` which is the main game
view. To begin with I just loaded the sprite map texture and manually specified
the source and destination rects:

{{< highlight csharp >}}

class MapView : IView
{
    GraphicsDevice _screen;
    SpriteBatch spriteBatch;
    Texture2D mapSprites;
    Rectangle tileRect;

    public MapView(GraphicsDevice screen)
    {
        _screen = screen;
        spriteBatch = new SpriteBatch(screen);
        tileRect = new Rectangle(0, 0, 16, 16);
    }

    public void LoadContent(ContentManager content)
    {
        mapSprites = content.Load<Texture2D>("map");
    }

    public void Draw(GameTime gameTime)
    {
        Rectangle source = tileRect;
        source.X = 7 * 16;
        source.Y = 26 * 16;
        Rectangle dest = tileRect;
        spriteBatch.Begin(SpriteSortMode.Immediate, BlendState.Opaque);
        for (var y = 0; y < 30; y++)
        {
            dest.X = 0;
            for (var x = 0; x < 50; x++)
            {
                spriteBatch.Draw(mapSprites, dest, source, Color.White);
                dest.X += 16;
            }
            dest.Y += 16;
        }
        spriteBatch.End();
    }
}

{{< /highlight >}}

I then switched to a Dictionary mapping a tile name to the tile's source rect.
That worked pretty well, but I had in mind that I would load the sprite map data
from a json file. In preparation I created a wrapper class:
{{< highlight csharp >}}

class SpriteMap
{
    Texture2D _spriteMap;
    int Width;
    int Height;
    int Size;
    Dictionary<string, Rectangle> Sprites;

    public SpriteMap(Texture2D spriteMap, int width, int height, int size = 16)
    {
        _spriteMap = spriteMap;
        Width = width;
        Height = height;
        Size = size;
        Sprites = new Dictionary<string, Rectangle>();
        // FIXME replace manually added water sprites
        Sprites["BrickPit.CyanWater.Middle.0"] = new Rectangle(7 * 16, 26 * 16, 16, 16);
        Sprites["BrickPit.CyanWater.Middle.1"] = new Rectangle(13 * 16, 26 * 16, 16, 16);
    }

    public void Draw(SpriteBatch spriteBatch, string tile, Rectangle dest)
    {
        if (Sprites.ContainsKey(tile))
        {
            var source = Sprites[tile];
            spriteBatch.Draw(_spriteMap, dest, source, Color.White);
        }
    }
}

{{< /highlight >}}

Some of the sprites are animated (only two frames) so I added some timing
code and alternated between source rects to test animation. I wasn't really
happy just storing rects for each sprite, and I wanted somewhere to store
animation parameters and state, so I added a struct to represent a tile in a
sprite map:
{{< highlight csharp >}}

public struct TileSprite
{
    // Animated indicates second frame is available
    // Flipped indicates second frame is shown
    public bool Animated, Flipped;
    // How long to show each side in milliseconds
    // and ellapsed time since last flip
    public int ForeDuration, TotalDuration, Ellapsed;
    // Front and back source rects
    public Rectangle Fore, Back;

    public TileSprite(Rectangle fore)
    {
        Animated = false;
        Flipped = false;
        ForeDuration = 0;
        TotalDuration = 0;
        Ellapsed = 0;
        Fore = fore;
        Back = fore;
    }
    public TileSprite(Rectangle fore, int foreDuration,
                      Rectangle back, int totalDuration)
    {
        Animated = true;
        Flipped = false;
        ForeDuration = foreDuration;
        TotalDuration = totalDuration;
        Ellapsed = 0;
        Fore = fore;
        Back = back;
    }
}

{{< /highlight >}}

To update the animation state of the tiles I added an Update method to the
`SpriteMap`, `IView`, and `ViewManager` that is called in the `Game.Update`
method. I also changed from storing `Rectangle`s in each tile to storing just the
sprite index. I'm using an old trick for mapping an index to a 2d coordinate,
and memoizing the result:
{{< highlight csharp >}}

class SpriteMap
{
    Texture2D _spriteMap;
    int Width;
    int Height;
    int Size;
    Dictionary<int, Rectangle> _rectCache;
    Dictionary<string, TileSprite> Sprites;

    public SpriteMap(Texture2D spriteMap, int width, int height, int size = 16)
    {
        _spriteMap = spriteMap;
        Width = width;
        Height = height;
        Size = size;
        _rectCache = new Dictionary<int, Rectangle>();
        Sprites = new Dictionary<string, TileSprite>();
        // FIXME replace manually added water sprites
        Sprites["BrickPit.CyanWater.Middle"] = new TileSprite(1671, 1677, 500, 1000)
    }

    public void Update(GameTime gameTime)
    {
        TileSprite sprite;
        foreach (var key in Sprites.Keys.ToList())
        {
            sprite = Sprites[key];
            if (sprite.Animated)
            {
                sprite.Ellapsed += gameTime.ElapsedGameTime.Milliseconds;
                if (sprite.Ellapsed > sprite.TotalDuration)
                {
                    sprite.Flipped = false;
                    sprite.Ellapsed = 0;
                } else if (sprite.Ellapsed > sprite.ForeDuration)
                {
                    sprite.Flipped = true;
                }
                Sprites[key] = sprite;
            }
        }
    }

    public void Draw(SpriteBatch spriteBatch, string tile, Rectangle dest)
    {
        if (Sprites.ContainsKey(tile))
        {
            var sprite = Sprites[tile];
            var index = sprite.Fore;
            if (sprite.Flipped)
                index = sprite.Back;
            var source = Index2Rect(index);
            spriteBatch.Draw(_spriteMap, dest, source, Color.White);
        }
    }

    public Rectangle Index2Rect(int index)
    {
        if (_rectCache.ContainsKey(index))
        {
            return _rectCache[index];
        }
        var y = index / Width;
        var x = index - (y * Width);
        var rect = new Rectangle(x * Size, y * Size, Size, Size);
        _rectCache[index] = rect;
        return rect;
    }
}

{{< /highlight >}}

The last step in creating my sprite data source was loading the sprite map
definition from file. I'm managing my resource files manually using Visual
Studio. I found some MSDN articles showing
[how to include content in a build](https://msdn.microsoft.com/en-us/library/ff434501.aspx),
and [how to get a file handle for included content](https://msdn.microsoft.com/en-us/library/bb199094.aspx).
I'm using Newtonsoft.Json for parsing. The Newtonsoft Json library is awesome -
it can consume json and produce complex nested objects. The only problem I ran
into was that my struct constructor's weren't used and I had to explicitly
specify the animated flag in my json. I also had to delay setting the SpriteMap
texture since that is context dependent:
{{< highlight csharp >}}

class SpriteMap
{
    ...
    public static SpriteMap FromJson(string filename)
    {
        string json;
        using (var stream = TitleContainer.OpenStream(filename))
        {
            var sreader = new System.IO.StreamReader(stream);
            json = sreader.ReadToEnd();
        }
        var spriteMap = JsonConvert.DeserializeObject<SpriteMap>(json);
        return spriteMap;
    }

    public SpriteMap(int width, int height, int size = 16)
    {
        ...
    }
    
    public void SetSpriteMap(Texture2D spriteMap)
    {
        _spriteMap = spriteMap;
    }
    ...
}

{{< /highlight >}}

And here is my initial json file for my map spritemap:
{{< highlight json >}}

{
  "Width": 64,
  "Height": 32,
  "Size": 16,
  "Sprites": {
    "SolidBlack": {
      "Fore": 0
    },
    "SolidMauve": {
      "Fore": 1
    },
    "BrickPit.CyanWater.Middle": {
      "Animated": true,
      "Fore": 1671,
      "Back": 1677,
      "ForeDuration": 500,
      "TotalDuration": 1000
    }
  }
}

{{< /highlight >}}

It all works too. The water animation is really minimal, but looks very
nice. Now I just need to work on adding tile definitions.

I've created a repo at https://github.com/kitsu/PCGTest, the code for this post
is in commit [694bd101](https://github.com/kitsu/PCGTest/tree/694bd10174b6bab28cd1c6074158373cb3edb920).