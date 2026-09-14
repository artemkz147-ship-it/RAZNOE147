import * as THREE from '../vendor/three.module.js';

const LANES = [-2.15, 0, 2.15];
const PLAYER_Z = 2.25;
const WORLD_SPAN = 92;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const damp = (current, target, speed, dt) => lerp(current, target, 1 - Math.exp(-speed * dt));

function seeded(index, salt = 0) {
  const x = Math.sin((index + 1) * 9283.17 + salt * 413.91) * 43758.5453;
  return x - Math.floor(x);
}

function material(color, roughness = .55, metalness = .12, emissive = 0x000000, emissiveIntensity = 0) {
  return new THREE.MeshStandardMaterial({ color, roughness, metalness, emissive, emissiveIntensity });
}

function addMesh(parent, geometry, mat, x = 0, y = 0, z = 0) {
  const mesh = new THREE.Mesh(geometry, mat);
  mesh.position.set(x, y, z);
  parent.add(mesh);
  return mesh;
}

function createWindowTexture() {
  const c = document.createElement('canvas');
  c.width = 64;
  c.height = 128;
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#0a1020';
  ctx.fillRect(0, 0, c.width, c.height);
  for (let y = 7; y < 122; y += 13) {
    for (let x = 7; x < 60; x += 12) {
      const lit = seeded(x * 13 + y, 4) > .38;
      ctx.fillStyle = lit ? (seeded(x + y, 9) > .7 ? '#ff77d8' : '#50dff5') : '#121a32';
      ctx.globalAlpha = lit ? .78 : .45;
      ctx.fillRect(x, y, 5, 7);
    }
  }
  ctx.globalAlpha = 1;
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  return tex;
}

function createPlayer() {
  const root = new THREE.Group();
  const body = new THREE.Group();
  root.add(body);

  const jacket = material(0x38bdf8, .36, .28, 0x075b72, .65);
  const dark = material(0x0f172a, .55, .35);
  const accent = material(0xf472b6, .34, .25, 0x77124c, .75);
  const skin = material(0xe9b997, .72, .02);
  const shoe = material(0xd7efff, .42, .18);

  const pelvis = addMesh(body, new THREE.BoxGeometry(.68, .36, .40), dark, 0, .90, 0);
  const torso = addMesh(body, new THREE.BoxGeometry(.92, 1.02, .48), jacket, 0, 1.52, 0);
  torso.rotation.x = -.04;
  const stripe = addMesh(body, new THREE.BoxGeometry(.58, .08, .505), accent, 0, 1.64, .01);
  const head = addMesh(body, new THREE.SphereGeometry(.31, 18, 14), skin, 0, 2.30, 0);
  head.scale.z = .92;
  addMesh(body, new THREE.BoxGeometry(.38, .12, .20), dark, 0, 2.52, -.03);
  addMesh(body, new THREE.BoxGeometry(.58, .74, .20), dark, 0, 1.54, .34);

  function arm(side) {
    const shoulder = new THREE.Group();
    shoulder.position.set(side * .56, 1.94, 0);
    body.add(shoulder);
    const upper = addMesh(shoulder, new THREE.CylinderGeometry(.115, .135, .62, 10), jacket, 0, -.31, 0);
    const elbow = new THREE.Group();
    elbow.position.y = -.62;
    shoulder.add(elbow);
    addMesh(elbow, new THREE.SphereGeometry(.13, 10, 8), jacket, 0, 0, 0);
    addMesh(elbow, new THREE.CylinderGeometry(.09, .11, .58, 10), skin, 0, -.29, 0);
    addMesh(elbow, new THREE.SphereGeometry(.13, 12, 10), skin, 0, -.62, 0);
    return { shoulder, elbow, upper };
  }

  function leg(side) {
    const hip = new THREE.Group();
    hip.position.set(side * .25, .83, 0);
    body.add(hip);
    addMesh(hip, new THREE.CylinderGeometry(.15, .17, .72, 10), dark, 0, -.36, 0);
    const knee = new THREE.Group();
    knee.position.y = -.72;
    hip.add(knee);
    addMesh(knee, new THREE.SphereGeometry(.15, 10, 8), dark, 0, 0, 0);
    addMesh(knee, new THREE.CylinderGeometry(.12, .14, .68, 10), dark, 0, -.34, 0);
    const foot = addMesh(knee, new THREE.BoxGeometry(.32, .18, .54), shoe, 0, -.70, -.12);
    foot.rotation.x = .06;
    return { hip, knee };
  }

  const leftArm = arm(-1);
  const rightArm = arm(1);
  const leftLeg = leg(-1);
  const rightLeg = leg(1);

  const shield = addMesh(
    root,
    new THREE.SphereGeometry(1.34, 24, 18),
    new THREE.MeshBasicMaterial({ color: 0x5ee7ff, transparent: true, opacity: .13, wireframe: true, depthWrite: false, blending: THREE.AdditiveBlending }),
    0, 1.15, 0
  );
  shield.visible = false;

  const shadow = addMesh(
    root,
    new THREE.CircleGeometry(.62, 24),
    new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: .32, depthWrite: false }),
    0, .015, .04
  );
  shadow.rotation.x = -Math.PI / 2;

  return { root, body, torso, pelvis, stripe, leftArm, rightArm, leftLeg, rightLeg, shield, shadow, laneX: 0, scaleY: 1 };
}

