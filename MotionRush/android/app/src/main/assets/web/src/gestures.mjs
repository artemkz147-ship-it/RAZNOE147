const REQUIRED = [
  'nose',
  'leftShoulder', 'rightShoulder',
  'leftWrist', 'rightWrist',
  'leftHip', 'rightHip',
  'leftKnee', 'rightKnee',
];

const midpoint = (a, b) => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 });
const abs = Math.abs;

function confident(frame, min = 0.55) {
  return REQUIRED.every((name) => frame?.[name] && (frame[name].visibility ?? 1) >= min);
}

function cooldownReady(last, now, cooldown) {
  return last == null || now - last >= cooldown;
}

export function createGestureInterpreter(options = {}) {
  const settings = {
    confidence: 0.55,
    laneCooldownMs: 350,
    jumpCooldownMs: 120,
    crouchCooldownMs: 450,
    punchCooldownMs: 260,
    raiseHoldMs: 120,
    raiseCooldownMs: 380,
    ...options,
  };

  let baseline = null;
  const last = new Map();
  const holdSince = { left: null, right: null, both: null };

  function mark(action, now) {
    last.set(action, now);
  }

  function ready(action, now, ms) {
    return cooldownReady(last.get(action), now, ms);
  }

  function calibrate(frame) {
    if (!confident(frame, settings.confidence)) return false;
    const shoulder = midpoint(frame.leftShoulder, frame.rightShoulder);
    const hip = midpoint(frame.leftHip, frame.rightHip);
    const shoulderWidth = abs(frame.rightShoulder.x - frame.leftShoulder.x);
    const bodyHeight = abs(hip.y - shoulder.y);
    if (shoulderWidth < 0.05 || bodyHeight < 0.08) return false;
    baseline = {
      centerX: hip.x,
      shoulderY: shoulder.y,
      hipY: hip.y,
      shoulderWidth,
      bodyHeight,
    };
    holdSince.left = holdSince.right = holdSince.both = null;
    last.clear();
    return true;
  }

  function setHold(name, active, now) {
    if (!active) {
      holdSince[name] = null;
      return false;
    }
    if (holdSince[name] == null) holdSince[name] = now;
    return now - holdSince[name] >= settings.raiseHoldMs;
  }

  function update(frame, nowMs = performance.now()) {
    if (!baseline || !confident(frame, settings.confidence)) {
      holdSince.left = holdSince.right = holdSince.both = null;
      return [];
    }

    const actions = [];
    const hip = midpoint(frame.leftHip, frame.rightHip);
    const b = baseline;

    const laneThreshold = Math.max(0.075, b.shoulderWidth * 0.62);
    const dx = hip.x - b.centerX;
    if (dx < -laneThreshold && ready('LANE', nowMs, settings.laneCooldownMs)) {
      actions.push('MOVE_LEFT');
      mark('LANE', nowMs);
    } else if (dx > laneThreshold && ready('LANE', nowMs, settings.laneCooldownMs)) {
      actions.push('MOVE_RIGHT');
      mark('LANE', nowMs);
    }

    const jumpThreshold = Math.max(0.09, b.bodyHeight * 0.42);
    if (b.hipY - hip.y > jumpThreshold && ready('JUMP', nowMs, settings.jumpCooldownMs)) {
      actions.push('JUMP');
      mark('JUMP', nowMs);
    }

    const crouchDrop = hip.y - b.hipY;
    const kneeGap = ((frame.leftKnee.y - frame.leftHip.y) + (frame.rightKnee.y - frame.rightHip.y)) / 2;
    if (
      crouchDrop > Math.max(0.085, b.bodyHeight * 0.36) &&
      kneeGap < b.bodyHeight * 0.65 &&
      ready('CROUCH', nowMs, settings.crouchCooldownMs)
    ) {
      actions.push('CROUCH');
      mark('CROUCH', nowMs);
    }

    const punchReach = Math.max(0.20, b.shoulderWidth * 1.35);
    const verticalPunchWindow = Math.max(0.10, b.bodyHeight * 0.52);
    const leftPunch =
      frame.leftShoulder.x - frame.leftWrist.x > punchReach &&
      abs(frame.leftWrist.y - frame.leftShoulder.y) < verticalPunchWindow;
    const rightPunch =
      frame.rightWrist.x - frame.rightShoulder.x > punchReach &&
      abs(frame.rightWrist.y - frame.rightShoulder.y) < verticalPunchWindow;

    if (leftPunch && ready('PUNCH_LEFT', nowMs, settings.punchCooldownMs)) {
      actions.push('PUNCH_LEFT');
      mark('PUNCH_LEFT', nowMs);
    }
    if (rightPunch && ready('PUNCH_RIGHT', nowMs, settings.punchCooldownMs)) {
      actions.push('PUNCH_RIGHT');
      mark('PUNCH_RIGHT', nowMs);
    }

    const raiseMargin = Math.max(0.07, b.bodyHeight * 0.28);
    const leftRaised = frame.leftWrist.y < frame.leftShoulder.y - raiseMargin;
    const rightRaised = frame.rightWrist.y < frame.rightShoulder.y - raiseMargin;

    const bothHeld = setHold('both', leftRaised && rightRaised, nowMs);
    const leftHeld = setHold('left', leftRaised && !rightRaised, nowMs);
    const rightHeld = setHold('right', rightRaised && !leftRaised, nowMs);

    if (bothHeld && ready('RAISE_BOTH', nowMs, settings.raiseCooldownMs)) {
      actions.push('RAISE_BOTH');
      mark('RAISE_BOTH', nowMs);
    } else if (leftHeld && ready('RAISE_LEFT', nowMs, settings.raiseCooldownMs)) {
      actions.push('RAISE_LEFT');
      mark('RAISE_LEFT', nowMs);
    } else if (rightHeld && ready('RAISE_RIGHT', nowMs, settings.raiseCooldownMs)) {
      actions.push('RAISE_RIGHT');
      mark('RAISE_RIGHT', nowMs);
    }

    return actions;
  }

  return {
    calibrate,
    update,
    get calibrated() { return baseline != null; },
    get baseline() { return baseline ? { ...baseline } : null; },
  };
}
