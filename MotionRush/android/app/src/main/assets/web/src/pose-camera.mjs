const INDEX = {
  nose: 0,
  leftShoulder: 11,
  rightShoulder: 12,
  leftWrist: 15,
  rightWrist: 16,
  leftHip: 23,
  rightHip: 24,
  leftKnee: 25,
  rightKnee: 26,
};

export function mapPoseLandmarks(landmarks) {
  const out = {};
  for (const [name, index] of Object.entries(INDEX)) {
    const p = landmarks[index];
    out[name] = { x: 1 - p.x, y: p.y, visibility: p.visibility ?? p.presence ?? 1 };
  }
  return out;
}

export async function createPoseCamera({ video, onFrame, onStatus = () => {} }) {
  let stream = null;
  let landmarker = null;
  let running = false;
  let raf = 0;
  let lastVideoTime = -1;

  async function start() {
    onStatus('requesting-camera');
    stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: 'user',
        width: { ideal: 720 },
        height: { ideal: 1280 },
        frameRate: { ideal: 30, max: 30 },
      },
      audio: false,
    });
    video.srcObject = stream;
    await video.play();
    onStatus('loading-pose');

    const vision = await import('https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14/+esm');
    const fileset = await vision.FilesetResolver.forVisionTasks(
      'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14/wasm'
    );
    landmarker = await vision.PoseLandmarker.createFromOptions(fileset, {
      baseOptions: {
        modelAssetPath: 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task',
        delegate: 'GPU',
      },
      runningMode: 'VIDEO',
      numPoses: 1,
      minPoseDetectionConfidence: 0.55,
      minPosePresenceConfidence: 0.55,
      minTrackingConfidence: 0.55,
    });

    running = true;
    onStatus('tracking');
    tick();
  }

  function tick() {
    if (!running) return;
    const now = performance.now();
    if (video.readyState >= 2 && video.currentTime !== lastVideoTime) {
      lastVideoTime = video.currentTime;
      try {
        const result = landmarker.detectForVideo(video, now);
        if (result.landmarks?.[0]) onFrame(mapPoseLandmarks(result.landmarks[0]), now);
        else onStatus('no-pose');
      } catch (error) {
        onStatus('tracking-error', error);
      }
    }
    raf = requestAnimationFrame(tick);
  }

  function stop() {
    running = false;
    if (raf) cancelAnimationFrame(raf);
    stream?.getTracks().forEach((track) => track.stop());
    stream = null;
    landmarker?.close?.();
    landmarker = null;
  }

  return { start, stop };
}
