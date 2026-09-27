import { useEffect, useRef, useState } from "react";
import * as THREE from "three";

type PanoramaViewerProps = { imagePath: string };

export default function PanoramaViewer({ imagePath }: PanoramaViewerProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    setLoadFailed(false);
    let disposed = false;
    let texture: THREE.Texture | undefined;
    let dragging = false;
    let previousX = 0;
    let previousY = 0;
    let yaw = 0;
    let pitch = 0;

    const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setSize(canvas.clientWidth, canvas.clientHeight, false);

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(78, canvas.clientWidth / Math.max(canvas.clientHeight, 1), 0.1, 1200);
    const geometry = new THREE.SphereGeometry(500, 72, 48);
    const material = new THREE.MeshBasicMaterial({ color: 0xffffff, side: THREE.BackSide });
    const sphere = new THREE.Mesh(geometry, material);
    scene.add(sphere);

    const render = () => {
      sphere.rotation.order = "YXZ";
      sphere.rotation.y = yaw;
      sphere.rotation.x = pitch;
      renderer.render(scene, camera);
    };

    const resize = () => {
      const width = canvas.clientWidth;
      const height = canvas.clientHeight;
      if (!width || !height) return;
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      renderer.setSize(width, height, false);
      render();
    };

    const loader = new THREE.TextureLoader();
    loader.load(
      imagePath,
      (loadedTexture) => {
        if (disposed) {
          loadedTexture.dispose();
          return;
        }
        texture = loadedTexture;
        texture.colorSpace = THREE.SRGBColorSpace;
        texture.anisotropy = renderer.capabilities.getMaxAnisotropy();
        material.map = texture;
        material.color.set(0xffffff);
        material.needsUpdate = true;
        render();
      },
      undefined,
      () => {
        if (!disposed) setLoadFailed(true);
      },
    );

    const pointerDown = (event: PointerEvent) => {
      dragging = true;
      previousX = event.clientX;
      previousY = event.clientY;
      canvas.setPointerCapture(event.pointerId);
      canvas.classList.add("is-dragging");
    };
    const pointerMove = (event: PointerEvent) => {
      if (!dragging) return;
      yaw += (event.clientX - previousX) * 0.004;
      pitch = THREE.MathUtils.clamp(pitch + (event.clientY - previousY) * 0.004, -1.42, 1.42);
      previousX = event.clientX;
      previousY = event.clientY;
      render();
    };
    const pointerUp = () => {
      dragging = false;
      canvas.classList.remove("is-dragging");
    };
    const wheel = (event: WheelEvent) => {
      event.preventDefault();
      camera.fov = THREE.MathUtils.clamp(camera.fov + Math.sign(event.deltaY) * 3, 42, 98);
      camera.updateProjectionMatrix();
      render();
    };
    const keyDown = (event: KeyboardEvent) => {
      const rotationStep = 0.08;
      if (event.key === "ArrowLeft") yaw -= rotationStep;
      else if (event.key === "ArrowRight") yaw += rotationStep;
      else if (event.key === "ArrowUp") pitch = THREE.MathUtils.clamp(pitch + rotationStep, -1.42, 1.42);
      else if (event.key === "ArrowDown") pitch = THREE.MathUtils.clamp(pitch - rotationStep, -1.42, 1.42);
      else if (event.key === "+" || event.key === "=") camera.fov = Math.max(42, camera.fov - 4);
      else if (event.key === "-") camera.fov = Math.min(98, camera.fov + 4);
      else return;
      event.preventDefault();
      camera.updateProjectionMatrix();
      render();
    };

    canvas.addEventListener("pointerdown", pointerDown);
    canvas.addEventListener("pointermove", pointerMove);
    canvas.addEventListener("pointerup", pointerUp);
    canvas.addEventListener("pointercancel", pointerUp);
    canvas.addEventListener("wheel", wheel, { passive: false });
    canvas.addEventListener("keydown", keyDown);
    const observer = new ResizeObserver(resize);
    observer.observe(canvas);
    resize();

    return () => {
      disposed = true;
      observer.disconnect();
      canvas.removeEventListener("pointerdown", pointerDown);
      canvas.removeEventListener("pointermove", pointerMove);
      canvas.removeEventListener("pointerup", pointerUp);
      canvas.removeEventListener("pointercancel", pointerUp);
      canvas.removeEventListener("wheel", wheel);
      canvas.removeEventListener("keydown", keyDown);
      texture?.dispose();
      geometry.dispose();
      material.dispose();
      renderer.dispose();
    };
  }, [imagePath]);

  return (
    <div className="panorama-frame" aria-label="Панорама 360 градусов">
      <canvas ref={canvasRef} className="panorama-canvas" tabIndex={0} aria-label="Потяни, чтобы осмотреться; клавиши со стрелками вращают панораму" />
      {loadFailed && <div className="panorama-error">Не удалось загрузить панораму. Проверьте файлы в <code>public/panoramas</code>.</div>}
      <div className="provider-mask provider-mask-top" />
      <div className="provider-mask provider-mask-bottom" />
      <div className="panorama-wash" />
    </div>
  );
}