function createBarrier(kind) {
  const group = new THREE.Group();
  const metal = material(0x26334b, .55, .55);
  const warning = material(0xffb020, .42, .25, 0xff6b00, .5);
  if (kind === 'high') {
    addMesh(group, new THREE.BoxGeometry(.25, 2.0, .42), metal, -.72, 1.0, 0);
    addMesh(group, new THREE.BoxGeometry(.25, 2.0, .42), metal, .72, 1.0, 0);
    addMesh(group, new THREE.BoxGeometry(1.7, .42, .46), warning, 0, 2.0, 0);
  } else if (kind === 'low') {
    addMesh(group, new THREE.BoxGeometry(1.55, .62, .58), warning, 0, .31, 0);
    addMesh(group, new THREE.BoxGeometry(1.62, .10, .63), metal, 0, .63, 0);
  } else {
    addMesh(group, new THREE.BoxGeometry(1.55, 1.56, .68), metal, 0, .78, 0);
    addMesh(group, new THREE.BoxGeometry(1.30, .18, .71), warning, 0, .98, 0);
  }
  return group;
}

function createDrone(side = 'left') {
  const g = new THREE.Group();
  const shell = material(0x182033, .32, .72);
  const glow = material(0xff466e, .28, .35, 0xff174f, 2.1);
  addMesh(g, new THREE.SphereGeometry(.45, 16, 12), shell, 0, 1.16, 0);
  addMesh(g, new THREE.BoxGeometry(.64, .13, .48), shell, 0, 1.16, 0);
  addMesh(g, new THREE.SphereGeometry(.09, 12, 8), glow, side === 'left' ? -.17 : .17, 1.20, -.41);
  const ring = addMesh(g, new THREE.TorusGeometry(.52, .035, 8, 28), new THREE.MeshBasicMaterial({ color: 0xff315f, transparent: true, opacity: .75 }), 0, 1.16, 0);
  ring.rotation.x = Math.PI / 2;
  g.userData.ring = ring;
  return g;
}

function pickupMaterial(type) {
  if (type === 'shield') return material(0x54e8ff, .26, .45, 0x32b9ff, 1.8);
  if (type === 'magnet') return material(0xf472b6, .28, .42, 0xd62986, 1.55);
  if (type === 'boost') return material(0xa7ff5b, .25, .38, 0x67d92b, 1.7);
  return material(0xffd65c, .25, .58, 0xff9d22, 1.3);
}

