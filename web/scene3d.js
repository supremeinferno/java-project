// The two three.js scenes: a floating bank card with coins on the login page,
// and one tower per account on the dashboard. app.js loads this file only if WebGL works.
import * as THREE from "./vendor/three/three.module.min.js";

const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;

// colours for the dark hero background (the dark-mode steps of the type palette)
const TYPE_COLORS = { SAVINGS: 0x3987e5, CURRENT: 0xc98500, STUDENT: 0xd55181 };

export function webglAvailable() {
	try {
		const c = document.createElement("canvas");
		return Boolean(c.getContext("webgl2") || c.getContext("webgl"));
	} catch (e) {
		return false;
	}
}

function makeRenderer(canvas) {
	const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
	renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
	renderer.outputColorSpace = THREE.SRGBColorSpace;
	renderer.toneMapping = THREE.ACESFilmicToneMapping;
	return renderer;
}

/** Soft studio lights baked into an environment map, so metal and gloss reflect something. */
function studioEnvironment(renderer) {
	const env = new THREE.Scene();
	env.add(new THREE.Mesh(new THREE.SphereGeometry(10, 32, 16), new THREE.MeshBasicMaterial({ color: 0x14203a, side: THREE.BackSide })));
	const panel = (color, strength, position, w, h) => {
		const mesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h),
			new THREE.MeshBasicMaterial({ color: new THREE.Color(color).multiplyScalar(strength), side: THREE.DoubleSide }));
		mesh.position.set(...position);
		mesh.lookAt(0, 0, 0);
		env.add(mesh);
	};
	panel(0xffffff, 5, [0, 6, 4], 8, 3);
	panel(0x5eead4, 3, [-7, 1, 3], 3, 6);
	panel(0xa5b4fc, 3, [7, 2, -2], 3, 6);
	const pmrem = new THREE.PMREMGenerator(renderer);
	const texture = pmrem.fromScene(env, 0.04).texture;
	pmrem.dispose();
	return texture;
}

/**
 * Runs frame(dt, t) every animation frame while the canvas is on screen.
 * Keeps the renderer sized to the canvas. Returns a stop function.
 */
function animate(canvas, renderer, camera, frame) {
	const clock = new THREE.Clock();
	let running = true;
	function fit() {
		const w = canvas.clientWidth;
		const h = canvas.clientHeight;
		if (!w || !h) return false;
		const size = renderer.getSize(new THREE.Vector2());
		if (size.x !== w || size.y !== h) {
			renderer.setSize(w, h, false);
			camera.aspect = w / h;
			camera.updateProjectionMatrix();
		}
		return true;
	}
	function tick() {
		if (!running) return;
		requestAnimationFrame(tick);
		const dt = Math.min(clock.getDelta(), 0.05);
		// offsetParent is null while the canvas (or a parent) is display:none
		if (document.hidden || !canvas.offsetParent || !fit()) return;
		frame(reducedMotion ? 0 : dt, reducedMotion ? 0 : clock.elapsedTime);
	}
	tick();
	return () => { running = false; };
}

// ---------------------------------------------------------------- login scene

