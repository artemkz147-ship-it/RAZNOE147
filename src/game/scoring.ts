export type RoundScore = {
  universe: number;
  location: number;
  total: number;
};

const UNIVERSE_POINTS = 1_000;
const LOCATION_POINTS = 4_000;

/** Scores a normalized map-distance where 0 is exact and 1 is the map diagonal. */
export function scoreRound(
  answerUniverseId: string,
  guessUniverseId: string,
  normalizedDistance: number,
): RoundScore {
  const correctUniverse = answerUniverseId === guessUniverseId;
  const distance = Number.isFinite(normalizedDistance)
    ? Math.max(0, normalizedDistance)
    : 1_000_000;
  const universe = correctUniverse ? UNIVERSE_POINTS : 0;
  const location = correctUniverse
    ? Math.max(0, Math.round(LOCATION_POINTS * Math.exp(-4 * distance)))
    : 0;

  return { universe, location, total: universe + location };
}
