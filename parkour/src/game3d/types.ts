export type Vec3 = [number, number, number];

export type ColliderSpec = {
  p: Vec3;
  s: Vec3;
  kind: 'solid' | 'soft';
};

export type BreakableSpec = {
  asset: string;
  p: Vec3;
  r?: Vec3;
  threshold: number;
  reward: number;
};

export type MoverSpec = {
  asset: string;
  p: Vec3;
  axis: 'x' | 'y' | 'z';
  distance: number;
  speed: number;
  collider: Vec3;
};

export type LevelSpec = {
  id: number;
  name: string;
  subtitle: string;
  asset: string;
  spawn: Vec3;
  finish: Vec3;
  colliders: ColliderSpec[];
  breakables: BreakableSpec[];
  movers: MoverSpec[];
  checkpoints: Vec3[];
  theme: string;
};

export type RunStats = {
  falls: number;
  breaks: number;
  checkpoints: number;
};