function cardTexture() {
	const c = document.createElement("canvas");
	c.width = 1024;
	c.height = 646;
	const g = c.getContext("2d");
	const bg = g.createLinearGradient(0, 0, 1024, 646);
	bg.addColorStop(0, "#0f1b33");
	bg.addColorStop(0.55, "#1d3461");
	bg.addColorStop(1, "#0f766e");
	g.fillStyle = bg;
	g.fillRect(0, 0, 1024, 646);
	const glow = g.createRadialGradient(880, 80, 10, 880, 80, 520);
	glow.addColorStop(0, "rgba(94,234,212,0.55)");
	glow.addColorStop(1, "rgba(94,234,212,0)");
	g.fillStyle = glow;
	g.fillRect(0, 0, 1024, 646);
	// wavy lines
	g.strokeStyle = "rgba(255,255,255,0.06)";
	g.lineWidth = 2;
	for (let i = 0; i < 14; i++) {
		g.beginPath();
		for (let x = 0; x <= 1024; x += 16) {
			const y = 330 + i * 26 + Math.sin(x / 140 + i * 0.5) * 40;
			x ? g.lineTo(x, y) : g.moveTo(x, y);
		}
		g.stroke();
	}
	// chip
	const chip = g.createLinearGradient(80, 200, 220, 300);
	chip.addColorStop(0, "#f6dc9a");
	chip.addColorStop(1, "#c79a3c");
	g.fillStyle = chip;
	g.beginPath();
	g.roundRect(80, 200, 140, 104, 16);
	g.fill();
	g.strokeStyle = "rgba(0,0,0,0.25)";
	g.lineWidth = 3;
	g.beginPath();
	g.moveTo(80, 252); g.lineTo(220, 252);
	g.moveTo(150, 200); g.lineTo(150, 304);
	g.stroke();
	// contactless
	g.strokeStyle = "rgba(255,255,255,0.8)";
	g.lineWidth = 7;
	g.lineCap = "round";
	for (let i = 0; i < 3; i++) {
		g.beginPath();
		g.arc(250, 252, 22 + i * 18, -0.7, 0.7);
		g.stroke();
	}
	g.fillStyle = "#ffffff";
	g.font = "700 40px system-ui, -apple-system, sans-serif";
	g.fillText("BANKING SYSTEM", 80, 120);
	g.font = "600 30px system-ui, -apple-system, sans-serif";
	g.fillStyle = "rgba(255,255,255,0.7)";
	g.textAlign = "right";
	g.fillText("PREMIER", 944, 120);
	g.textAlign = "left";
	g.fillStyle = "#ffffff";
	g.font = "500 62px ui-monospace, Menlo, monospace";
	g.fillText("4096  2731  8890  1024", 80, 450);
	g.font = "600 30px system-ui, -apple-system, sans-serif";
	g.fillStyle = "rgba(255,255,255,0.85)";
	g.fillText("ADMIN", 80, 560);
	g.fillText("12 / 30", 760, 560);
	const texture = new THREE.CanvasTexture(c);
	texture.colorSpace = THREE.SRGBColorSpace;
	texture.anisotropy = 8;
	return texture;
}

function coinTexture() {
	const c = document.createElement("canvas");
	c.width = c.height = 256;
	const g = c.getContext("2d");
	const face = g.createRadialGradient(100, 90, 10, 128, 128, 140);
	face.addColorStop(0, "#ffe9a8");
	face.addColorStop(1, "#d9a43a");
	g.fillStyle = face;
	g.fillRect(0, 0, 256, 256);
	g.strokeStyle = "rgba(120,80,10,0.45)";
	g.lineWidth = 8;
	g.beginPath();
	g.arc(128, 128, 104, 0, Math.PI * 2);
	g.stroke();
	g.fillStyle = "rgba(120,80,10,0.55)";
	g.font = "700 130px system-ui, -apple-system, sans-serif";
	g.textAlign = "center";
	g.textBaseline = "middle";
	g.fillText("₹", 128, 136);
	const texture = new THREE.CanvasTexture(c);
	texture.colorSpace = THREE.SRGBColorSpace;
	return texture;
}

function roundedRect(w, h, r) {
	const s = new THREE.Shape();
	const x = -w / 2;
	const y = -h / 2;
	s.moveTo(x + r, y);
	s.lineTo(x + w - r, y);
	s.quadraticCurveTo(x + w, y, x + w, y + r);
	s.lineTo(x + w, y + h - r);
	s.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
	s.lineTo(x + r, y + h);
	s.quadraticCurveTo(x, y + h, x, y + h - r);
	s.lineTo(x, y + r);
	s.quadraticCurveTo(x, y, x + r, y);
	return s;
}

