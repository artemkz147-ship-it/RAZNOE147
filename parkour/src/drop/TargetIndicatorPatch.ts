import * as THREE from 'three';
import { DropGame3D } from './DropGame3D';

type RuntimeGame = {
  frame: () => void;
  running: boolean;
  paused: boolean;
  targetIndex: number;
  currentLevel?: { targets: unknown[] };
  camera: THREE.PerspectiveCamera;
  levelManager: { getTargetPosition: (index: number, target: THREE.Vector3) => THREE.Vector3 };
};

const proto = DropGame3D.prototype as unknown as RuntimeGame;
const originalFrame = proto.frame;
const projectedTarget = new THREE.Vector3();

function updateIndicator(game: RuntimeGame) {
  const indicator = document.querySelector<HTMLElement>('#targetIndicator');
  if (!indicator) return;

  const hasTarget = game.running
    && !game.paused
    && game.currentLevel
    && game.targetIndex < game.currentLevel.targets.length;
  if (!hasTarget) {
    indicator.classList.remove('visible', 'offscreen');
    return;
  }

  game.levelManager.getTargetPosition(game.targetIndex, projectedTarget);
  game.camera.updateMatrixWorld(true);
  projectedTarget.project(game.camera);

  const width = Math.max(1, innerWidth);
  const height = Math.max(1, innerHeight);
  const rawX = (projectedTarget.x * 0.5 + 0.5) * width;
  const rawY = (-projectedTarget.y * 0.5 + 0.5) * height;
  const marginX = Math.min(70, width * 0.09);
  const marginY = Math.min(78, height * 0.12);
  const behind = projectedTarget.z < -1 || projectedTarget.z > 1;
  const offscreen = behind || rawX < marginX || rawX > width - marginX || rawY < marginY || rawY > height - marginY;

  const x = Math.max(marginX, Math.min(width - marginX, rawX));
  const y = Math.max(marginY, Math.min(height - marginY, rawY));
  indicator.style.left = `${x}px`;
  indicator.style.top = `${y}px`;
  indicator.classList.add('visible');
  indicator.classList.toggle('offscreen', offscreen);
}

proto.frame = function patchedFrame(this: RuntimeGame) {
  originalFrame.call(this);
  updateIndicator(this);
};
