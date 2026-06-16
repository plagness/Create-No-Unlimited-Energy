# Create: No Unlimited Energy (NUE)

**Make power in Create something you *engineer*, not something you plop down.**

NUE ties [Create](https://modrinth.com/mod/create-fabric)'s kinetics to genuinely flowing water
([Flowing Fluids](https://modrinth.com/mod/flowing-fluids)) and a touch of
[ComputerCraft](https://modrinth.com/mod/cc-tweaked), turning energy generation into a real engineering
problem: build a hydroelectric dam, a wind farm, and the logic that runs them — instead of a wheel parked
in a puddle. Server-authoritative; clients only need the jar to see correct numbers in the goggles.

## Philosophy

- **Free, easy energy is the bug.** A wheel in a still pool — or a sealed pump loop — should *not* power
  a base. Power has to be earned.
- **Water is engineered.** Wheels run only on a real current, and output scales with flow strength, the
  **head** (how far the water falls onto the wheel) and biome. Strong channels and tall dams are the lever.
- **Bigger pays off.** Many working wheels clustered into one dam reward **super-linearly** — a real
  hydro megastructure beats scattered wheels by more than ×N.
- **No perpetual motion.** Generating consumes a little water at the intake, so closed pump→wheel loops
  run dry; only genuine rain / river / ocean sources sustain.
- **Wind is variable, not free.** A windmill's rotor speed tracks live gusts, weather and the day/night
  cycle, and its strength depends on placement (open sky, height, biome). Cheap and early — but unreliable.
- **Stable water vs gusty wind.** Water is the premium, scalable backbone; wind is the convenient, swingy
  early option. Build both.
- **Automate it — optionally.** ComputerCraft peripherals expose live telemetry of every wheel, windmill
  and gauge, so you can build dashboards and control logic in Lua. Never required.

## Requirements

| Mod | Role |
|-----|------|
| **[Create (Fabric)](https://modrinth.com/mod/create-fabric)** 6.x | **Required** |
| **[Flowing Fluids](https://modrinth.com/mod/flowing-fluids)** | **Required** — finite, genuinely flowing water. Set `create_waterWheelMode = REQUIRE_FLOW`. |
| **[CC: Tweaked](https://modrinth.com/mod/cc-tweaked)** | *Optional* — read-only telemetry peripherals. Everything works without it. |

Fabric Loader ≥ 0.15 · **Java 17 or 21**.

## Features

### Water wheels — flowing water only, and dams pay off
- **Real current only.** A wheel in still water produces **nothing** — you build an actual flow. (Also
  fixes a Flowing Fluids `REQUIRE_FLOW` bug where wheels never registered as flow listeners and stayed dead.)
- **Power = current strength × head × biome.** Tall dams and strong channels are how you scale.
- **Megastructure reward.** A cluster of *generating* wheels (a real dam) rewards super-linearly; a dry,
  fake cluster gains nothing.
- **Direction stability.** Signed-score hysteresis + shaft consensus stop wheels flickering direction under
  finite-water churn, so large single-direction arrays are buildable.
- **No perpetual motion.** A small water-as-fuel tax drains the intake while generating.
- **Goggle readout.** Wearing Create's goggles on a wheel shows the dam size and its multiplier.

### Windmills — variable wind, not a flat number
Two independent quantities (in Create, `SU = capacity-per-RPM × RPM`):
- **Speed (the visible rotor)** = how windy it is *right now* — gusts × weather × day/night, smoothed so
  the rotor drifts naturally.
- **Force (capacity)** = the windmill's **placement** — open-sky exposure × height × region biome.

### ComputerCraft (optional)
Attach a wired modem to a wheel, windmill, **Stressometer** or **Speedometer** — no new blocks, no hard
dependency (the integration only loads when CC is present).

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

Generated at `config/create_nue.json` on first launch — every value is tunable (flow/head/biome curves, the
dam-coherence reward, the water tax, the full wind model, the per-wheel SU cap). Delete it to regenerate.

## Building from source

Loom + Mojang mappings, compiled for **Java 17** (runs on 17 or 21). The compile-time dependencies (Create,
Flowing Fluids, CC: Tweaked, and the `fabric-api-lookup-api-v1` module) are **not redistributed** — drop
their jars into `libs/`, then:

```bash
./gradlew build   # -> build/libs/create-nue-<version>.jar
```

## Roadmap

NUE is an ongoing answer to *"free, easy energy in Create is silly."* Generation is being deepened while
RF/electricity and storage stay a normal progression tier — the aim is to make a **huge hydro dam the most
rewarding way to make electricity**, with water wheels and windmills as the mechanical base the whole energy
economy is built on. Planned: a profitable-generation economy hook, pumped-hydro storage, seasonal
water/wind, and richer ComputerCraft telemetry.

## License

[MIT](LICENSE). Contributions and forks welcome.
