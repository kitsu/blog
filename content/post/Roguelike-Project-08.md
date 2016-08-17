+++
categories = ["Development"]
tags = ["CSharp", "MonoGame", "Roguelike", "code"]
date = "2016-08-05T11:10:55-07:00"
title = "Roguelike Project - Off By One"

+++

Fixing a discrete mathematics bug and fixing scrolling responsivity.
<!--more-->
<hr/><br/>
Per my closing comment last post I switched over to the display module and tried
to implement view scrolling. I had to add keyboard input first which was painful
because of how simplistic MonoGame's input API is. My first implementation had a
problem, but it wasn't immediately obvious:

{{< highlight csharp >}}

class EventProvider
{
    private readonly Subject<Point> _click;
    public IObservable<Point> WhenClick;
    private readonly Subject<Keys[]> _keys;
    public IObservable<Keys[]> WhenKeyPressed;
    bool _down;

    public EventProvider()
    {
        _click = new Subject<Point>();
        WhenClick = _click.AsObservable();
        _keys = new Subject<Keys[]>();
        WhenKeyPressed = _keys.AsObservable();
    }

    public void Update(int elapsed)
    {
        var ms = Mouse.GetState();
        if (ms.LeftButton == ButtonState.Pressed)
        {
            _down = true;
        } else if (ms.LeftButton == ButtonState.Released && _down)
        {
            _down = false;
            _click.OnNext(ms.Position);
        }
        var ks = Keyboard.GetState().GetPressedKeys();
        if (ks.Count() > 0)
            _keys.OnNext(ks);
    }
}

{{< /highlight >}}

From there I just just subscribed the map's viewport to key events, watched for
Vi keys, updated the viewport rect location, and signal the simulation to
provide updated data. I tweaked view/control setup in anticipation of multiple
overlapping views:

{{< highlight csharp >}}
class ViewManager: IViewManager
{
    ...
    public IView CreateMapView(MapViewController ctrl)
    {
        var view = new MapView(_screen, Width, Height);
        Add(view);
        // Setup controller viewport
        var vp = ctrl.AddViewport(new Rect(0, 0, view.Width, view.Height));
        // Bind input events
        var dispose = _events.WhenKeyPressed.Subscribe(view.HandleInput);
        _handlers[view] = dispose;
        // Bind controller events
        ctrl.WhenAddTileKey.Subscribe(view.AddTileKey);
        vp.WhenMapChanges
            .Throttle(new TimeSpan((1/60)*1000))
            .Subscribe(view.UpdateMap);
        // Let controller initialize
        vp.Initialize(view);
        ctrl.Initialize(view);
        return view;
    }
    ...
}

class MapView: BaseView, IMapView
{
    ...
    private readonly Subject<char> _move;
    public IObservable<char> WhenMove => _move.AsObservable();
    ...

    public void HandleInput(Keys[] pressed)
    {
        // Note this ordering incidentally imposes a direction preference
        if (pressed.Contains(Keys.K))
        {
            _move.OnNext('N');
        } else if (pressed.Contains(Keys.J))
        {
            _move.OnNext('S');
        } else if (pressed.Contains(Keys.H))
        {
            _move.OnNext('W');
        } else if (pressed.Contains(Keys.L))
        {
            _move.OnNext('E');
        }
    }
    ...
}

public class Viewport: IDisposable
{
    ...
    public void Initialize(IMapView view)
    {
        //FIXME Bind view move observable directly to move viewport rect
        view.WhenMove.Subscribe(MoveView);
        // Trigger chunk load/touch
        _sim.LoadArea(Bounds);
    }
    ...

    void MoveView(char dir)
    {
        // Given one of NESW move the rect Up, Right, Down, or Left
        switch (dir)
        {
            case 'N':
                Bounds.Y -= 1;
                break;
            case 'S':
                Bounds.Y += 1;
                break;
            case 'E':
                Bounds.X += 1;
                break;
            case 'W':
                Bounds.X -= 1;
                break;
        }
        _sim.LoadArea(Bounds);
    }
    ...
}

{{< /highlight >}}

It sort of worked too. I was getting some weird delays and visual bugs when
scrolling beyond the initial view though. I wasn't entirely sure how to debug
the problem, but I knew my lagging test coverage wasn't helping. I started by
adding tests for the chunk math from a couple posts back. While writing those
tests though I discovered a logical inconsistency with my rect
implementation. It is a little hard to explain, but the problem has to do with
the difference between indices and areas, and how area is counted.

{{< figure src="/images/20160805_132244.jpg" title="Chess vs Go sketch" >}}

