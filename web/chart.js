// "Money in & out" chart: one column per day, deposits rise above the zero line, withdrawals hang below it.
const NS = "http://www.w3.org/2000/svg";
const DAYS = 14;
const HEIGHT = 240;
const dayLabel = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" });
const fullDay = new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short" });

function el(name, attrs = {}) {
	const node = document.createElementNS(NS, name);
	for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
	return node;
}

/** Rounds up to 1, 2 or 5 times a power of ten, for tidy axis labels. */
function niceCeil(v) {
	if (v <= 0) return 0;
	const p = 10 ** Math.floor(Math.log10(v));
	const n = v / p;
	return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p;
}

/** A bar with 4px rounded corners on the end away from the baseline. */
function barPath(x, base, w, h, up) {
	const r = Math.min(4, w / 2, h);
	if (up) {
		const top = base - h;
		return `M${x},${base}V${top + r}Q${x},${top} ${x + r},${top}H${x + w - r}Q${x + w},${top} ${x + w},${top + r}V${base}Z`;
	}
	const bottom = base + h;
	return `M${x},${base}V${bottom - r}Q${x},${bottom} ${x + r},${bottom}H${x + w - r}Q${x + w},${bottom} ${x + w},${bottom - r}V${base}Z`;
}

/** Totals per day for the last 14 days. Transfers are left out: they move money, they don't add or remove it. */
function dailyTotals(transactions) {
	const today = new Date();
	today.setHours(0, 0, 0, 0);
	const days = [];
	for (let i = DAYS - 1; i >= 0; i--) {
		const d = new Date(today);
		d.setDate(d.getDate() - i);
		days.push({ date: d, start: d.getTime(), in: 0, out: 0 });
	}
	for (const t of transactions) {
		if (t.time < days[0].start || t.kind === "TRANSFER_IN" || t.kind === "TRANSFER_OUT") continue;
		const day = days.findLast(d => d.start <= t.time);
		if (t.credit) day.in += t.amount;
		else day.out += t.amount;
	}
	return days;
}

export function renderFlowChart(container, transactions, { money, moneyShort, showTooltip, hideTooltip }) {
	const days = dailyTotals(transactions);
	const maxIn = niceCeil(Math.max(...days.map(d => d.in)));
	const maxOut = niceCeil(Math.max(...days.map(d => d.out)));
	if (!maxIn && !maxOut) {
		container.innerHTML = `<div class="chart-empty">No deposits or withdrawals in the last 14 days.</div>`;
		return;
	}

	const width = Math.max(280, container.clientWidth);
	const m = { top: 8, right: 2, bottom: 26, left: 50 };
	const plotH = HEIGHT - m.top - m.bottom;
	const range = maxIn + maxOut;
	const zeroY = m.top + plotH * (maxIn / range);
	const scale = v => (v / range) * plotH;
	const band = (width - m.left - m.right) / DAYS;
	const barW = Math.min(22, band * 0.56);

	const svg = el("svg", { viewBox: `0 0 ${width} ${HEIGHT}`, role: "img", "aria-label": "Money in and out per day for the last 14 days" });

	// grid and y labels
	const ticks = [maxIn, maxIn / 2, -maxOut / 2, -maxOut].filter(v => v !== 0);
	for (const v of ticks) {
		const y = zeroY - Math.sign(v) * scale(Math.abs(v));
		svg.append(el("line", { class: "grid", x1: m.left, x2: width - m.right, y1: y, y2: y }));
		const label = el("text", { class: "tick", x: m.left - 8, y: y + 4, "text-anchor": "end" });
		label.textContent = (v < 0 ? "−" : "") + moneyShort.format(Math.abs(v));
		svg.append(label);
	}

	// bars, x labels and hover targets
	const every = Math.ceil(48 / band);
	days.forEach((d, i) => {
		const x = m.left + i * band;
		const cx = x + band / 2;
		if (d.in > 0) {
			svg.append(el("path", { class: "bar-in", d: barPath(cx - barW / 2, zeroY - 1, barW, Math.max(2, scale(d.in) - 1), true) }));
		}
		if (d.out > 0) {
			svg.append(el("path", { class: "bar-out", d: barPath(cx - barW / 2, zeroY + 1, barW, Math.max(2, scale(d.out) - 1), false) }));
		}
		if ((DAYS - 1 - i) % every === 0) {
			const label = el("text", { class: "tick", x: cx, y: HEIGHT - 6, "text-anchor": "middle" });
			label.textContent = i === DAYS - 1 ? "Today" : dayLabel.format(d.date);
			svg.append(label);
		}
		const title = fullDay.format(d.date);
		const rows = [
			{ color: "var(--in)", label: "In", value: money.format(d.in) },
			{ color: "var(--out)", label: "Out", value: money.format(d.out) },
		];
		const hit = el("rect", {
			class: "hit", x, y: m.top, width: band, height: plotH, rx: 6, tabindex: 0,
			"aria-label": `${title}: in ${rows[0].value}, out ${rows[1].value}`,
		});
		hit.addEventListener("pointermove", e => showTooltip(e.clientX, e.clientY, title, rows));
		hit.addEventListener("pointerleave", hideTooltip);
		hit.addEventListener("focus", () => {
			const r = hit.getBoundingClientRect();
			showTooltip(r.left + r.width / 2, r.top + 20, title, rows);
		});
		hit.addEventListener("blur", hideTooltip);
		svg.append(hit);
	});
	svg.append(el("line", { class: "zero", x1: m.left, x2: width - m.right, y1: zeroY, y2: zeroY }));

	container.replaceChildren(svg);
}
