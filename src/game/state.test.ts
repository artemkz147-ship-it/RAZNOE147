import { describe, expect, it } from "vitest";
import { createGameState, openMultiverse, placeGuess, selectUniverse, submitGuess } from "./state";

describe("round flow", () => {
  it("moves from panorama to universe map, places a guess, and reveals a score", () => {
    const initial = createGameState();
    expect(initial.phase).toBe("panorama");

    const universePicker = openMultiverse(initial);
    const map = selectUniverse(universePicker, "middle-earth");
    const guessed = placeGuess(map, { x: 0.2, y: 0.3 });
    const result = submitGuess(guessed, {
      universeId: "middle-earth",
      point: { x: 0.2, y: 0.3 },
      label: "Хоббитон",
    });

    expect(result.phase).toBe("result");
    expect(result.score.total).toBe(5_000);
    expect(result.answer.label).toBe("Хоббитон");
  });

  it("clamps a map guess to the map bounds", () => {
    const map = selectUniverse(openMultiverse(createGameState()), "middle-earth");
    expect(placeGuess(map, { x: 1.5, y: -0.5 }).guess).toEqual({ x: 1, y: 0 });
  });

  it("does not allow a result before a map guess is placed", () => {
    const map = selectUniverse(openMultiverse(createGameState()), "middle-earth");
    expect(() => submitGuess(map, {
      universeId: "middle-earth",
      point: { x: 0.2, y: 0.3 },
      label: "Хоббитон",
    })).toThrow("Place a guess before submitting");
  });
});
