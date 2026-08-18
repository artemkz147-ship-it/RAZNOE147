export type Vec3 = [number, number, number];

export type SurfaceKind = 'roof' | 'factory' | 'pole' | 'beam' | 'unit';

export type MovingSurface = {
  axis: 'x' | 'z';
  distance: number;
  speed: number;
  phase?: number;
};

export type DropSurface = {
  p: Vec3;
  size: [number, number];
  radius: number;
  kind: SurfaceKind;
  label: string;
  moving?: MovingSurface;
};

export type DropLevelSpec = {
  id: number;
  name: string;
  subtitle: string;
  start: DropSurface;
  targets: DropSurface[];
  theme: 'sunset' | 'city' | 'industrial' | 'night' | 'final';
  recommended: string;
  parScore: number;
};

export type TrickKind = 'front' | 'back' | 'side' | 'twist';

export type TrickEvent = {
  kind: TrickKind;
  label: string;
  points: number;
};

export type LandingGrade = 'perfect' | 'clean' | 'roll' | 'rough';

export type LandingResult = {
  grade: LandingGrade;
  label: string;
  stageScore: number;
  precision: number;
  precisionPoints: number;
  heightPoints: number;
  trickPoints: number;
  combo: number;
};

export type DropRunStats = {
  score: number;
  falls: number;
  tricks: number;
  uniqueTricks: number;
  perfectLandings: number;
  cleanLandings: number;
  bestCombo: number;
  totalDrop: number;
};
