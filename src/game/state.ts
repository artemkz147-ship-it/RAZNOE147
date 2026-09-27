import { scoreRound, type RoundScore } from "./scoring";

export type Point = { x: number; y: number };
export type RoundAnswer = { universeId: string; point: Point; label: string };
export type PanoramaState = { phase: "panorama"; selectedUniverseId: null; guess: null };
export type MultiverseState = { phase: "multiverse"; selectedUniverseId: null; guess: null };
export type MapState = { phase: "map"; selectedUniverseId: string; guess: Point | null };
export type ResultState = {
  phase: "result";
  selectedUniverseId: string;
  guess: Point;
  answer: RoundAnswer;
  score: RoundScore;
};
export type GameState =
  | PanoramaState
  | MultiverseState
  | MapState
  | ResultState;

export function createGameState(): PanoramaState {
  return {
    phase: "panorama",
    selectedUniverseId: null,
    guess: null,
  };
}

export function openMultiverse(state: PanoramaState): MultiverseState {
  if (state.phase !== "panorama") throw new Error("Open the multiverse from a panorama");
  return { phase: "multiverse", selectedUniverseId: null, guess: null };
}

export function selectUniverse(state: MultiverseState, universeId: string): MapState {
  if (state.phase !== "multiverse") throw new Error("Choose a universe from the multiverse map");
  return { phase: "map", selectedUniverseId: universeId, guess: null };
}

export function placeGuess(state: MapState, point: Point): MapState {
  return {
    ...state,
    guess: {
      x: Number.isFinite(point.x) ? Math.min(1, Math.max(0, point.x)) : 0.5,
      y: Number.isFinite(point.y) ? Math.min(1, Math.max(0, point.y)) : 0.5,
    },
  };
}

export function submitGuess(
  state: MapState,
  answer: RoundAnswer,
): ResultState {
  if (!state.guess || !state.selectedUniverseId) {
    throw new Error("Place a guess before submitting");
  }

  const distance = Math.hypot(
    state.guess.x - answer.point.x,
    state.guess.y - answer.point.y,
  ) / Math.SQRT2;

  return {
    phase: "result",
    selectedUniverseId: state.selectedUniverseId,
    guess: state.guess,
    answer,
    score: scoreRound(answer.universeId, state.selectedUniverseId, distance),
  };
}
