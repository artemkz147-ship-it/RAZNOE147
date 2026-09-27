import { describe, expect, it } from "vitest";
import { mapPointFromClient } from "./mapCoordinates";

describe("mapPointFromClient", () => {
  it("converts a pointer location to normalized map coordinates", () => {
    expect(mapPointFromClient(150, 120, { left: 50, top: 20, width: 200, height: 200 }))
      .toEqual({ x: 0.5, y: 0.5 });
  });

  it("clamps a pointer outside the map to the nearest edge", () => {
    expect(mapPointFromClient(0, 300, { left: 50, top: 20, width: 200, height: 200 }))
      .toEqual({ x: 0, y: 1 });
  });
});
