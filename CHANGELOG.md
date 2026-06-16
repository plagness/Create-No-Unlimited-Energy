# Changelog

All notable changes per release. Newest first.

## 1.0.0
First public release of **Create: No Unlimited Energy**.

- **Water wheels:** power only from genuine flowing water (fixes the Flowing Fluids `REQUIRE_FLOW` dead-wheel
  bug); power scales with current strength × head × biome; super-linear **megastructure dam** reward;
  signed-score **direction stability** (hysteresis + shaft consensus); **water-as-fuel tax** so sealed
  perpetual-motion loops run dry; total-SU cap; goggle dam readout.
- **Windmills:** two-lever model — visible rotor **speed** (gusts × weather × day/night) and placement-based
  **force** (sky exposure × height × region biome). Wind is variable and weaker than water.
- **ComputerCraft (optional):** `cm_generator` (wheels, windmills) and `cm_meter` (Stressometer, Speedometer)
  read-only telemetry peripherals; no new blocks, no hard dependency.
- **Localization:** English, Russian, Simplified Chinese.
- Fully configurable via `config/create_nue.json`.