function createPickup(type, air = false) {
  const g = new THREE.Group();
  const m = pickupMaterial(type);
  const y = air ? 3.05 : .88;
  if (type === 'coin') {
    const ring = addMesh(g, new THREE.TorusGeometry(.31, .09, 10, 24), m, 0, y, 0);
    ring.rotation.y = Math.PI / 2;
    addMesh(g, new THREE.SphereGeometry(.09, 10, 8), m, 0, y, 0);
  } else if (type === 'shield') {
    addMesh(g, new THREE.IcosahedronGeometry(.38, 1), m, 0, y, 0);
  } else if (type === 'boost') {
    const cone = addMesh(g, new THREE.ConeGeometry(.34, .75, 8), m, 0, y, 0);
    cone.rotation.z = Math.PI;
  } else {
    const ring = addMesh(g, new THREE.TorusGeometry(.34, .11, 10, 18, Math.PI * 1.55), m, 0, y, 0);
    ring.rotation.z = .8;
  }
  const halo = addMesh(
    g,
    new THREE.SphereGeometry(.58, 14, 10),
    new THREE.MeshBasicMaterial({ color: m.color, transparent: true, opacity: .09, depthWrite: false, blending: THREE.AdditiveBlending }),
    0, y, 0
  );
  g.userData.halo = halo;
  return g;
}

function createParticleSystem(scene) {
  const count = 120;
  const positions = new Float32Array(count * 3);
  const colors = new Float32Array(count * 3);
  const life = new Float32Array(count);
  const vx = new Float32Array(count);
  const vy = new Float32Array(count);
  const vz = new Float32Array(count);
  for (let i = 0; i < count; i++) positions[i * 3 + 1] = -100;
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  const points = new THREE.Points(geometry, new THREE.PointsMaterial({ size: .11, vertexColors: true, transparent: true, opacity: .9, depthWrite: false, blending: THREE.AdditiveBlending, sizeAttenuation: true }));
  scene.add(points);
  let cursor = 0;
  const tmp = new THREE.Color();

  function burst(x, y, z, color, amount = 14, force = 1) {
    tmp.set(color);
    for (let n = 0; n < amount; n++) {
      const i = cursor++ % count;
      const p = i * 3;
      positions[p] = x;
      positions[p + 1] = y;
      positions[p + 2] = z;
      colors[p] = tmp.r;
      colors[p + 1] = tmp.g;
      colors[p + 2] = tmp.b;
      life[i] = .45 + Math.random() * .35;
      vx[i] = (Math.random() - .5) * 3.2 * force;
      vy[i] = (.4 + Math.random() * 2.4) * force;
      vz[i] = (Math.random() - .5) * 2.6 * force;
    }
    geometry.attributes.position.needsUpdate = true;
    geometry.attributes.color.needsUpdate = true;
  }

  function update(dt) {
    for (let i = 0; i < count; i++) {
      if (life[i] <= 0) continue;
      life[i] -= dt;
      const p = i * 3;
      if (life[i] <= 0) {
        positions[p + 1] = -100;
        continue;
      }
      positions[p] += vx[i] * dt;
      positions[p + 1] += vy[i] * dt;
      positions[p + 2] += vz[i] * dt;
      vy[i] -= 3.8 * dt;
      vx[i] *= .985;
      vz[i] *= .985;
    }
    geometry.attributes.position.needsUpdate = true;
  }
  return { burst, update };
}

function disposeObject(object) {
  object.traverse((node) => {
    node.geometry?.dispose?.();
    if (node.material) {
      const mats = Array.isArray(node.material) ? node.material : [node.material];
      for (const m of mats) m.dispose?.();
    }
  });
}