function cardBackTexture() {
	const c = document.createElement("canvas");
	c.width = 1024;
	c.height = 646;
	const g = c.getContext("2d");
	const bg = g.createLinearGradient(0, 0, 1024, 646);
	bg.addColorStop(0, "#14223f");
	bg.addColorStop(1, "#0b1324");
	g.fillStyle = bg;
	g.fillRect(0, 0, 1024, 646);
	g.fillStyle = "#05080f"; // magnetic stripe
	g.fillRect(0, 70, 1024, 120);
	g.fillStyle = "#e9edf5"; // signature strip
	g.fillRect(70, 250, 620, 90);
	g.strokeStyle = "rgba(15,118,110,0.35)";
	g.lineWidth = 2;
	for (let x = 70; x < 690; x += 14) {
		g.beginPath();
		g.moveTo(x, 250);
		g.lineTo(x + 40, 340);
		g.stroke();
	}
	g.fillStyle = "#1c2b4a";
	g.font = "italic 600 46px 'Snell Roundhand', 'Segoe Script', cursive";
	g.fillText("Admin", 110, 312);
	g.fillStyle = "#ffffff";
	g.fillRect(710, 250, 130, 90);
	g.fillStyle = "#0b1324";
	g.font = "italic 600 40px ui-monospace, Menlo, monospace";
	g.fillText("731", 735, 310);
	const holo = g.createLinearGradient(860, 250, 960, 340); // hologram
	holo.addColorStop(0, "#5eead4");
	holo.addColorStop(0.5, "#a5b4fc");
	holo.addColorStop(1, "#f9a8d4");
	g.fillStyle = holo;
	g.beginPath();
	g.roundRect(860, 250, 100, 90, 12);
	g.fill();
	g.fillStyle = "rgba(255,255,255,0.6)";
	g.font = "500 24px system-ui, -apple-system, sans-serif";
	g.fillText("This card is the property of Banking System and must be returned on request.", 70, 420);
	g.fillText("Customer care: 1800 000 0000  ·  Valid for demo use only", 70, 460);
	g.fillStyle = "#ffffff";
	g.font = "700 34px system-ui, -apple-system, sans-serif";
	g.fillText("BANKING SYSTEM", 70, 580);
	const texture = new THREE.CanvasTexture(c);
	texture.colorSpace = THREE.SRGBColorSpace;
	texture.anisotropy = 8;
	return texture;
}

const TAU = Math.PI * 2;
const easeOutCubic = x => 1 - (1 - x) ** 3;
const easeOutBack = x => 1 + 2.2 * (x - 1) ** 3 + 1.2 * (x - 1) ** 2;

/**
 * The login card. It flies in spinning, floats, flips over every few seconds to show its back,
 * can be dragged to spin (it keeps spinning, then springs back to the front), and flips when clicked.
 */
