# Create: No Unlimited Energy (NUE)

**Create's free, easy energy is the bug. Create: NUE deepens it.**

A Fabric 1.20.1 add-on for [Create](https://modrinth.com/mod/create) that turns power generation from a
plop-it-down convenience into something you *engineer*. Water wheels only run on genuine flowing water and
reward real dams; windmills become variable, weather-driven wind; and perpetual-motion loops dry up. The
goal is a world where energy is *earned* — and, with optional [ComputerCraft](https://modrinth.com/mod/cc-tweaked)
support, *managed in code*.

> Built for, and battle-tested on, the CodeMine SMP. Server-authoritative; clients only need the jar to
> see correct numbers in the goggle/stressometer.

## Requirements

| Mod | Role |
|-----|------|
| **[Create](https://modrinth.com/mod/create)** 6.x | **Required** — the kinetics this builds on. |
| **[Flowing Fluids](https://modrinth.com/mod/flowing-fluids)** | **Required** — finite, genuinely flowing water is the whole point. Set `create_waterWheelMode = REQUIRE_FLOW`. |
| **[CC: Tweaked](https://modrinth.com/mod/cc-tweaked)** | *Optional* — adds read-only telemetry peripherals. Everything works without it. |

Fabric Loader ≥ 0.15, Java 17.

## What it does

### Water wheels — flowing water only, and dams pay off
- **Real current only.** Create's water wheel is redirected to read vanilla flow, so a wheel sitting in a
  still pool produces **nothing** — you must build an actual current. (This also fixes a Flowing Fluids
  `REQUIRE_FLOW` bug where wheels never registered as flow listeners and stayed dead.)
- **Power scales with engineering:** current **strength** × **head** (how far the water falls onto the
  wheel) × **biome**. Tall dams and strong channels are the lever.
- **Megastructure reward.** Many *generating* wheels clustered into one dam reward **super-linearly** — a
  real hydro dam beats scattered wheels by more than ×N (a dry, fake cluster gains nothing).
- **Direction stability.** Signed-score hysteresis + shaft consensus stop wheels from flickering
  direction under finite-water churn, so you can build large, single-direction arrays.
- **No perpetual motion.** A small *water-as-fuel tax* removes a little water at the intake while
  generating, so a sealed pump→wheel loop runs dry; a real rain/river/ocean-fed source keeps working.
- **Goggle readout.** Wearing Create's goggles on a wheel shows the dam size and its multiplier.

### Windmills — variable wind, not a flat number
Modelled as two independent quantities (because in Create, `SU = capacity-per-RPM × RPM`):
- **Speed (the visible rotor)** = how windy it is *right now*: smooth gusts × weather × the **day/night
  cycle**, EMA-smoothed and de-flickered so the rotor drifts naturally instead of snapping.
- **Force (capacity, the SU)** = the windmill's **placement**: open-sky exposure × height × region biome.
- Net effect: wind is **variable and weaker than water** — a cheap, early, but unreliable source; water
  is the stable, premium, scalable one.

### ComputerCraft (optional)
Attach a wired modem to a wheel, windmill, **Stressometer** or **Speedometer** and read it from Lua — no
new blocks, no hard dependency (the integration only loads when CC is present).

| Peripheral | Methods |
|-----------|---------|
| `cm_generator` (wheels, windmills) | `kind()`, `active()`, `capacity()`, `rpm()`, `stress()`, `info()` |
| `cm_meter` (Stressometer, Speedometer) | `gaugeType()`, `speed()`, `capacity()`, `stressUsed()`, `load()`, `overstressed()` |

```lua
-- print every NUE generator + gauge on the wired network
for _, n in ipairs(peripheral.getNames()) do
  local t = peripheral.getType(n)
  if t == "cm_generator" then local g = peripheral.wrap(n)
    print(n, g.kind(), g.active(), g.stress() .. "SU", g.rpm() .. "rpm")
  elseif t == "cm_meter" then local m = peripheral.wrap(n)
    print(n, m.gaugeType(), m.stressUsed() .. "/" .. m.capacity() .. "SU", math.floor(m.load()*100) .. "%")
  end
end
```

## Configuration

Generated at `config/create_nue.json` on first launch; every value is tunable (flow/head/biome curves,
the dam-coherence reward, the water tax, the full wind model, the per-wheel SU cap). It is created with
sensible defaults — delete it to regenerate.

## Building from source

Loom + Mojang mappings. The compile-time dependencies (Create, Flowing Fluids, CC: Tweaked, and the
`fabric-api-lookup-api-v1` module) are **not redistributed** — drop their jars into `libs/` (extract them
from the matching mod jars / a 1.20.1 modpack), then:

```bash
./gradlew build   # -> build/libs/create-nue-<version>.jar
```

## Philosophy & roadmap

NUE is an ongoing answer to *"free, easy energy in Create is silly."* Generation is being deepened so it
takes real engineering, while RF/electricity and storage stay a normal progression tier — the aim is to
make a **huge hydro dam the most rewarding way to make electricity**, and to keep water wheels and
windmills relevant as the mechanical base the whole energy economy is built on. Planned: an economy hook
(profitable generation), pumped-hydro storage, seasonal water/wind, and richer ComputerCraft telemetry.

## License

[MIT](LICENSE). Contributions and forks welcome.
