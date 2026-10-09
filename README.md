# Morph Watch — Forge 1.20.1 (Stage 2)

A gold watch with a red strap that you wear on your wrist. Scan mobs to unlock them, then turn into them, with their hearts, their attacks and two powers each.

## Controls

**Right-click the watch to put it on.** It straps onto your left wrist (press F5 to see it). The ring around the face glows **green** when your power is ready and **red** while it recharges.

| Key | What happens |
|---|---|
| **V** while looking at a mob | Scan it with a beam. This unlocks the mob and puts it on your dial |
| **C** | Slam the watch and transform into the mob on the dial (the one you just scanned, or the one you picked) |
| **X** | Raise your arm and open the dial (press X again to close it) |
| **Scroll wheel** (dial up) | Up = turn right (next mob), down = turn left (previous mob). Locked mobs show a **?** |
| **Sneak + V** | Back to human |
| **R** | Mob power. **Hold R** for 1.5 seconds for a charged, double-strength power |
| **Z** | Second mob power |
| **J** | Take the watch off |

Change keys in Options → Controls → Key Binds → Morph Watch.

The panel in the top-left corner shows your mob, your watch, how many mobs you've scanned, and bars for when R, Z and C (the next transform) are ready. A beep plays when a power has recharged.

## The dial and transformation

Press **X** and a hologram of a mob rises out of your watch. Friends nearby can see it too. Golden forms glow gold and the rest glow blue. Scroll to turn the dial (it clicks), then press **C** to slam the watch. The screen shakes, gold light spirals up your body, you spin and shrink or grow into the mob, there's a flash and a jingle, and the camera pulls out so you can watch it happen.

| Watch | Transform time | Wait before the next transform |
|---|---|---|
| Gold | 1 second | 3 seconds |
| Diamond | Half a second | 2 seconds |
| Netherite | Instant | 1 second |

Turning back to human never has to wait.

## The mobs

| Mob | Hearts | Passive | R power | Z power | Punch |
|---|---|---|---|---|---|
| Chicken | 2 | Slow falling, no fall damage | Lay eggs | Flap upward | Light knockback |
| Cat | 5 | Speed, night vision, no fall damage | Purr: heal | Pounce | +2 damage |
| Creeper | 10 | — | Explode (you're safe) | Hiss: scare mobs away | — |
| Spider | 8 | Climb walls, night vision | Shoot cobwebs | Spider sense: mobs glow | Slows |
| Zombie | 10 | Strength | Groan: weakens mobs | Undead toughness | Hunger, +2 damage |
| Skeleton | 10 | Speed | Shoot arrows | Bone shield | Knockback |
| Blaze | 10 | Fireproof, slow falling | Fireballs | Flame burst | Sets on fire |
| Enderman | 20 | Speed, no fall damage | Teleport where you look | Random escape teleport | +3 damage |
| Wither Skeleton | 10 | Fireproof, strength | Wither aura | Wither skull | Wither, +3 damage |
| Iron Golem | 50 | Strength, slow, no fall damage | Ground slam | Toss a mob into the air | Launches, +6 damage |
| Bat | 3 | Fly, night vision | Echolocation: mobs glow | Screech: confuse mobs | — |
| Snow Golem | 2 | — | Snowball burst | Freeze mobs and lay snow | Freezes |

**Disguise:** mobs of your kind won't attack you (as a Zombie, zombies, husks and drowned leave you alone) unless you hit them first.
**Mob friends:** up to 6 mobs of your kind follow you and attack whatever you fight.
**Auto-escape:** if your hearts drop below a quarter, the watch turns you human, cancels the hit and heals you. It then needs 60 seconds to recharge (45 for diamond, 30 for netherite).

## Golden forms

Scanning a mob counts toward its Golden form. Scan **10 different mobs of the same kind** (e.g. 10 different zombies) to unlock it. Golden forms have 50% more hearts, stronger powers and gold sparkles. The mob menu shows your progress, like "4/10".

## Upgrades

| Watch | How to make it | Bonus |
|---|---|---|
| Morph Watch | Red wool top and bottom middle, ender pearl in the centre, gold blocks in the other 6 slots | — |
| Diamond Morph Watch | Morph Watch surrounded by 4 diamonds (top, bottom, left, right) | Recharges 30% faster, powers 25% stronger |
| Netherite Morph Watch | Smithing table: netherite upgrade template + Diamond Morph Watch + netherite ingot | Recharges 55% faster, powers 50% stronger, fireproof item |

## Achievements

Open Advancements (L) and find the **Morph Watch** tab. It has: DNA Collector, Mob Encyclopedia, Gold Standard, Power Up, Fully Charged, Master of Powers, Close Call, Shiny Upgrade and Ultimate Watch.

Your watch, scans and achievements stay with you when you die. Your form resets to human.

## Coming in the next stages

- **Stage 3, watch style and items:** watch faces, glow colours, case shapes, engraving, strap colours, glow in the dark, nickname, the Watch Workbench, the Recharge Crystal, the Mob Statue, gems, DNA cards, the battery, the lost watch tracker and the watch lock.
- **Later stages:** watch gadgets (flashlight, danger alarm, home compass, weather forecast), mob life (mob armor, mob talk, mob food, mob mining), and levelling (mob levels, skill points, daily challenges, shiny mobs, boss and fusion forms). The full list is in FEATURE_PLAN.md.
- **Stage 4, world:** day/night powers, weather powers, combo hits, boss forms, fusion forms, mob talk, mob homes and mob food.

---

## Make the .jar and install it (Mac)

TLauncher loads mods from `.jar` files. This folder is the mod's source code; building it makes the `.jar`.

**Easy way: double-click `BUILD-MOD.command`.**

1. Unzip `MorphWatch.zip` (double-click it in Finder).
2. Open the `MorphWatch` folder, **right-click `BUILD-MOD.command` and choose Open**, then click **Open** again. Right-clicking gets past the Mac's "unidentified developer" warning, and you only need to do it the first time.
3. If you don't have Java 17 yet, it opens the download page. Install the `.pkg` (**aarch64** for Apple M1/M2/M3/M4 chips, **x64** for Intel), then run `BUILD-MOD.command` again.
4. Wait for the build. The first one downloads Minecraft and Forge, so it can take about 10 minutes.
5. When it says **DONE!**, the `.jar` is already in your mods folder (`~/Library/Application Support/minecraft/mods`), and any older Morph Watch `.jar` has been removed.
6. Open TLauncher, choose **Forge 1.20.1** and press Play. The watches are in the creative **Tools & Utilities** tab.

If the build fails, it shows you a file called `build-log.txt`. Send that file to Claude.

**Terminal way (if you prefer):** in Terminal, `cd` into the MorphWatch folder and run `./gradlew build`. The jar appears in `build/libs/`.

## If something goes wrong

- **Build errors:** send `build-log.txt` to Claude.
- **"Unsupported class file major version":** run `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` and build again.
- **Game crashes on start:** make sure you launched *Forge* 1.20.1 and that only one morphwatch jar is in the mods folder.
- **Servers:** the server needs the mod too, and Bat flight and Spider climbing need `allow-flight=true` in `server.properties`.
