+++
categories = ["Development"]
tags = ["C#", "MonoGame", "Roguelike", "code"]
date = "2016-07-12T17:42:23-07:00"
title = "Roguelike Project - Reactivity"

+++

Replacing events and delegates with observables.
<!--more-->
<hr/><br/>
Today I was watching some Pluralsight videos while finishing my prototype
MapViewController. In the final module of the course the presenter was showing
his implementation of an exercise where he used something called Reactive
Extensions. I was only half paying attention, but when I heard "Reactive" I had
to rewind. He used the library to simulate a hardware event source, using it
to provide a stream of values over time. It seemed like it could also be use to
create observable properties perfect for MVVM data binding.

Looking around it seems that "Reactive Extensions (Rx)" was abandoned
around 2012. All the tutorials and references are dated from around that time.
Looking a bit farther though I found that the project has been expanded to
many languages and runtimes at: [ReactiveX](http://reactivex.io/). The DotNet
library has been renamed to "System.Reactive" and lives at
https://github.com/Reactive-Extensions/Rx.NET. There is a online book availible
at http://www.introtorx.com/ that serves as a tutorial and reference.

Right at the beginning of the book the author describes `Subject<T>` which
provides an object that can be subscribed to and pushes updates to all
subscribers, which is perfect for my use case. He then goes on to strongly
recommend against using them! He then describes other ways to create
observables, none of which fit my needs.

He does have some good reasons for avoiding Subjects, mainly around laziness and
functional purity. After some additional research I found [this blog post](http://davesexton.com/blog/post/To-Use-Subject-Or-Not-To-Use-Subject.aspx)
which validates my impression that Subjects are the right choice for my use
case. Specifically I don't have any external data source, or an existing
observable or event, to use as an observable. I am providing the root generators
of value streams.

Before finding Rx I had defined some delegates and events for updating my view
from my controller. Here is what I had:

{{< highlight csharp >}}

namespace PCGTest.Director
{
    public delegate void UpdateTileKeysHandler(Dictionary<int, string> keys);
    public delegate void AddTileKeyHandler(int key, string value);
    public delegate void RemoveTileKeyHandler(int key);

    class MapViewController
    {
        public event UpdateTileKeysHandler UpdateTileKeys;
        public event AddTileKeyHandler AddTileKey;
        public event RemoveTileKeyHandler RemoveTileKey;
        ...
        
        public void Initialize()
        {
            var keys = new Dictionary<int, string>() {
                { 0, "SolidBlack" },
                { 1, "BrickPit.CyanWater" },
                { 2, "BrickFloor.Gray" },
                { 3, "BrickWall.LiteBlue" },
            };
            OnUpdateTileKeys(keys);
            ...
        }

        public void OnUpdateTileKeys(Dictionary<int, string> keys)
        {
            UpdateTileKeys(keys);
        }
        ...
    }
    
    class GameManager
    {
        ...
        void CreateMapView()
        {
            // Setup map view and controller
            mapView = _viewMan.CreateMapView();
            mapCtrl = new MapViewController();
            ...
            // Bind events
            mapCtrl.UpdateTileKeys += mapView.UpdateTileKeys;
            mapCtrl.AddTileKey += mapView.AddTileKey;
            mapCtrl.RemoveTileKey += mapView.RemoveTileKey;
            ...
            // Initialize map
            mapCtrl.Initialize();
        }
        ...
    }
}

{{< /highlight >}}

I was going to try to simplify the code until I found Rx. Also I still haven't
decided how exactly all the pieces will fit together. Here is the new code with
Subjects serving as update sources:

{{< highlight csharp >}}

namespace PCGTest.Director
{
    class MapViewController
    {
        // Pairs of subjects and public observables
        private readonly Subject<KeyValuePair<int, string>> _addTileKeys;
        public IObservable<KeyValuePair<int, string>> AddTileKeys;
        private readonly Subject<int> _subTileKeys;
        public IObservable<int> SubTileKeys;
        ...

        public MapViewController()
        {
            _addTileKeys = new Subject<KeyValuePair<int, string>>();
            AddTileKeys = _addTileKeys.AsObservable();
            _subTileKeys = new Subject<int>();
            SubTileKeys = _subTileKeys.AsObservable();
        }
        
        public void Initialize()
        {
            var keys = new Dictionary<int, string>() {
                { 0, "SolidBlack" },
                { 1, "BrickPit.CyanWater" },
                { 2, "BrickFloor.Gray" },
                { 3, "BrickWall.LiteBlue" },
            };
            foreach (var key in keys)
            {
                _addTileKeys.OnNext(key);
            }
            ...
        }
        ...
    }

    class GameManager
    {
        ...
        void CreateMapView()
        {
            // Setup map view and controller
            mapView = _viewMan.CreateMapView();
            mapCtrl = new MapViewController();
            ...
            // Bind events
            mapCtrl.AddTileKeys.Subscribe(mapView.AddTileKey);
            ...
        }
        ...
    }
}


{{< /highlight >}}

Using Subjects also allows me to change how my code is structured. Because they
are first-class I could pass the Subject observables into the view and let it
setup all the subscriptions. With my continued research on the MVVM pattern I've
run across the advice that the views should be given their view-model so they
can handle their own setup/tear-down. Like I said - I'm still not clear on what
the best structure is going to be.

Looking at naming conventions it seems like the observables should be prefixed
with "When". So `_addTileKey` fires `WhenTileAdded` which pushes to `AddTile`.

Switching to Rx has given me some ideas for how to build the simulation module
and how to tie it to the controllers. First all the Subjects really belong in
the Simulation. The controllers will then expose observables that filter and
preprocess the raw simulation data streams. I should also be able to add a layer
of asynchrony between the view and the simulation with simulation updates
happening in a background thread.

Anyway, I think I have enough figured out to start building the Simulation. I
also need to write the `TileSolver` to handle picking tile sprites, but that
will be much easier with a proper map source in place.