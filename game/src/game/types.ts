export type GamePhase = 'boot' | 'menu' | 'preparation' | 'battle' | 'result' | 'route';
export type PauseReason = 'menu' | 'focus' | 'ad' | 'system';
export type DamageType = 'physical' | 'arcane' | 'frost' | 'lightning' | 'poison';
export type ModuleKind = 'ballista' | 'runeCannon' | 'frostCrystal' | 'lightningSpire' | 'poisonSprayer' | 'shieldTotem' | 'repairBay' | 'barracks';
export type WagonKind = 'locomotive' | 'weapon' | 'arcane' | 'armored' | 'workshop' | 'treasure' | 'crew' | 'relic';
export type EnemyKind = 'goblinRaider' | 'wolfRider' | 'harpy' | 'ogreBrute' | 'goblinBoss';

export interface Vec3Data { x: number; y: number; z: number; }

export interface ModuleState {
  id: string;
  kind: ModuleKind;
  level: 1 | 2 | 3;
  wagonId: string;
  slot: number;
  cooldown: number;
}

export interface WagonState {
  id: string;
  kind: WagonKind;
  hp: number;
  maxHp: number;
  armor: number;
  modules: string[];
}

export interface EnemyState {
  id: string;
  kind: EnemyKind;
  hp: number;
  maxHp: number;
  lane: 'north' | 'south' | 'air';
  progress: number;
  speed: number;
  attackCooldown: number;
  alive: boolean;
}

export interface ProjectileEvent {
  type: 'projectile';
  sourceModuleId: string;
  targetEnemyId: string;
  damageType: DamageType;
}

export interface ImpactEvent {
  type: 'impact';
  targetEnemyId: string;
  damageType: DamageType;
  amount: number;
  killed: boolean;
}

export interface WagonHitEvent {
  type: 'wagon-hit';
  wagonId: string;
  amount: number;
}

export interface EnemySpawnEvent {
  type: 'enemy-spawn';
  enemyId: string;
}

export interface EnemyRemovedEvent {
  type: 'enemy-removed';
  enemyId: string;
}

export interface BattleEndedEvent {
  type: 'battle-ended';
  victory: boolean;
}

export type GameEvent = ProjectileEvent | ImpactEvent | WagonHitEvent | EnemySpawnEvent | EnemyRemovedEvent | BattleEndedEvent;

export interface StageState {
  id: string;
  duration: number;
  elapsed: number;
  spawnedEntries: number;
  victory: boolean | null;
}

export interface SaveData {
  version: 1;
  gold: number;
  runes: number;
  unlockedRegions: number;
  bestStage: number;
  selectedModules: ModuleKind[];
}

export interface GameState {
  version: 1;
  phase: GamePhase;
  pauseReasons: PauseReason[];
  region: number;
  stage: number;
  gold: number;
  runes: number;
  trainSpeed: number;
  wagons: WagonState[];
  modules: ModuleState[];
  enemies: EnemyState[];
  battle: StageState | null;
}

export interface ModuleDefinition {
  kind: ModuleKind;
  damageType: DamageType;
  damage: readonly [number, number, number];
  cooldown: readonly [number, number, number];
  range: readonly [number, number, number];
  target: 'ground' | 'air' | 'any';
}

export interface SpawnEntry {
  at: number;
  kind: EnemyKind;
  lane: EnemyState['lane'];
  count: number;
  interval: number;
}

export interface StageDefinition {
  id: string;
  duration: number;
  spawns: readonly SpawnEntry[];
}