Basically if I had a rect that started at x = 0 with a size of 5 it would
enumerate x coordinates [0, 1, 2, 3, 4, 5], which would be six squares instead
of 5. Fixing that bug broke some tests and chunk creation. Once I cleaned up the
mess though scrolling behaved a little better.

The next obvious problem was a delay between when a new chunk moves into view
and when the chunk loads. It seemed like my area->chunk logic was a little off,
but I already knew of one change needed to that code. The area loading code only
loaded chunks that overlap with the provided area. In order to amortize chunk
loading wait times I wanted to pre-load neighboring chunks too. By expanding the
provided area by one chunk all around I made loading take longer per operation,
but reduced the frequency chunks need to be generated. It also fixed the visible
loading error.

The last remaining problem was when loading occurred it seemed like multiple key
presses were registered. The view would freeze because of the loading, then
scroll multiple tiles all at once. In order to solve this I decided to try
moving loading into a separate thread. My plan all along was to move all sim
processing to a separate thread, and this seemed like a good place to start. I
want to develop a much more rigorous framework for separating and managing sim
computation, but at this stage just adding a couple of `Task.Run` calls achieved
the goal:

{{< highlight csharp >}}

public class Chunk
{
...
public void Initialize()
    {
        Task.Run(() => GenerateCells());
    }
...
}

public class Viewport: IDisposable
{
    ...
    public void Initialize(IMapView view)
    {
        //FIXME Bind view move observable directly to move viewport rect
        view.WhenMove.Subscribe(MoveView);
        // Trigger chunk load/touch
        //_sim.LoadArea(Bounds);
        Task.Run(() => _sim.LoadArea(Bounds));
    }
    ...

    void MoveView(char dir)
    {
        // Given one of NESW move the rect Up, Right, Down, or Left
        switch (dir)
        {
            case 'N':
                Bounds.Y -= 1;
                break;
            case 'S':
                Bounds.Y += 1;
                break;
            case 'E':
                Bounds.X += 1;
                break;
            case 'W':
                Bounds.X -= 1;
                break;
        }
        //_sim.LoadArea(Bounds);
        Task.Run(() => _sim.LoadArea(Bounds));
   }
    ...
}


{{< /highlight >}}

With those changes I could suddenly zip around the map with no perceptible lag!
It was really neat to play with, but was a little hard to control. Once the lag
was gone I found I couldn't move a single tile at a time, which would make
playing very punishing. Finally I came back to the actual problem that caused
the jumping: my input handling generating key events on every update, 60 times a
second! What I really wanted was one event on initial key press, and no
additional events until the key was released and re-pressed. Here are my
changes to the event provider:

{{< highlight csharp >}}

class EventProvider
{
    private readonly Subject<Point> _click;
    public IObservable<Point> WhenClick;
    private readonly Subject<Keys[]> _keys;
    public IObservable<Keys[]> WhenKeyPressed;
    bool _down;
    // Set of keys currently pressed
    HashSet<Keys> _pressed;

    public EventProvider()
    {
        _click = new Subject<Point>();
        WhenClick = _click.AsObservable();
        _keys = new Subject<Keys[]>();
        WhenKeyPressed = _keys.AsObservable();
        _pressed = new HashSet<Keys>();
    }

    public void Update(int elapsed)
    {
        var ms = Mouse.GetState();
        if (ms.LeftButton == ButtonState.Pressed)
        {
            _down = true;
        } else if (ms.LeftButton == ButtonState.Released && _down)
        {
            _down = false;
            _click.OnNext(ms.Position);
        }
        // Preproccess keys to remove continuing presses
        var ks = UpdateKeys(Keyboard.GetState().GetPressedKeys());
        if (ks.Count() > 0)
            _keys.OnNext(ks);
    }

    Keys[] UpdateKeys(Keys[] keys)
    {
        var pressed = new HashSet<Keys>(keys);
        // set.except produces the difference between sets
        var novel = pressed.Except(_pressed);
        _pressed = pressed;
        return novel.ToArray();
    }
}

{{< /highlight >}}

It might be better to produce an enumerable of new presses, or to stream the
events one at a time. I haven't decided yet.

There is one remaining visual bug. Tiles along the bottom and right sides of the
view are being processed incorrectly by the `TileSolver`. I guess next I will
look into fixing that and the other problems I've found with the solver.

GitHub commit [bc7e99a79](https://github.com/kitsu/PCGTest/tree/bc7e99a794e46673b0bd34ab95b41f8f525d1d8c).