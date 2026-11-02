# BF Companion

An offline Android guide for **Brave Frontier Revived**: every unit, sphere, drop and summon gate in your
pocket, plus a team builder that shows your squad's numbers like the game's Manage Squad screen.

No account, no ads, no internet needed: everything is inside the app.

**[⬇ Download the latest APK](../../releases/latest)** · ✅ No permissions · ✅ No internet access · ✅ [Code you can read](app) · ✅ [Scanned on VirusTotal (4.9)](https://www.virustotal.com/gui/file/4a0becfd3c7d33b522ca3ecc7b6105d0245334f8b0ad832bdbb7cdcc6cafebd6) · [Is it safe?](#is-it-safe)

Slow download from GitHub? Get the APK from the **[Google Drive mirror](https://drive.google.com/file/d/1uFvBd24L8b5KQAOG_Wnh-q1i7tvH-koN/view)** (Google says it can't scan files this big for viruses: tap *Download anyway*).

<p align="center">
  <img src="screenshots/1-units.jpg" width="200" alt="Unit list">
  <img src="screenshots/2-unit.jpg" width="200" alt="Unit page with the animated battle sprite">
  <img src="screenshots/3-skills.jpg" width="200" alt="Skills with roles and numbers">
  <img src="screenshots/4-how-to-get.jpg" width="200" alt="Where to capture a unit in Revived">
</p>
<p align="center">
  <img src="screenshots/5-team.jpg" width="200" alt="Team builder">
  <img src="screenshots/6-battle-check.jpg" width="200" alt="Battle check">
  <img src="screenshots/7-gate.jpg" width="200" alt="Summon gate with chances per pull">
  <img src="screenshots/8-quest.jpg" width="200" alt="What drops in a quest">
</p>

## What's inside

**Units**
- All 2,292 units with artwork and their **animated battle sprite**, taken from the game's own files. Save the sprite
  as a **GIF** to share, on the app's battle background or see-through.
- Stats for every type (Lord, Anima, Breaker, Guardian, Oracle) of every unit, Imps, max level.
- Leader Skill, BB, SBB, UBB, Extra Skill and Dual BB with BC cost, hits and the exact numbers.
- Roles with how strong they are ("Mitigation 50%", "BB ATK 600%"), SP enhancements, evolution line and materials, lore.
- All in English: units from the Japanese game show the game's own English, and a fan translation where it has none.
- Search by name, skill text or role, several words at once: `fire omni heal`, `def ignore`, `water mitigation`.
  A typo still finds it: *Did you mean…?* shows the closest names.
- **Similar units** on every unit page: the same element and roles, for when you don't have one.
- **My box**: mark the units you have (many at once with *Edit box*), filter the list to them, see what you're
  missing in each summon gate, and build teams from your own units.
- Sort by rarity, **element**, newest, stats, cost or the strongest unit for a role.

**How to get them in Revived**
- Summon gates the unit is in, with the **chance per pull** and the chance to get it in 10 pulls.
- Quests where you can **capture** it, with the capture chance; clear rewards; event shops.
- Every quest it **fights in as an enemy**, also where it can't be captured or a capture gives another unit.
- Only Revived's own data: for a unit you can't get directly, the earlier form you can get and how, then you evolve it.
- A page for every summon gate, and a *Get it* filter (This week, Summon, Capture, Reward / shop).

**Elgifs**
- All 541 Elgifs (the extra skills an Imbued Tablet Elgif gives a unit) from the game's own files, with rarity and effect.
- The ones Revived gives, with how: Frontier Hunter ranks, Frontier Gate and Eternal Crusade floors or points, Frontier Rift
  and Frontier Spire milestones, Grand Quest episodes, quest first clears and achievements. Search by name, effect or where,
  or filter by each way to get them.

**Spheres and items**
- Every sphere and material with Revived's own ways to get it: drops (chance per kill or per hit), clear rewards, shops,
  raid drops, Arena rank rewards, achievements and Synthesis. Something Revived doesn't give says so, and *craftable*
  means every material can be had.
- **Crafting planner**: all base materials and Karma for any Synthesis recipe, for any amount, and **where to farm**
  each material with its drop chance.
- Suggested spheres for each unit: the ones that switch on its extra skill, then the spheres Revived gives that suit
  what it does (damage or support, its element), and stronger ones Revived doesn't give yet.

**Quest search**
- Type an item to see where it drops in Revived, or a quest or area to see what drops there and which units you can capture.

**Team builder**
- Up to 10 named teams of 5 units + a friend, on a battle field with the animated sprites.
- Squad numbers like the game: HP, ATK, DEF, REC with the unit type, spheres, leader skill, friend skill, extra skills and Imps.
- **Battle check**: how much BC the team's skills fill (also per spark, and from leader and extra skills), who can cast
  SBB again right away, and rough BB/SBB/UBB damage from every attack, with the game's own modifiers.
- **Spark estimate**: how many of the team's hits spark in slot order, the best casting order and each unit's share,
  from the game's hit frames and the community's Automatic Sparking Simulator timings.
- What the team covers (mitigation, healing, BB fill, buffs…), *Fill empty slots* with units that cover what's missing,
  newer units first: from the units you can get in Revived, or only from your box.
- **Against each element**: how many of your units hit each enemy element hard or weakly, and the team's best element.
- Choosing a leader or friend shows each unit's leader skill.
- Share your team as a picture.

**Also:** favorites, recently viewed, compare up to 3 units side by side, the element chart, swipe between units, and
*What's new* in every version (tap the logo).

## Install

1. Download `BF-Companion.apk` from the [latest release](../../releases/latest) on your phone (or from the
   [Google Drive mirror](https://drive.google.com/file/d/1uFvBd24L8b5KQAOG_Wnh-q1i7tvH-koN/view) if GitHub is slow).
2. Open it and allow installing apps from your browser or file manager when Android asks.
3. Done. The app needs no permissions at all.

Android 7.0 or newer. About 110 MB, because every unit's artwork and animated sprite is included.

**Every version and what changed in it:** [Releases](../../releases). Older versions stay there to download.

Android warns about "unknown apps" for **every** APK installed from outside the Play Store; that warning doesn't
mean anything is wrong. If Google Play Protect offers to scan the app, let it.

## Is it safe?

Yes, and you don't have to take my word for it:

- **It asks for no permissions at all.** After installing, open *Settings → Apps → BF Companion → Permissions*:
  it says *No permissions requested*. Android's own tool shows the same for the APK (`aapt2 dump permissions`
  lists nothing but the app's name).
- **It can't use the internet.** Without the *Internet* permission Android blocks every network connection, and on
  top of that the app's code refuses to load anything that isn't one of its own built-in pages
  ([MainActivity.java, line 54](app/src/sg/bf/companion/MainActivity.java#L54)).
- **It collects nothing.** No account, no ads, no analytics, no trackers. Your favorites and teams are saved only
  on your phone, in the app's own storage, and are gone when you uninstall it.
- **The only file it ever writes** is a picture you choose to share or save (a unit or team card, or a sprite GIF), in
  *Pictures/BF Companion*.
- **Read the code.** The [`app`](app) folder holds everything that makes the APK:
  - [`AndroidManifest.xml`](app/AndroidManifest.xml): what the app is allowed to do (nothing extra).
  - [`MainActivity.java`](app/src/sg/bf/companion/MainActivity.java): the whole Android part, about 160 lines. It
    shows the app's pages, colours the phone's status and navigation bars, saves or shares a picture when you tap
    *Share* or *Save GIF*, and handles the back button.
  - [`www/index.html`](app/www/index.html): the app itself. An APK is a zip file: unzip it and you'll find this
    exact file as `assets/www/index.html`. The rest of the APK is the game data, artwork and sprites it shows.
  - [`build_apk.bat`](app/build_apk.bat): how the APK is built with Google's Android tools.
- **Checked by antivirus engines on VirusTotal:** [see the scan of version 4.9](https://www.virustotal.com/gui/file/4a0becfd3c7d33b522ca3ecc7b6105d0245334f8b0ad832bdbb7cdcc6cafebd6),
  with the result of every engine. Later versions are built the same way, with their code in [`app`](app).

## Where the data comes from

- **The game's own files**: unit, skill, sphere, item and Elgif data, artwork, sprites and icons.
- **[bfrevive.net](https://bfrevive.net/)**: Revived's server data for drops with chances, capture spots, the quests every unit
  fights in, summon gates and shops.
- **[Brave Frontier Wiki](https://bravefrontierglobal.fandom.com/)**: skill numbers, SP enhancements, lore and evolution details.

The data is a snapshot from the day the APK was built; new versions come with fresh data.

## Credits

Made by [@Nervequakes](https://github.com/Nervequakes).

Thanks to the BF Revive team for bringing the game back and publishing its data, to the Brave Frontier Wiki editors, and
to the makers of the Automatic Sparking Simulator (and BluuArc's Project Sparkle) for the spark timings.

*BF Companion is a fan-made guide and is not affiliated with gumi Inc., Alim or the BF Revive team. Brave Frontier, its
characters, artwork and sprites belong to their owners.*