export function createLoginScene(canvas) {
	const renderer = makeRenderer(canvas);
	const scene = new THREE.Scene();
	scene.environment = studioEnvironment(renderer);
	const camera = new THREE.PerspectiveCamera(35, 1, 0.1, 100);
	camera.position.set(0, 0, 10);

	scene.add(new THREE.AmbientLight(0xffffff, 0.35));
	const key = new THREE.DirectionalLight(0xffffff, 1.5);
	key.position.set(3, 5, 6);
	scene.add(key);

	const rig = new THREE.Group(); // position, entrance and float
	scene.add(rig);
	const card = new THREE.Group(); // rotation
	rig.add(card);

	// the card: a metal-edged rounded slab with a printed face on each side
	const W = 3.4;
	const H = 2.14;
	const shape = roundedRect(W, H, 0.18);
	const slab = new THREE.ExtrudeGeometry(shape, {
		depth: 0.04, bevelEnabled: true, bevelThickness: 0.015, bevelSize: 0.015, bevelSegments: 3, curveSegments: 12,
	});
	slab.translate(0, 0, -0.02);
	card.add(new THREE.Mesh(slab, new THREE.MeshStandardMaterial({ color: 0xb8c4dc, metalness: 0.9, roughness: 0.3 })));
	const faceGeo = new THREE.ShapeGeometry(shape, 12);
	const face = map => {
		map.repeat.set(1 / W, 1 / H); // shape UVs are in card units
		map.offset.set(0.5, 0.5);
		return new THREE.Mesh(faceGeo, new THREE.MeshPhysicalMaterial({
			map, roughness: 0.35, metalness: 0.15, clearcoat: 1, clearcoatRoughness: 0.1,
		}));
	};
	const front = face(cardTexture());
	front.position.z = 0.0365;
	const back = face(cardBackTexture());
	back.position.z = -0.0365;
	back.rotation.y = Math.PI;
	card.add(front, back);

	// a band of light that sweeps across the front now and then; it uses the card's own shape, so it never spills off it
	const shineCanvas = document.createElement("canvas");
	shineCanvas.width = shineCanvas.height = 256;
	const sg = shineCanvas.getContext("2d");
	const band = sg.createLinearGradient(0, 160, 256, 96); // tilted, with both side edges fully transparent
	band.addColorStop(0.3, "rgba(255,255,255,0)");
	band.addColorStop(0.5, "rgba(255,255,255,0.32)");
	band.addColorStop(0.7, "rgba(255,255,255,0)");
	sg.fillStyle = band;
	sg.fillRect(0, 0, 256, 256);
	const shineMap = new THREE.CanvasTexture(shineCanvas);
	shineMap.repeat.set(1 / 1.4, 1 / H); // the band texture covers 1.4 card units across
	const shine = new THREE.Mesh(faceGeo, new THREE.MeshBasicMaterial({
		map: shineMap, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false,
	}));
	shine.position.z = 0.037;
	card.add(shine);

	// coins orbiting the card
	const coinMap = coinTexture();
	// low envMapIntensity: the studio's teal panel would otherwise tint the gold green
	const gold = new THREE.MeshStandardMaterial({ color: 0xf0c050, metalness: 1, roughness: 0.3, envMapIntensity: 0.45, emissive: 0x3a2400, emissiveIntensity: 0.6 });
	const coinFace = new THREE.MeshStandardMaterial({ map: coinMap, metalness: 0.8, roughness: 0.35, envMapIntensity: 0.45, emissive: 0x3a2400, emissiveIntensity: 0.6 });
	const coinGeo = new THREE.CylinderGeometry(0.34, 0.34, 0.07, 48);
	const coins = [];
	for (let i = 0; i < 7; i++) {
		const coin = new THREE.Mesh(coinGeo, [gold, coinFace, coinFace]);
		coin.userData = {
			radius: 2.3 + (i % 3) * 0.45,
			speed: 0.22 + (i % 4) * 0.05,
			phase: (i / 7) * TAU,
			height: ((i * 37) % 10) / 10 - 0.5,
			spin: 0.6 + (i % 3) * 0.4,
		};
		coin.scale.setScalar(0.75 + (i % 3) * 0.18);
		coins.push(coin);
		rig.add(coin);
	}

	// dust
	const count = 260;
	const positions = new Float32Array(count * 3);
	for (let i = 0; i < count; i++) {
		positions[i * 3] = (Math.random() - 0.5) * 18;
		positions[i * 3 + 1] = (Math.random() - 0.5) * 10;
		positions[i * 3 + 2] = (Math.random() - 0.5) * 8 - 2;
	}
	const dustGeo = new THREE.BufferGeometry();
	dustGeo.setAttribute("position", new THREE.BufferAttribute(positions, 3));
	const dust = new THREE.Points(dustGeo, new THREE.PointsMaterial({ color: 0x8fe9dc, size: 0.035, transparent: true, opacity: 0.55 }));
	scene.add(dust);

	// ---- motion state
	let life = 0;            // seconds the scene has been on screen
	let spin = 0;            // card turn around Y, in radians
	let spinVel = 0;
	let spinTarget = 0;      // always a whole number of turns, so the card rests face up
	let nextFlip = 6;
	let burst = 0;           // coins fly outward when the card flips, then settle
	let lift = 0;
	let hovered = false;
	let drag = null;
	const pointer = new THREE.Vector2();
	const raycaster = new THREE.Raycaster();

	function flip() {
		spinTarget = Math.round(spin / TAU) * TAU + TAU;
		burst = 1;
		nextFlip = life + 7;
	}
	function hitsCard(e) {
		const rect = canvas.getBoundingClientRect();
		const ndc = new THREE.Vector2(((e.clientX - rect.left) / rect.width) * 2 - 1, -((e.clientY - rect.top) / rect.height) * 2 + 1);
		raycaster.setFromCamera(ndc, camera);
		return raycaster.intersectObject(card, true).length > 0;
	}

	window.addEventListener("pointermove", e => {
		pointer.set(e.clientX / innerWidth - 0.5, e.clientY / innerHeight - 0.5);
		if (drag) {
			const now = performance.now();
			const dx = e.clientX - drag.x;
			drag.moved = Math.max(drag.moved, Math.abs(e.clientX - drag.startX));
			spinVel = (dx * 0.012) / Math.max(0.008, (now - drag.time) / 1000);
			spin += dx * 0.012;
			drag.x = e.clientX;
			drag.time = now;
		} else if (e.target === canvas) {
			hovered = hitsCard(e);
			canvas.style.cursor = hovered ? "grab" : "";
		}
	});
	canvas.addEventListener("pointerdown", e => {
		if (!hitsCard(e)) return;
		drag = { x: e.clientX, startX: e.clientX, time: performance.now(), moved: 0 };
		canvas.style.cursor = "grabbing";
	});
	window.addEventListener("pointerup", () => {
		if (!drag) return;
		if (drag.moved < 5) {
			flip();
		} else {
			// land on the nearest face-up turn in the direction of the throw
			spinTarget = Math.round((spin + spinVel * 0.35) / TAU) * TAU;
			burst = Math.min(1, Math.abs(spinVel) / 20);
			nextFlip = life + 7;
		}
		if (reducedMotion) spin = spinTarget;
		drag = null;
		canvas.style.cursor = hovered ? "grab" : "";
	});

	return {
		stop: animate(canvas, renderer, camera, (dt, t) => {
			life += dt;
			const wide = camera.aspect > 1.15;
			const home = new THREE.Vector3(wide ? 1.3 : 0, wide ? 0.3 : 0.55, 0);
			const size = wide ? 1 : 0.8;

			// entrance: fly in from the lower right while spinning two and a half turns
			const intro = reducedMotion ? 1 : Math.min(1, life / 1.8);
			const e = easeOutCubic(intro);
			rig.position.set(home.x + (1 - e) * 4, home.y - (1 - e) * 2.5 + Math.sin(t * 1.1) * 0.1 * e, (1 - e) * -6);
			lift += ((hovered || drag ? 1 : 0) - lift) * Math.min(1, dt * 8);
			rig.scale.setScalar(size * (0.35 + 0.65 * easeOutBack(intro)) * (1 + lift * 0.05));

			// spin: a spring pulls the card to its target turn, with a little overshoot
			if (!drag) {
				if (life > nextFlip && intro === 1) flip();
				spinVel += (spinTarget - spin) * 30 * dt;
				spinVel *= Math.exp(-6 * dt);
				spin += spinVel * dt;
			}
			const introSpin = (1 - e) * -TAU * 2.5;
			card.rotation.y = spin + introSpin + pointer.x * 0.5 - 0.3 + Math.sin(t * 0.5) * 0.1;
			card.rotation.x = pointer.y * 0.35 + 0.12 + Math.cos(t * 0.4) * 0.05;
			card.rotation.z = Math.sin(t * 0.7) * 0.04 + (1 - e) * 0.6;

			// shine sweeps left to right every 4 seconds (texture u = x / 1.4 + offset; the band is off the card outside 0..1)
			const sweep = Math.min(1, (t % 4) / 1.1);
			shineMap.offset.set(2 - sweep * 3.6, 0.5);

			burst *= Math.exp(-2.2 * dt);
			for (const coin of coins) {
				const d = coin.userData;
				const a = d.phase + t * d.speed + burst * 1.5;
				const r = d.radius * (1 + burst * 0.45) * e;
				coin.position.set(Math.cos(a) * r, d.height + Math.sin(t * 0.9 + d.phase) * 0.25 + burst * Math.sin(d.phase * 3) * 0.6, Math.sin(a) * r * 0.55);
				coin.rotation.set(Math.PI / 2 + Math.sin(t + d.phase) * 0.4, 0, t * d.spin + burst * 6);
			}
			dust.rotation.y = t * 0.015;
			renderer.render(scene, camera);
		}),
	};
}

