const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const mix = (a, b, t) => a + (b - a) * t;

export function computeCharacterPose(view, motion = null, time = 0) {
  const p = view?.player || {};
  const conf = clamp(Number(motion?.conf ?? motion?.confidence ?? 0), 0, 1);
  const runPhase = (view?.distance || 0) * .82 + time * .7;
  const airborne = (p.y || 0) > .05;
  const crouching = (p.crouchRemaining || 0) > 0;
  const runWeight = airborne ? .24 : crouching ? .28 : 1;
  const run = Math.sin(runPhase) * .78 * runWeight;
  const lean = clamp(Number(motion?.lean || 0), -1.4, 1.4) * conf;
  const centerX = clamp(Number(motion?.cx || 0), -1.5, 1.5) * conf;
  const leftRaise = clamp(Number(motion?.la || 0), 0, 1.6) * conf;
  const rightRaise = clamp(Number(motion?.ra || 0), 0, 1.6) * conf;
  const jumpImpulse = clamp(Number(motion?.jump || 0), 0, 2.5) * conf;

  let bodyScaleY = crouching ? .70 : airborne ? 1.035 : 1;
  let hipY = crouching ? -.16 : 0;
  let leftHipX = -run;
  let rightHipX = run;
  let leftKneeX = Math.max(0, run) * .55;
  let rightKneeX = Math.max(0, -run) * .55;
  if (crouching) {
    leftHipX = rightHipX = .55;
    leftKneeX = rightKneeX = -1.02;
  } else if (airborne) {
    leftHipX = .34 - jumpImpulse * .03;
    rightHipX = -.18 + jumpImpulse * .02;
    leftKneeX = -.76;
    rightKneeX = -.43;
  }

  let leftShoulderX = run;
  let rightShoulderX = -run;
  let leftShoulderZ = -leftRaise * 2.08;
  let rightShoulderZ = rightRaise * 2.08;
  let leftElbowX = -.16 - leftRaise * .22;
  let rightElbowX = -.16 - rightRaise * .22;
  if ((p.leftHandRemaining || 0) > 0) leftShoulderZ = Math.min(leftShoulderZ, -2.65);
  if ((p.rightHandRemaining || 0) > 0) rightShoulderZ = Math.max(rightShoulderZ, 2.65);

  if ((p.punchRemaining || 0) > 0 && p.punchSide) {
    const progress = 1 - clamp(p.punchRemaining / .34, 0, 1);
    const thrust = Math.sin(progress * Math.PI);
    if (p.punchSide === 'left') {
      leftShoulderX = mix(leftShoulderX, 1.62, thrust);
      leftElbowX = mix(leftElbowX, -.05, thrust);
    } else {
      rightShoulderX = mix(rightShoulderX, 1.62, thrust);
      rightElbowX = mix(rightElbowX, -.05, thrust);
    }
  }

  const landing = !airborne && (p.vy || 0) < -.1 ? clamp(-(p.vy || 0) / 8, 0, 1) : 0;
  if (landing > 0) bodyScaleY *= 1 - landing * .12;
  return {
    bodyScaleY, hipY,
    torsoRoll: -lean * .34 - centerX * .08,
    torsoPitch: crouching ? .16 : airborne ? -.06 : .02 + Math.abs(Math.sin(runPhase * 2)) * .025,
    torsoYaw: lean * .08,
    leftShoulderX, rightShoulderX, leftShoulderZ, rightShoulderZ,
    leftElbowX, rightElbowX, leftHipX, rightHipX, leftKneeX, rightKneeX,
    bobY: airborne || crouching ? 0 : Math.abs(Math.sin(runPhase * 2)) * .06,
    landing,
  };
}

export function applyCharacterPose(rig, pose, damp, dt) {
  if (!rig || !pose) return;
  const d = (current, target, speed = 18) => typeof damp === 'function' ? damp(current, target, speed, dt) : target;
  rig.body.scale.y = d(rig.body.scale.y, pose.bodyScaleY, 16);
  rig.body.position.y = d(rig.body.position.y, pose.bobY + pose.hipY, 18);
  rig.body.rotation.z = d(rig.body.rotation.z, pose.torsoRoll, 20);
  rig.body.rotation.x = d(rig.body.rotation.x, pose.torsoPitch, 18);
  rig.body.rotation.y = d(rig.body.rotation.y, pose.torsoYaw, 18);
  rig.leftArm.shoulder.rotation.x = d(rig.leftArm.shoulder.rotation.x, pose.leftShoulderX, 24);
  rig.rightArm.shoulder.rotation.x = d(rig.rightArm.shoulder.rotation.x, pose.rightShoulderX, 24);
  rig.leftArm.shoulder.rotation.z = d(rig.leftArm.shoulder.rotation.z, pose.leftShoulderZ, 22);
  rig.rightArm.shoulder.rotation.z = d(rig.rightArm.shoulder.rotation.z, pose.rightShoulderZ, 22);
  rig.leftArm.elbow.rotation.x = d(rig.leftArm.elbow.rotation.x, pose.leftElbowX, 22);
  rig.rightArm.elbow.rotation.x = d(rig.rightArm.elbow.rotation.x, pose.rightElbowX, 22);
  rig.leftLeg.hip.rotation.x = d(rig.leftLeg.hip.rotation.x, pose.leftHipX, 18);
  rig.rightLeg.hip.rotation.x = d(rig.rightLeg.hip.rotation.x, pose.rightHipX, 18);
  rig.leftLeg.knee.rotation.x = d(rig.leftLeg.knee.rotation.x, pose.leftKneeX, 18);
  rig.rightLeg.knee.rotation.x = d(rig.rightLeg.knee.rotation.x, pose.rightKneeX, 18);
}
