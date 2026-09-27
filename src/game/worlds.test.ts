import { describe, expect, it } from "vitest";
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { SCENARIOS, WORLDS } from "./worlds";

describe("world atlas", () => {
  it("contains ten uniquely identified universes", () => {
    expect(WORLDS).toHaveLength(10);
    expect(new Set(WORLDS.map((world) => world.id)).size).toBe(10);
  });

  it("has one local panorama and answer point for each of the ten universes", () => {
    expect(SCENARIOS).toHaveLength(10);
    expect(new Set(SCENARIOS.map((scene) => scene.universeId)).size).toBe(10);
    for (const scene of SCENARIOS) {
      expect(scene.panoramaPath).toMatch(/^\/panoramas\/.+\.webp$/);
      expect(scene.locationName.length).toBeGreaterThan(2);
      expect(scene.point.x).toBeGreaterThanOrEqual(0);
      expect(scene.point.x).toBeLessThanOrEqual(1);
      expect(scene.point.y).toBeGreaterThanOrEqual(0);
      expect(scene.point.y).toBeLessThanOrEqual(1);
      expect(existsSync(resolve(process.cwd(), "web-panoramas", scene.panoramaPath.slice(1)))).toBe(true);
    }
  });

  it("keeps every scene answer tied to an existing universe", () => {
    const ids = new Set(WORLDS.map((world) => world.id));
    expect(SCENARIOS.every((scene) => ids.has(scene.universeId))).toBe(true);
  });
});
