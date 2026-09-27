import { describe, expect, it } from "vitest";
import { scoreRound } from "./scoring";

describe("scoreRound", () => {
  it("awards the full 5,000 points for the exact universe and location", () => {
    expect(scoreRound("middle-earth", "middle-earth", 0)).toEqual({
      universe: 1_000,
      location: 4_000,
      total: 5_000,
    });
  });

  it("awards no location points when the universe is wrong", () => {
    expect(scoreRound("middle-earth", "the-matrix", 0)).toEqual({
      universe: 0,
      location: 0,
      total: 0,
    });
  });

  it("awards more location points for a closer guess", () => {
    const close = scoreRound("middle-earth", "middle-earth", 0.05);
    const far = scoreRound("middle-earth", "middle-earth", 0.5);
    expect(close.location).toBeGreaterThan(far.location);
    expect(far.location).toBeGreaterThanOrEqual(0);
  });

  it("clamps points at zero for guesses outside the map", () => {
    expect(scoreRound("middle-earth", "middle-earth", 100).location).toBe(0);
  });
});
