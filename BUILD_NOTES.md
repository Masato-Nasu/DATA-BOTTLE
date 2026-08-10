# Build notes — v0.1.7

Windows bootstrap is unchanged and no new Gradle dependency is required.

## v0.1.7 static-surface changes

- Fully-settled display direction snaps to the nearest cardinal axis when the
  orthogonal gravity component is below `0.025` (about 1.43 degrees).
- The internal simulation state remains unsnapped, preventing the visual snap
  from feeding back into the spring and restarting motion.
- When settled on a horizontal cardinal surface, the visible liquid fraction
  is rounded to the nearest *complete interior dot-row boundary*.
- Headline/detail values remain based on the exact metric; only the rendered
  liquid is rounded.
- The static widget uses the same complete-row fraction snapping.

## Retained liquid parameters

- sensor low-pass follow: 0.12
- spring acceleration: 0.035 per 60 Hz frame
- spring damping: 0.82 per 60 Hz frame
- maximum component velocity: 0.18
- still tilt epsilon: 0.015
- still velocity epsilon: 0.010
- maximum dynamic wave amplitude: 4.5 dots
- wave follow: 0.18
- wave decay: 0.90 per 60 Hz frame
- wave motion gain: 18
- primary wave: about 1.2 cycles
- secondary wave: about 2.1 cycles
- secondary mix: 25–35% per new slosh
- primary phase jitter: ±0.40 rad per new slosh
- phase-speed variation: ±10%
- amplitude variation: ±6%
- moving meniscus: 0.8 dot
- still meniscus: 0
- main grid: 56 × 112
- empty interior alpha: 0.12
- outline alpha: 0.28
