import type { Point } from "./state";

export function mapPointFromClient(
  clientX: number,
  clientY: number,
  rect: { left: number; top: number; width: number; height: number },
): Point {
  return {
    x: rect.width > 0 ? Math.min(1, Math.max(0, (clientX - rect.left) / rect.width)) : 0.5,
    y: rect.height > 0 ? Math.min(1, Math.max(0, (clientY - rect.top) / rect.height)) : 0.5,
  };
}