export function createRenderer(canvas) {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false, powerPreference: 'high-performance', precision: 'mediump' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.45));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.18;

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x07101d);
  scene.fog = new THREE.FogExp2(0x07101d, .025);

  const camera = new THREE.PerspectiveCamera(61, 1, .1, 130);
  camera.position.set(0, 3.15, 8.2);
  const lookTarget = new THREE.Vector3(0, 1.28, -7.8);

  scene.add(new THREE.HemisphereLight(0xa8d8ff, 0x101020, 1.55));
  const key = new THREE.DirectionalLight(0xffffff, 1.6);
  key.position.set(-4, 8, 5);
  scene.add(key);
  const neonFill = new THREE.PointLight(0x22d3ee, 3.2, 16, 2);
  neonFill.position.set(0, 2.2, 3.5);
  scene.add(neonFill);

  const roadMat = material(0x131a29, .82, .18);
  const sidewalkMat = material(0x20283a, .78, .14);
  addMesh(scene, new THREE.BoxGeometry(9.1, .18, 104), roadMat, 0, -.10, -43);
  addMesh(scene, new THREE.BoxGeometry(1.6, .30, 104), sidewalkMat, -5.2, 0, -43);
  addMesh(scene, new THREE.BoxGeometry(1.6, .30, 104), sidewalkMat, 5.2, 0, -43);

  const edgeMat = material(0x22d3ee, .30, .45, 0x00c7ff, 1.6);
  addMesh(scene, new THREE.BoxGeometry(.08, .045, 104), edgeMat, -4.47, .05, -43);
  addMesh(scene, new THREE.BoxGeometry(.08, .045, 104), edgeMat, 4.47, .05, -43);

  const markers = [];
  const markerMat = material(0xd8f7ff, .48, .15, 0x4cc9ff, .25);
  for (let i = 0; i < 34; i++) {
    for (const x of [-1.08, 1.08]) {
      const mesh = addMesh(scene, new THREE.BoxGeometry(.075, .035, 1.32), markerMat, x, .015, 0);
      markers.push({ mesh, base: i * 3.25 + (x > 0 ? 1.62 : 0) });
    }
  }

  const windowTexture = createWindowTexture();
  const buildings = [];
  for (let i = 0; i < 28; i++) {
    const side = i % 2 === 0 ? -1 : 1;
    const width = 2.0 + seeded(i, 1) * 2.7;
    const height = 4.3 + seeded(i, 2) * 8.2;
    const depth = 2.4 + seeded(i, 3) * 3.0;
    const tex = windowTexture.clone();
    tex.needsUpdate = true;
    tex.repeat.set(Math.max(1, Math.round(width)), Math.max(2, Math.round(height / 2)));
    const bm = new THREE.MeshStandardMaterial({
      color: new THREE.Color().setHSL(.60 + seeded(i, 5) * .10, .30, .10 + seeded(i, 7) * .055),
      roughness: .78,
      metalness: .18,
      map: tex,
      emissiveMap: tex,
      emissive: new THREE.Color(0x2f6d8a),
      emissiveIntensity: .38,
    });
    const mesh = addMesh(scene, new THREE.BoxGeometry(width, height, depth), bm, 0, height / 2, 0);
    mesh.userData.baseDepth = 7 + (i * 3.47) % WORLD_SPAN;
    mesh.userData.x = side * (6.8 + seeded(i, 8) * 5.4);
    mesh.rotation.y = (seeded(i, 11) - .5) * .08;
    buildings.push(mesh);
  }

  const lamps = [];
  const lampPostMat = material(0x253147, .45, .62);
  const lampGlowMat = material(0x8ff8ff, .25, .28, 0x32e6ff, 2.8);
  for (let i = 0; i < 22; i++) {
    const side = i % 2 === 0 ? -1 : 1;
    const g = new THREE.Group();
    addMesh(g, new THREE.CylinderGeometry(.055, .07, 2.8, 8), lampPostMat, 0, 1.4, 0);
    addMesh(g, new THREE.SphereGeometry(.14, 10, 8), lampGlowMat, 0, 2.82, 0);
    g.userData.baseDepth = 5 + (i * 4.35) % WORLD_SPAN;
    g.userData.side = side;
    scene.add(g);
    lamps.push(g);
  }

  const moon = addMesh(scene, new THREE.SphereGeometry(4.6, 24, 16), new THREE.MeshBasicMaterial({ color: 0x334c7a, transparent: true, opacity: .30 }), -18, 18, -74);
  const player = createPlayer();
  player.root.position.set(0, .05, PLAYER_Z);
  scene.add(player.root);

  const entityObjects = new Map();
  const particles = createParticleSystem(scene);

  const speedLines = [];
  const speedMat = new THREE.MeshBasicMaterial({ color: 0x9ff7ff, transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending });
  for (let i = 0; i < 24; i++) {
    const m = addMesh(scene, new THREE.BoxGeometry(.025, .025, 2.8 + seeded(i, 14) * 3), speedMat, 0, 0, 0);
    m.userData.baseDepth = seeded(i, 15) * 38;
    m.userData.x = (seeded(i, 16) - .5) * 8.2;
    m.userData.y = .3 + seeded(i, 17) * 3.4;
    speedLines.push(m);
  }

  let lastTime = performance.now() / 1000;
  let shake = 0;
  let flash = 0;
  let lastHealth = 3;

  function resize() {
    const width = Math.max(1, canvas.clientWidth || window.innerWidth);
    const height = Math.max(1, canvas.clientHeight || window.innerHeight);
    const expectedW = Math.floor(width * renderer.getPixelRatio());
    const expectedH = Math.floor(height * renderer.getPixelRatio());
    if (canvas.width !== expectedW || canvas.height !== expectedH) {
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
    }
  }

  function entityObject(entity) {
    if (entity.type === 'obstacle') return createBarrier(entity.obstacle || 'solid');
    if (entity.type === 'enemy') return createDrone(entity.side);
    if (entity.type === 'pickup') return createPickup(entity.pickup || 'coin', false);
    if (entity.type === 'airPickup') return createPickup(entity.pickup || 'coin', true);
    return new THREE.Group();
  }

  function syncEntities(view, time, dt) {
    const alive = new Set();
    for (const entity of view.entities) {
      alive.add(entity.id);
      let object = entityObjects.get(entity.id);
      if (!object) {
        object = entityObject(entity);
        scene.add(object);
        entityObjects.set(entity.id, object);
      }
      const targetX = LANES[entity.lane + 1] ?? 0;
      object.position.x = damp(object.position.x, targetX, 18, dt);
      object.position.z = PLAYER_Z - entity.z;
      if (entity.type === 'enemy') {
        object.position.y = .03 + Math.sin(time * 4.2 + entity.z) * .10;
        object.rotation.y = Math.sin(time * 1.8 + entity.z) * .10;
        if (object.userData.ring) object.userData.ring.rotation.z += dt * 3.2;
      } else {
        object.position.y = 0;
        if (entity.type === 'pickup' || entity.type === 'airPickup') object.rotation.y += dt * 2.6;
        if (object.userData.halo) {
          const pulse = 1 + Math.sin(time * 6 + entity.z) * .10;
          object.userData.halo.scale.setScalar(pulse);
        }
      }
    }
    for (const [id, object] of entityObjects) {
      if (!alive.has(id)) {
        scene.remove(object);
        disposeObject(object);
        entityObjects.delete(id);
      }
    }
  }

  function animatePlayer(view, dt) {
    const p = view.player;
    const targetX = LANES[p.lane + 1] ?? 0;
    player.laneX = damp(player.laneX, targetX, 16, dt);
    player.root.position.x = player.laneX;

    const crouching = p.crouchRemaining > 0;
    const airborne = p.y > .05;
    player.scaleY = damp(player.scaleY, crouching ? .72 : 1, 15, dt);
    player.body.scale.y = player.scaleY;
    player.root.position.y = .04 + p.y + (crouching ? -.03 : 0);

    const runPhase = view.distance * .78;
    const run = Math.sin(runPhase) * (airborne ? .23 : .78);
    player.body.position.y = airborne || crouching ? 0 : Math.abs(Math.sin(runPhase * 2)) * .055;
    player.body.rotation.y = damp(player.body.rotation.y, 0, 10, dt);

    let leftArmX = run;
    let rightArmX = -run;
    let leftArmZ = 0;
    let rightArmZ = 0;
    if (p.leftHandRemaining > 0) { leftArmX = .06; leftArmZ = -2.75; }
    if (p.rightHandRemaining > 0) { rightArmX = .06; rightArmZ = 2.75; }

    if (p.punchRemaining > 0) {
      const punchT = clamp(p.punchRemaining / .34, 0, 1);
      const thrust = Math.sin((1 - punchT) * Math.PI);
      if (p.punchSide === 'left') {
        leftArmX = 1.52 * thrust + run * (1 - thrust);
        leftArmZ = -.12;
        player.body.rotation.y = .22 * thrust;
      } else if (p.punchSide === 'right') {
        rightArmX = 1.52 * thrust - run * (1 - thrust);
        rightArmZ = .12;
        player.body.rotation.y = -.22 * thrust;
      }
    }

    player.leftArm.shoulder.rotation.x = damp(player.leftArm.shoulder.rotation.x, leftArmX, 22, dt);
    player.rightArm.shoulder.rotation.x = damp(player.rightArm.shoulder.rotation.x, rightArmX, 22, dt);
    player.leftArm.shoulder.rotation.z = damp(player.leftArm.shoulder.rotation.z, leftArmZ, 20, dt);
    player.rightArm.shoulder.rotation.z = damp(player.rightArm.shoulder.rotation.z, rightArmZ, 20, dt);

    let leftLeg = -run;
    let rightLeg = run;
    let leftKnee = Math.max(0, run) * .55;
    let rightKnee = Math.max(0, -run) * .55;
    if (crouching) {
      leftLeg = rightLeg = .56;
      leftKnee = rightKnee = -1.02;
    } else if (airborne) {
      leftLeg = .35;
      rightLeg = -.16;
      leftKnee = -.72;
      rightKnee = -.42;
    }
    player.leftLeg.hip.rotation.x = damp(player.leftLeg.hip.rotation.x, leftLeg, 18, dt);
    player.rightLeg.hip.rotation.x = damp(player.rightLeg.hip.rotation.x, rightLeg, 18, dt);
    player.leftLeg.knee.rotation.x = damp(player.leftLeg.knee.rotation.x, leftKnee, 18, dt);
    player.rightLeg.knee.rotation.x = damp(player.rightLeg.knee.rotation.x, rightKnee, 18, dt);

    player.shield.visible = view.effects.shieldRemaining > 0;
    if (player.shield.visible) player.shield.rotation.y += dt * 1.2;
    player.shadow.material.opacity = clamp(.34 - p.y * .045, .12, .34);
    const shadowScale = clamp(1 - p.y * .06, .62, 1);
    player.shadow.scale.setScalar(shadowScale);
  }

  function animateWorld(view, dt, time) {
    for (const marker of markers) {
      let depth = (marker.base - view.distance) % 58;
      if (depth < 0) depth += 58;
      marker.mesh.position.z = PLAYER_Z + 1.5 - depth;
    }
    for (const b of buildings) {
      let depth = (b.userData.baseDepth - view.distance) % WORLD_SPAN;
      if (depth < 0) depth += WORLD_SPAN;
      b.position.z = PLAYER_Z + 3 - depth;
      b.position.x = b.userData.x;
    }
    for (const lamp of lamps) {
      let depth = (lamp.userData.baseDepth - view.distance) % WORLD_SPAN;
      if (depth < 0) depth += WORLD_SPAN;
      lamp.position.z = PLAYER_Z + 2 - depth;
      lamp.position.x = lamp.userData.side * 5.45;
    }
    const boosting = view.effects.boostRemaining > 0;
    speedMat.opacity = damp(speedMat.opacity, boosting ? .52 : 0, 8, dt);
    for (const line of speedLines) {
      let depth = (line.userData.baseDepth - view.distance * (boosting ? 1.5 : 1)) % 38;
      if (depth < 0) depth += 38;
      line.position.set(line.userData.x, line.userData.y, PLAYER_Z + 2 - depth);
    }
    moon.rotation.y += dt * .01;
    neonFill.intensity = 2.8 + Math.sin(time * 1.3) * .35;
  }

  function processEvents(view) {
    for (const event of view.events) {
      if (event.type === 'pickup') {
        const color = event.pickup === 'boost' ? 0xa7ff5b : event.pickup === 'shield' ? 0x54e8ff : event.pickup === 'magnet' ? 0xf472b6 : 0xffd65c;
        particles.burst(player.root.position.x, 1.2 + view.player.y, PLAYER_Z - .4, color, 18, 1);
        flash = Math.max(flash, .08);
      } else if (event.type === 'enemy-hit') {
        particles.burst(player.root.position.x, 1.3 + view.player.y, PLAYER_Z - .7, 0xff466e, 22, 1.25);
        shake = Math.max(shake, .13);
      } else if (event.type === 'hit') {
        particles.burst(player.root.position.x, 1.1 + view.player.y, PLAYER_Z, 0xff3b5c, 26, 1.45);
        shake = Math.max(shake, .34);
        flash = Math.max(flash, .16);
      } else if (event.type === 'shield-break') {
        particles.burst(player.root.position.x, 1.2 + view.player.y, PLAYER_Z, 0x54e8ff, 32, 1.55);
        shake = Math.max(shake, .20);
      }
    }
    if (view.health < lastHealth) shake = Math.max(shake, .28);
    lastHealth = view.health;
  }

  function updateCamera(view, dt) {
    const boosting = view.effects.boostRemaining > 0;
    camera.fov = damp(camera.fov, boosting ? 69 : 61, 5.5, dt);
    camera.updateProjectionMatrix();
    shake = Math.max(0, shake - dt * 1.8);
    flash = Math.max(0, flash - dt * 1.5);
    const sx = shake > 0 ? (Math.random() - .5) * shake : 0;
    const sy = shake > 0 ? (Math.random() - .5) * shake * .55 : 0;
    const laneLag = (LANES[view.player.lane + 1] ?? 0) - player.laneX;
    camera.position.x = damp(camera.position.x, player.laneX * .12 + laneLag * .10, 7, dt) + sx;
    camera.position.y = damp(camera.position.y, 3.15 + Math.min(view.player.y * .09, .25), 8, dt) + sy;
    camera.position.z = damp(camera.position.z, boosting ? 8.85 : 8.2, 5, dt);
    lookTarget.x = damp(lookTarget.x, player.laneX * .08, 7, dt);
    lookTarget.y = damp(lookTarget.y, 1.25 + Math.min(view.player.y * .06, .18), 7, dt);
    camera.lookAt(lookTarget);
  }

  function resize() {
    const width = Math.max(1, canvas.clientWidth || window.innerWidth);
    const height = Math.max(1, canvas.clientHeight || window.innerHeight);
    const expectedW = Math.floor(width * renderer.getPixelRatio());
    const expectedH = Math.floor(height * renderer.getPixelRatio());
    if (canvas.width !== expectedW || canvas.height !== expectedH) {
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
    }
  }

  return {
    render(view, timeSeconds) {
      resize();
      const now = Number.isFinite(timeSeconds) ? timeSeconds : performance.now() / 1000;
      const dt = clamp(now - lastTime, 1 / 120, .05);
      lastTime = now;
      animateWorld(view, dt, now);
      animatePlayer(view, dt);
      syncEntities(view, now, dt);
      processEvents(view);
      particles.update(dt);
      updateCamera(view, dt);
      renderer.toneMappingExposure = 1.18 + flash * 1.8;
      renderer.render(scene, camera);
    },
  };
}
