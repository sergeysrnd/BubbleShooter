# Bubble Shooter: implementation brief

Build a desktop Bubble Shooter in Java 21 and JavaFX. Keep the game deterministic,
testable, and independent of rendering where possible.

## Core rules

- The board is an odd-row offset hex grid; every anchored bubble has exactly one
  `(row, column)` cell and up to six neighbours.
- A fired bubble travels as a circle of the same radius as anchored bubbles.
- The left and right playfield edges reflect horizontal velocity. A wall contact
  never attaches a bubble and the remaining distance of the current frame must
  still be simulated after reflection.
- A bubble attaches only on the first contact with the ceiling or an anchored
  bubble. Collision detection must sweep the entire movement segment, not sample
  its endpoint, so a fast projectile cannot tunnel through a cluster.
- On a grid impact, choose an empty immediate neighbour of the impacted cell.
  Rank candidates by distance to the exact impact point, then by stable row and
  column order. Never search arbitrary distant cells or attach inside a cluster.
- A ceiling impact chooses the nearest empty top-row cell; if blocked, choose a
  supported cell in a small local window below it.
- If a malformed board has no legal placement, finish the turn safely and return
  the bubble to the launcher; never leave a projectile active and never silently
  discard ammunition.
- Three or more connected bubbles of the same colour pop. Then remove every
  component no longer connected to the top row. Add a pressure row after a fixed
  number of misses.

## Architecture

- `ProjectilePhysics`: pure continuous collision solver. Return an impact point,
  collision kind, and hit grid cell. It must not know UI or scoring.
- `BubbleGrid`: pure hex-grid model. It owns legal placement, neighbours,
  matching, floating-bubble detection, and layout shifting.
- `GameController`: JavaFX input, animation, rendering, queue, score, and state.
  It calls physics and grid but must not duplicate their rules.

## Required tests

- wall bounce preserves the remaining travel in the same frame;
- a high-speed shot cannot pass through a dense cloud;
- a bank shot attaches to an immediate neighbour of the first bubble hit;
- a seam collision is deterministic;
- a malformed/fully blocked placement never freezes input or loses ammunition;
- top attachment, clusters, floating components, and pressure rows.