// ---------------------------------------------------------------- tower scene

/**
 * One tower per account; height grows with the square root of the balance so small accounts stay visible.
 * callbacks: onHover(account | null, clientX, clientY), onClick(account)
 */
export function createTowerScene(canvas, { onHover, onClick }) {
	const renderer = makeRenderer(canvas);
	const scene = new THREE.Scene();
	scene.environment = studioEnvironment(renderer);
	const camera = new THREE.PerspectiveCamera(36, 1, 0.1, 200);

	scene.add(new THREE.HemisphereLight(0xcfe8ff, 0x0b1324, 0.7));
	const sun = new THREE.DirectionalLight(0xffffff, 1.4);
	sun.position.set(4, 8, 5);
	scene.add(sun);

	const platform = new THREE.Mesh(new THREE.CylinderGeometry(1, 1.04, 0.16, 96),
		new THREE.MeshStandardMaterial({ color: 0x16223d, metalness: 0.5, roughness: 0.45 }));
	platform.position.y = -0.08;
	scene.add(platform);
	const ring = new THREE.Mesh(new THREE.TorusGeometry(1, 0.012, 8, 160),
		new THREE.MeshBasicMaterial({ color: 0x2bb3a5, transparent: true, opacity: 0.8 }));
	ring.rotation.x = Math.PI / 2;
	scene.add(ring);

	const towerGeo = new THREE.BoxGeometry(0.6, 1, 0.6);
	towerGeo.translate(0, 0.5, 0); // grow from the floor
	const towers = new Map(); // accNum -> mesh

	let radius = 2;
	let azimuth = 0.7;
	let hovered = null;

	function update(accounts) {
		const max = Math.max(1, ...accounts.map(a => a.balance));
		const cols = Math.max(1, Math.ceil(Math.sqrt(accounts.length)));
		const rows = Math.ceil(accounts.length / cols);
		const gap = 0.95;
		const seen = new Set();
		accounts.forEach((acc, i) => {
			seen.add(acc.accNum);
			let mesh = towers.get(acc.accNum);
			if (!mesh) {
				mesh = new THREE.Mesh(towerGeo, new THREE.MeshStandardMaterial({ metalness: 0.25, roughness: 0.3 }));
				mesh.scale.y = 0.001;
				towers.set(acc.accNum, mesh);
				scene.add(mesh);
			}
			const color = new THREE.Color(TYPE_COLORS[acc.type] || 0x8899aa);
			mesh.material.color.copy(color);
			mesh.material.emissive.copy(color);
			mesh.material.emissiveIntensity = 0.12;
			mesh.userData.account = acc;
			mesh.userData.target = 0.25 + 2.6 * Math.sqrt(acc.balance / max);
			mesh.position.set(((i % cols) - (cols - 1) / 2) * gap, 0, (Math.floor(i / cols) - (rows - 1) / 2) * gap);
		});
		for (const [accNum, mesh] of towers) {
			if (!seen.has(accNum)) {
				scene.remove(mesh);
				mesh.material.dispose();
				towers.delete(accNum);
			}
		}
		radius = Math.max(1.6, (Math.max(cols, rows) * gap) / Math.SQRT2 + 0.8);
		platform.scale.set(radius, 1, radius);
		ring.scale.setScalar(radius);
	}

	// drag to turn, click to open
	const raycaster = new THREE.Raycaster();
	const ndc = new THREE.Vector2();
	let drag = null;

	function pick(e) {
		const rect = canvas.getBoundingClientRect();
		ndc.set(((e.clientX - rect.left) / rect.width) * 2 - 1, -((e.clientY - rect.top) / rect.height) * 2 + 1);
		raycaster.setFromCamera(ndc, camera);
		const hit = raycaster.intersectObjects([...towers.values()])[0];
		return hit ? hit.object : null;
	}
	function setHovered(mesh, e) {
		if (hovered && hovered !== mesh) hovered.material.emissiveIntensity = 0.12;
		hovered = mesh;
		if (mesh) {
			mesh.material.emissiveIntensity = 0.45;
			onHover(mesh.userData.account, e.clientX, e.clientY);
		} else {
			onHover(null);
		}
		canvas.style.cursor = mesh ? "pointer" : "";
	}
	canvas.addEventListener("pointerdown", e => {
		drag = { x: e.clientX, start: azimuth, moved: 0 };
		canvas.setPointerCapture(e.pointerId);
	});
	canvas.addEventListener("pointermove", e => {
		if (drag) {
			drag.moved = Math.max(drag.moved, Math.abs(e.clientX - drag.x));
			azimuth = drag.start - (e.clientX - drag.x) * 0.01;
			if (drag.moved > 4) setHovered(null, e);
			return;
		}
		setHovered(pick(e), e);
	});
	canvas.addEventListener("pointerup", e => {
		const click = drag && drag.moved <= 4;
		drag = null;
		if (click) {
			const mesh = pick(e);
			if (mesh) onClick(mesh.userData.account);
		}
	});
	canvas.addEventListener("pointerleave", e => { if (!drag) setHovered(null, e); });

	animate(canvas, renderer, camera, (dt, t) => {
		if (!drag && !hovered) azimuth += dt * 0.18;
		const distance = radius * 2.3 + 3.2;
		camera.position.set(Math.sin(azimuth) * distance, distance * 0.62, Math.cos(azimuth) * distance);
		camera.lookAt(0, 0.9, 0);
		for (const mesh of towers.values()) {
			const target = mesh.userData.target;
			mesh.scale.y += (target - mesh.scale.y) * (reducedMotion ? 1 : 0.08);
		}
		ring.material.opacity = 0.55 + Math.sin(t * 1.5) * 0.25;
		renderer.render(scene, camera);
	});

	return { update };
}
