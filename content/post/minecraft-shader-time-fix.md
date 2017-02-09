+++
categories = ["Hacks"]
tags = ["Minecraft", "GLSL"]
date = "2017-02-02T10:20:43-08:00"
title = "Minecraft Shader Day Length Fix"

+++

And now for something completely different - hacking some GLSL.
<!--more-->
<hr/><br/>
So, admission of an addict, I play *heavily* modded Minecraft
semi-frequently. Sometimes other peoples modpacks (mostly FTB third party), and
sometimes my own assemblages. I've never published anything, though I've thought
about it occasionally. My current effort's primarily motivation is to play with
[Open Computers](http://ocdoc.cil.li/) (especially robots).

Playing modded Minecraft I've gotten used to falling farther and farther behind
the latest release. The [Forge](https://files.minecraftforge.net/) project is
not officially supported by Mojang/Microsoft, and new version compatibility
relies on the hard work of dedicated volunteers. At the time of writing the
latest Official version is 1.11.2 (which surprisingly already has a Forge build)
while I have been on 1.7.10 for nearly as long as I can remember.

The reason I bring it up is because for this pack I decided to switch to 1.10.2
(which is the current stable Forge version). When I had enough built to try it
out I found that rendering in this new version was *much* smoother on my laptop
(Dell XPS13 2015, Intel HD5500 graphics). I have for a long time wanted to try
[the shaders mod](http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/1286604-shaders-mod-updated-by-karyonix),
but I was worried about performance and compatibility. Since I was trying new
things anyway I decided to throw shaders in the mix. Turns out in 1.10.2 you
have to use [OptiFine](https://optifine.net/home) for shaders, and it turns out
my HD5500 handles shaders alright!

{{< figure src="/images/MC_Shaders_03.jpg" alt="Reflections across a local lake" >}}
{{< figure src="/images/MC_Shaders_02.jpg" alt="Shady pasture with eerie forest in BG" >}}
{{< figure src="/images/MC_Shaders_01.jpg" alt="New home in progress W/ C&Bs" >}}

After playing and tweaking with my pack for a while one thing I really wished
was that the days were longer. With shaders, especially the dawn and the dusk,
are very dark. It seemed like there were only a few minutes of usable light each
day. I added a mod called
[Precise Time](https://minecraft.curseforge.com/projects/precise-time) which
allows the adjustment of day and night length, but I ended up with multiple
sun-rises and sun-sets per day. It turns out the shaders have their day-night
cycle hard coded.

After researching all over I still didn't have a clear picture of how shaders
handled time of day, let alone an example of what I wanted to do. I was
motivated though, so I unzipped the shader pack that I liked best
[Chocapic13 V6 Low](http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/1293898-chocapic13s-shaders)
and started digging around. The root of the folder has the shaders for the
overworld, and their are sub-folders for the Nether (World-1) and the End
(World1). There are a bunch of files that come in pairs of `foo.fsh` and
`foo.vsh`, for fragment and vertex shaders I assume? A cursory investigation of
`composite.vsh` turned up a `uniform int worldTime;` and a main function:

{{< highlight c >}}

void main() {
	vec4 tpos = vec4(sunPosition,1.0)*gbufferProjection;
	tpos = vec4(tpos.xyz/tpos.w,1.0);
	vec2 pos1 = tpos.xy/tpos.z;
	lightPos = pos1*0.5+0.5;


	const vec3 moonlight = vec3(0.5, 0.9, 1.4) * 0.0012;
	/*--------------------------------*/
	gl_Position = ftransform();
	texcoord = (gl_MultiTexCoord0).xy;
	/*--------------------------------*/
	if (worldTime < 12700 || worldTime > 23250) {
		lightVector = normalize(sunPosition);
	}
	else {
		lightVector = normalize(-sunPosition);
	}
	/*--------------------------------*/
	sunVec = normalize(sunPosition);
	moonVec = normalize(-sunPosition);
	upVec = normalize(upPosition);
    ...

}

{{< /highlight >}}

I only understand some of the code, but the way the sun vector is being
calculated from the worldTime is promising. The hard-coded integers `12700` and
`23250` are *tick* counts (1 tick = 1/20th of a second). The problem is that
uniforms are provided variables, I'm not sure you can even change them in shader
code. My solution was to add another variable `scaledTime`, for example:

{{< highlight c >}}
void main() {
    ...
    int scaledTime = worldTime/4;

    ...
	if (scaledTime < 12700 || scaledTime > 23250) {
		lightVector = normalize(sunPosition);
	}
	else {
		lightVector = normalize(-sunPosition);
	}
    ...
}

{{< /highlight >}}

I have my day set to 4x the normal length, so I divide by 4 to re-normalize the
time of day to what the shaders expect. Now, don't think that one change is
enough to fix everything. Once I had a plan the next step was to find every
place `worldTime` appeared in the shader pack. I depended on a little
[Vim](http://www.vim.org/) magic to make that task simpler. You can probably use
another tool to search all the files, but Vim put everything in one tool for me.

After opening one of the files I ran the command `:vimgrep /worldTime/ ./*`
which populates the *quickfix* *list* with every place in every file where it
found "worldTime". From their I used Tim Pope's
[Unimpaired](https://github.com/tpope/vim-unimpaired) plugin's `[q` and `]q`
commands to jump backward/forward through the list. You could alternatively use
the `:cnext` and `:cprevious` ex commands for the same and `:clist` to view the
list. From their I added `int scaledTime = worldTime/4;` to the beginning of
each function, and then replaced every instance of `worldTime` with
`scaledTime`. The shader code is horribly chopped up and poorly formatted, which
makes it hard to follow. I just worked carefully and methodically and used
vimgrep to check my work.

And it worked! I was somewhat surprised. There's still one bug - at noon the sky
turns a reddish hue for a while. It is the dawn Rayleigh scattering simulation
re-playing for some reason. It only lasts a few minutes, so I can live with it.
Another problem is that when sleeping I tend to wake up at midnight every
night. I think it is a bug in PreciseTime, and I can go right back to sleep, so
I just consider it a more realistic sleep simulator!