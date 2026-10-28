import { renderFlowChart } from "./chart.js";

// ================================================================ state & helpers

const state = {
	types: [],          // { id, label, minBalance, extraLabel }
	accounts: [],       // see WebServer.json(BankAccount)
	transactions: [],   // oldest first
	accSearch: "",
	typeFilter: "ALL",
	sort: { key: "accNum", dir: 1 },
	txSearch: "",
	kindFilter: "ALL",
};

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const money = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });
const moneyShort = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", notation: "compact", maximumFractionDigits: 1 });
const dateTime = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
const monthYear = new Intl.DateTimeFormat("en-IN", { month: "2-digit", year: "2-digit" });
const longDate = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "long", year: "numeric" });

function esc(value) {
	return String(value).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
const icon = name => `<svg class="icon"><use href="#i-${name}"/></svg>`;
const initials = name => name.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0].toUpperCase()).join("");
const sum = list => list.reduce((s, v) => s + v, 0);
const findAccount = accNum => state.accounts.find(a => a.accNum === String(accNum || "").trim());
const typeOf = id => state.types.find(t => t.id === id);
/** The type-specific details, formatted for people (the server's getDetails() prints raw doubles). */
function detailsText(acc) {
	const limit = acc.withdrawLimit === null ? "" : `Max ${money.format(acc.withdrawLimit)} per withdrawal`;
	if (acc.type === "SAVINGS") return limit;
	const extra = `${typeOf(acc.type) ? typeOf(acc.type).extraLabel : "Details"}: ${acc.extra}`;
	return limit ? `${extra} · ${limit}` : extra;
}
const badge = acc => `<span class="badge type-${esc(acc.type)}">${esc(acc.typeLabel)}</span>`;

const KIND_ICON = { OPEN: "open", DEPOSIT: "in", WITHDRAW: "out", TRANSFER_IN: "transfer", TRANSFER_OUT: "transfer", CLOSE: "close" };
const KIND_FILTERS = [
	{ id: "ALL", label: "All", kinds: null },
	{ id: "DEPOSIT", label: "Deposits", kinds: ["DEPOSIT"] },
	{ id: "WITHDRAW", label: "Withdrawals", kinds: ["WITHDRAW"] },
	{ id: "TRANSFER", label: "Transfers", kinds: ["TRANSFER_IN", "TRANSFER_OUT"] },
	{ id: "LIFECYCLE", label: "Opened & closed", kinds: ["OPEN", "CLOSE"] },
];

function describe(t) {
	if (t.kind === "TRANSFER_OUT") return `Transfer to ${t.otherAccNum}`;
	if (t.kind === "TRANSFER_IN") return `Transfer from ${t.otherAccNum}`;
	return t.kindLabel;
}
const signed = t => (t.credit ? "+" : "−") + money.format(t.amount);

// ================================================================ server

class Unauthorized extends Error {}

/** GET when data is omitted, otherwise POST as a form. Throws with the server's error message. */
async function api(path, data) {
	const options = data ? { method: "POST", body: new URLSearchParams(data) } : {};
	const res = await fetch("/api/" + path, options);
	const body = await res.json().catch(() => ({}));
	if (res.status === 401 && path !== "login") {
		throw new Unauthorized(body.error);
	}
	if (!res.ok) {
		throw new Error(body.error || "Something went wrong (" + res.status + ")");
	}
	return body;
}

async function refresh() {
	const data = await api("bank");
	state.accounts = data.accounts;
	state.transactions = data.transactions;
}

/** Shows an error in the form's error box (or a toast); a lost session goes back to the login page. */
function handleError(err, form) {
	if (err instanceof Unauthorized) {
		showLogin();
	} else if (form) {
		$(".form-error", form).textContent = err.message;
	} else {
		toast(err.message);
	}
}

/** Disables the submit button while the request runs, and clears/fills the error box. */
async function submitting(form, work) {
	const button = $("[type=submit]", form);
	$(".form-error", form).textContent = "";
	button.disabled = true;
	try {
		await work();
	} catch (err) {
		handleError(err, form);
	} finally {
		button.disabled = false;
	}
}

// ================================================================ 3D (optional: the site works without it)

let scenes = null;
let loginScene = null;
let towers = null;

async function load3d() {
	if (scenes === null) {
		try {
			const mod = await import("./scene3d.js");
			scenes = mod.webglAvailable() ? mod : false;
		} catch (err) {
			console.warn("3D disabled:", err);
			scenes = false;
		}
		if (!scenes) document.body.classList.add("no-webgl");
	}
	return scenes;
}

async function startLoginScene() {
	const mod = await load3d();
	if (mod && !loginScene) loginScene = mod.createLoginScene($("#login-canvas"));
}

async function startTowers() {
	const mod = await load3d();
	if (!mod || towers) return;
	towers = mod.createTowerScene($("#tower-canvas"), {
		onHover(acc, x, y) {
			if (!acc) return hideTooltip();
			showTooltip(x, y, acc.name, [
				{ color: `var(--${acc.type.toLowerCase()})`, label: acc.typeLabel, value: money.format(acc.balance) },
			], acc.accNum);
		},
		onClick(acc) {
			hideTooltip();
			location.hash = "account/" + acc.accNum;
		},
	});
	towers.update(state.accounts);
}

// ================================================================ login / logout

function showLogin() {
	$("#app-view").hidden = true;
	$("#login-view").hidden = false;
	$("#login-form").password.focus();
	startLoginScene();
}

async function showApp() {
	await refresh();
	$("#login-view").hidden = true;
	$("#app-view").hidden = false;
	route();
	startTowers();
}

$("#login-form").addEventListener("submit", e => {
	e.preventDefault();
	const form = e.target;
	showPassword(false);
	submitting(form, async () => {
		try {
			await api("login", { username: form.username.value, password: form.password.value });
		} catch (err) {
			form.password.select();
			throw err;
		}
		form.password.value = "";
		await showApp();
	});
});

/** Shows or hides the password; it is hidden again after every login attempt. */
function showPassword(visible) {
	const button = $("#toggle-password");
	$("#login-form").password.type = visible ? "text" : "password";
	button.setAttribute("aria-pressed", String(visible));
	button.setAttribute("aria-label", visible ? "Hide password" : "Show password");
	button.title = visible ? "Hide password" : "Show password";
	$("use", button).setAttribute("href", visible ? "#i-eye-off" : "#i-eye");
}

$("#toggle-password").addEventListener("click", () => {
	const field = $("#login-form").password;
	showPassword(field.type === "password");
	field.focus();
});

$("#logout").addEventListener("click", async () => {
	await api("logout", {}).catch(() => {});
	state.accounts = [];
	state.transactions = [];
	showLogin();
});

// ================================================================ routing
// #dashboard  #accounts  #account/10001  #activity  #open  #deposit[/10001]  #withdraw[/10001]  #transfer[/10001]

const ROUTES = {
	dashboard: { view: "dashboard", nav: "dashboard", render: renderDashboard },
	accounts: { view: "accounts", nav: "accounts", render: renderAccounts },
	account: { view: "account", nav: "accounts", render: renderAccount },
	activity: { view: "activity", nav: "activity", render: renderActivity },
	open: { view: "open", nav: "open", render: showOpenForm },
	deposit: { view: "money", nav: "deposit", render: arg => showMoneyForm("deposit", arg) },
	withdraw: { view: "money", nav: "withdraw", render: arg => showMoneyForm("withdraw", arg) },
	transfer: { view: "transfer", nav: "transfer", render: showTransferForm },
};
let currentView = null;

function route() {
	const [name, arg] = decodeURIComponent(location.hash.slice(1)).split("/");
	const r = ROUTES[name] || ROUTES.dashboard;
	if (r.view !== currentView) {
		$$("[data-view]").forEach(s => { s.hidden = s.dataset.view !== r.view; });
		window.scrollTo(0, 0);
		currentView = r.view;
	}
	$$(".nav a").forEach(a => a.classList.toggle("active", a.dataset.route === r.nav));
	hideTooltip();
	closeJump();
	r.render(arg);
}

window.addEventListener("hashchange", () => {
	if (!$("#app-view").hidden) route();
});

// ================================================================ dashboard

function renderDashboard() {
	const { accounts, transactions } = state;
	const total = sum(accounts.map(a => a.balance));
	const today = new Date().setHours(0, 0, 0, 0);
	$("#hero-total").textContent = money.format(total);
	$("#hero-count").textContent = accounts.length;
	$("#hero-avg").textContent = money.format(accounts.length ? total / accounts.length : 0);
	$("#hero-today").textContent = transactions.filter(t => t.time >= today).length;
	$("#hero-legend").innerHTML = state.types.map(t =>
		`<span><i style="background:var(--${t.id.toLowerCase()})"></i>${esc(t.label.replace(" Account", ""))}</span>`).join("");

	$("#type-list").innerHTML = state.types.map(t => {
		const list = accounts.filter(a => a.type === t.id);
		const held = sum(list.map(a => a.balance));
		const pct = total ? (held / total) * 100 : 0;
		return `<div class="type-row type-${esc(t.id)}">
			<span class="label"><span class="dot"></span>${esc(t.label)}</span>
			<span class="amount">${money.format(held)}</span>
			<div class="bar"><span style="width:${pct}%"></span></div>
			<span class="sub">${list.length} account${list.length === 1 ? "" : "s"} · ${pct.toFixed(0)}% of holdings</span>
		</div>`;
	}).join("");

	const recent = transactions.slice(-6).reverse();
	$("#recent-feed").innerHTML = recent.length
		? recent.map(feedItem).join("")
		: `<li class="empty" style="display:block;border:0">No activity yet. <a href="#open">Open the first account</a>.</li>`;

	renderFlowChart($("#flow-chart"), transactions, { money, moneyShort, showTooltip, hideTooltip });
	if (towers) towers.update(accounts);
}

function feedItem(t) {
	return `<li>
		<span class="tx-icon ${t.credit ? "credit" : ""}">${icon(KIND_ICON[t.kind])}</span>
		<span class="what"><b>${esc(t.name)}</b><span>${esc(describe(t))} · <a href="#account/${esc(t.accNum)}">${esc(t.accNum)}</a></span></span>
		<span class="amt ${t.credit ? "credit-amt" : ""}">${signed(t)}<small>${dateTime.format(t.time)}</small></span>
	</li>`;
}

let resizeTimer;
window.addEventListener("resize", () => {
	clearTimeout(resizeTimer);
	resizeTimer = setTimeout(() => { if (currentView === "dashboard") renderDashboard(); }, 150);
});

// ================================================================ accounts list

const COLUMNS = [
	{ key: "accNum", label: "Account" },
	{ key: "name", label: "Holder" },
	{ key: "typeLabel", label: "Type" },
	{ key: "balance", label: "Balance", num: true },
	{ key: "details", label: "Details" },
];

function filteredAccounts() {
	const query = state.accSearch.trim().toLowerCase();
	const { key, dir } = state.sort;
	return state.accounts
		.filter(a => state.typeFilter === "ALL" || a.type === state.typeFilter)
		.filter(a => !query || [a.accNum, a.name, a.typeLabel, a.balance.toFixed(2), detailsText(a)].some(v => v.toLowerCase().includes(query)))
		.sort((a, b) => (typeof a[key] === "number" ? a[key] - b[key] : a[key].localeCompare(b[key], undefined, { numeric: true })) * dir);
}

function renderAccounts() {
	$("#type-filter").innerHTML = [{ id: "ALL", label: "All" }].concat(state.types.map(t => ({ id: t.id, label: t.label.replace(" Account", "") })))
		.map(o => `<button type="button" class="chip" data-type="${esc(o.id)}" aria-pressed="${o.id === state.typeFilter}">${esc(o.label)}</button>`)
		.join("");

	const rows = filteredAccounts();
	const { key, dir } = state.sort;
	const head = COLUMNS.map(c => {
		const sort = c.key === key ? (dir > 0 ? "ascending" : "descending") : "none";
		return `<th class="${c.num ? "num" : ""}" aria-sort="${sort}"><button type="button" data-sort="${c.key}">${c.label}</button></th>`;
	}).join("") + `<th><span hidden>Actions</span></th>`;

	const body = rows.length
		? rows.map(a => `<tr class="clickable" data-acc="${esc(a.accNum)}">
			<td><a class="acc-link" href="#account/${esc(a.accNum)}">${esc(a.accNum)}</a></td>
			<td class="strong">${esc(a.name)}</td>
			<td>${badge(a)}</td>
			<td class="num strong">${money.format(a.balance)}</td>
			<td class="details">${esc(detailsText(a))}</td>
			<td class="actions">
				<a class="btn btn-sm" href="#deposit/${esc(a.accNum)}">Deposit</a>
				<a class="btn btn-sm" href="#withdraw/${esc(a.accNum)}">Withdraw</a>
			</td>
		</tr>`).join("")
		: `<tr><td class="empty" colspan="${COLUMNS.length + 1}">${state.accounts.length ? "No accounts match your search." : `No accounts yet. <a href="#open">Open one</a>.`}</td></tr>`;

	$("#accounts-table").innerHTML = `<thead><tr>${head}</tr></thead><tbody>${body}</tbody>`;
	$("#accounts-foot").textContent = `Showing ${rows.length} of ${state.accounts.length} accounts · ${money.format(sum(rows.map(a => a.balance)))} in total`;
}

$("#acc-search").addEventListener("input", e => {
	state.accSearch = e.target.value;
	renderAccounts();
});

$("#type-filter").addEventListener("click", e => {
	const chip = e.target.closest("[data-type]");
	if (!chip) return;
	state.typeFilter = chip.dataset.type;
	renderAccounts();
});

$("#accounts-table").addEventListener("click", e => {
	const sortButton = e.target.closest("[data-sort]");
	if (sortButton) {
		const key = sortButton.dataset.sort;
		state.sort = { key, dir: state.sort.key === key ? -state.sort.dir : 1 };
		renderAccounts();
		return;
	}
	const row = e.target.closest("tr[data-acc]");
	if (row && !e.target.closest("a, button")) location.hash = "account/" + row.dataset.acc;
});

$("#export-csv").addEventListener("click", () => {
	// a leading = + - @ would make spreadsheets run the cell as a formula
	const cell = v => {
		const s = String(v);
		return `"${(/^[=+\-@]/.test(s) ? "'" + s : s).replace(/"/g, '""')}"`;
	};
	const lines = [["Account No", "Name", "Type", "Balance", "Details"]]
		.concat(filteredAccounts().map(a => [a.accNum, a.name, a.typeLabel, a.balance.toFixed(2), detailsText(a)]))
		.map(row => row.map(cell).join(","));
	const link = document.createElement("a");
	link.href = URL.createObjectURL(new Blob([lines.join("\r\n")], { type: "text/csv" }));
	link.download = "accounts.csv";
	link.click();
	setTimeout(() => URL.revokeObjectURL(link.href), 1000);
	toast("Exported " + (lines.length - 1) + " accounts");
});

// ================================================================ single account

function renderAccount(accNum) {
	const acc = findAccount(accNum);
	const history = state.transactions.filter(t => t.accNum === accNum).reverse();
	const body = $("#account-body");

	if (!acc) {
		body.innerHTML = `<div class="card empty">${history.length ? `Account ${esc(accNum)} has been closed.` : `There is no account ${esc(accNum)}.`}</div>`
			+ (history.length ? historyCard(history, false) : "");
		return;
	}

	const type = typeOf(acc.type);
	const opened = state.transactions.find(t => t.accNum === acc.accNum && t.kind === "OPEN");
	const facts = [
		["Minimum balance", money.format(acc.minBalance)],
		["Available to withdraw", money.format(Math.max(0, acc.balance - acc.minBalance))],
	];
	if (acc.withdrawLimit !== null) facts.push(["Max per withdrawal", money.format(acc.withdrawLimit)]);
	if (acc.type !== "SAVINGS") facts.push([type.extraLabel, esc(acc.extra)]);
	if (acc.netBalance !== null) facts.push(["Balance + 5% interest", money.format(acc.netBalance)]);
	if (opened) facts.push(["Opened on", longDate.format(opened.time)]);

	body.innerHTML = `
		<div class="account-top">
			<div class="card-stage" id="card-stage">
				<div class="bank-card type-${esc(acc.type)}" id="bank-card">
					<div class="bc-top"><span>Banking System</span><span>${esc(acc.typeLabel.replace(" Account", ""))}</span></div>
					<div class="chip-img"></div>
					<div class="bc-num">•••• •••• ${esc(acc.accNum)}</div>
					<div class="bc-bottom">
						<div><span>Account holder</span><b>${esc(acc.name)}</b></div>
						<div style="text-align:right"><span>Since</span><b>${opened ? monthYear.format(opened.time) : "—"}</b></div>
					</div>
				</div>
			</div>
			<div class="card summary">
				<span class="muted" style="margin:0">${esc(acc.name)} · ${badge(acc)}</span>
				<div class="balance">${money.format(acc.balance)}</div>
				<div class="facts">${facts.map(([k, v]) => `<div><span>${esc(k)}</span><b>${v}</b></div>`).join("")}</div>
				<div class="actions">
					<a class="btn btn-primary" href="#deposit/${esc(acc.accNum)}">${icon("in")}Deposit</a>
					<a class="btn" href="#withdraw/${esc(acc.accNum)}">${icon("out")}Withdraw</a>
					<a class="btn" href="#transfer/${esc(acc.accNum)}">${icon("transfer")}Transfer</a>
					<button class="btn btn-danger-ghost" id="close-account">${icon("close")}Close account</button>
				</div>
			</div>
		</div>
		${historyCard(history, false)}`;

	tiltCard($("#card-stage"), $("#bank-card"));
	$("#close-account").addEventListener("click", () => closeAccount(acc));
}

function historyCard(list, showAccount) {
	return `<div class="card card-flush">
		<div class="card-head"><div><h2>Transaction history</h2><p>${list.length} transaction${list.length === 1 ? "" : "s"}</p></div></div>
		<div class="table-wrap" style="margin-top:4px">${historyTable(list, showAccount)}</div>
	</div>`;
}

function historyTable(list, showAccount) {
	const cols = 4 + (showAccount ? 1 : 0);
	const rows = list.length
		? list.map(t => `<tr>
			<td>${dateTime.format(t.time)}</td>
			${showAccount ? `<td><a class="acc-link" href="#account/${esc(t.accNum)}">${esc(t.accNum)}</a><span class="sub-cell">${esc(t.name)}</span></td>` : ""}
			<td><span style="display:inline-flex;align-items:center;gap:10px"><span class="tx-icon ${t.credit ? "credit" : ""}">${icon(KIND_ICON[t.kind])}</span>${esc(describe(t))}</span></td>
			<td class="num strong ${t.credit ? "credit-amt" : ""}">${signed(t)}</td>
			<td class="num">${money.format(t.balanceAfter)}</td>
		</tr>`).join("")
		: `<tr><td class="empty" colspan="${cols}">Nothing here yet.</td></tr>`;
	return `<table><thead><tr><th>Date</th>${showAccount ? "<th>Account</th>" : ""}<th>Transaction</th><th class="num">Amount</th><th class="num">Balance after</th></tr></thead><tbody>${rows}</tbody></table>`;
}

/** Tilts the card towards the pointer and moves its shine. */
function tiltCard(stage, card) {
	if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;
	stage.addEventListener("pointermove", e => {
		const r = card.getBoundingClientRect();
		const x = (e.clientX - r.left) / r.width;
		const y = (e.clientY - r.top) / r.height;
		card.style.transform = `rotateY(${(x - 0.5) * 18}deg) rotateX(${(0.5 - y) * 14}deg)`;
		card.style.setProperty("--gx", x * 100 + "%");
		card.style.setProperty("--gy", y * 100 + "%");
	});
	stage.addEventListener("pointerleave", () => { card.style.transform = ""; });
}

async function closeAccount(acc) {
	const ok = await confirmDialog(
		`Close account ${acc.accNum}?`,
		`${acc.name}'s ${acc.typeLabel.toLowerCase()} will be closed and the remaining ${money.format(acc.balance)} paid out. This can't be undone.`,
		"Close account");
	if (!ok) return;
	try {
		await api("close", { account: acc.accNum });
		await refresh();
		toast(`Account ${acc.accNum} closed`);
		location.hash = "accounts";
	} catch (err) {
		handleError(err);
	}
}

// ================================================================ activity

function renderActivity() {
	$("#kind-filter").innerHTML = KIND_FILTERS.map(f =>
		`<button type="button" class="chip" data-kind="${f.id}" aria-pressed="${f.id === state.kindFilter}">${f.label}</button>`).join("");
	const kinds = KIND_FILTERS.find(f => f.id === state.kindFilter).kinds;
	const query = state.txSearch.trim().toLowerCase();
	const list = state.transactions
		.filter(t => !kinds || kinds.includes(t.kind))
		.filter(t => !query || t.name.toLowerCase().includes(query) || t.accNum.includes(query) || (t.otherAccNum || "").includes(query))
		.reverse();
	$("#activity-table").innerHTML = historyTable(list, true);
	const credits = sum(list.filter(t => t.credit).map(t => t.amount));
	const debits = sum(list.filter(t => !t.credit).map(t => t.amount));
	$("#activity-foot").textContent = `${list.length} transactions · ${money.format(credits)} in · ${money.format(debits)} out`;
}

$("#tx-search").addEventListener("input", e => {
	state.txSearch = e.target.value;
	renderActivity();
});

$("#kind-filter").addEventListener("click", e => {
	const chip = e.target.closest("[data-kind]");
	if (!chip) return;
	state.kindFilter = chip.dataset.kind;
	renderActivity();
});

// ================================================================ open account

const openForm = $("#open-form");

function buildOpenForm() {
	$("#type-options").innerHTML = state.types.map((t, i) => `
		<label class="type-option type-${esc(t.id)}">
			<input type="radio" name="type" value="${esc(t.id)}" ${i === 0 ? "checked" : ""}>
			<span><span class="dot"></span>${esc(t.label)}<small>Min. ${money.format(t.minBalance)}</small></span>
		</label>`).join("");
	$("#type-rules").innerHTML = state.types.map(t =>
		`<li><b>${esc(t.label)}</b>: keep at least ${money.format(t.minBalance)}; needs ${esc(t.extraLabel.toLowerCase())}.</li>`).join("")
		+ `<li>Savings and Student accounts allow a fixed maximum per withdrawal and show their balance with 5% interest.</li>`;
}

/** Updates the hints and the last field for the chosen account type. */
function updateOpenForm() {
	const type = typeOf(openForm.type.value);
	if (!type) return;
	$("#balance-hint").textContent = `At least ${money.format(type.minBalance)} for a ${type.label.toLowerCase()}.`;
	openForm.balance.min = type.minBalance;
	$("#extra-label").textContent = type.extraLabel;
	const numeric = type.id === "SAVINGS"; // the savings field is the withdraw limit
	openForm.extra.type = numeric ? "number" : "text";
	openForm.extra.step = numeric ? "0.01" : "";
	openForm.extra.min = numeric ? "0.01" : "";
	openForm.extra.placeholder = numeric ? "e.g. 10000" : type.id === "CURRENT" ? "e.g. TL-2024-0193" : "e.g. Delhi University";
}

function showOpenForm() {
	openForm.reset();
	$(".form-error", openForm).textContent = "";
	updateOpenForm();
}

openForm.addEventListener("change", e => {
	if (e.target.name === "type") updateOpenForm();
});
openForm.addEventListener("reset", () => setTimeout(updateOpenForm));

openForm.addEventListener("submit", e => {
	e.preventDefault();
	submitting(openForm, async () => {
		const { account } = await api("accounts", {
			type: openForm.type.value,
			name: openForm.elements.name.value,
			balance: openForm.balance.value,
			extra: openForm.extra.value,
		});
		await refresh();
		toast(`Account ${account.accNum} opened for ${account.name}`);
		location.hash = "account/" + account.accNum;
	});
});

// ================================================================ account previews (deposit, withdraw, transfer)

/** Shows whose account a typed number belongs to, so mistakes are caught before submitting. */
function updatePreview(box, accNum, outgoing) {
	const n = accNum.trim();
	const acc = findAccount(n);
	box.classList.toggle("not-found", Boolean(n && !acc));
	if (!n) {
		box.innerHTML = "";
	} else if (!acc) {
		box.textContent = `No account with number ${n}.`;
	} else {
		const available = Math.max(0, acc.balance - acc.minBalance);
		const right = outgoing
			? `${money.format(available)}<small>available${acc.withdrawLimit !== null ? ` · max ${money.format(acc.withdrawLimit)} each` : ""}</small>`
			: `${money.format(acc.balance)}<small>balance</small>`;
		box.innerHTML = `<span class="initials type-${esc(acc.type)}">${esc(initials(acc.name))}</span>
			<span class="who"><b>${esc(acc.name)}</b><span>${esc(acc.typeLabel)} · ${esc(acc.accNum)}</span></span>
			<span class="bal">${right}</span>`;
	}
}

// ================================================================ deposit / withdraw

const moneyForm = $("#money-form");
let moneyMode = "deposit";

function showMoneyForm(mode, accNum) {
	moneyMode = mode;
	const deposit = mode === "deposit";
	$("#money-title").textContent = deposit ? "Deposit" : "Withdraw";
	$("#money-subtitle").textContent = deposit ? "Add money to an account." : "Take money out of an account.";
	$("#money-submit").textContent = deposit ? "Deposit" : "Withdraw";
	$$(".segmented a").forEach(a => a.classList.toggle("active", a.dataset.mode === mode));
	$("#money-note").innerHTML = deposit
		? `<h3>Deposits</h3><ul><li>Any amount above zero.</li><li>Shows up straight away in the account's history.</li></ul>`
		: `<h3>Withdrawals</h3><ul><li>The account's minimum balance must stay in it.</li><li>Savings and Student accounts have a maximum per withdrawal.</li><li>The box under the account number shows how much can be taken out.</li></ul>`;
	const keep = accNum || moneyForm.account.value;
	moneyForm.reset();
	$(".form-error", moneyForm).textContent = "";
	moneyForm.account.value = keep;
	refreshMoneyPreview();
	(keep ? moneyForm.amount : moneyForm.account).focus();
}

const refreshMoneyPreview = () => updatePreview($("[data-preview=account]"), moneyForm.account.value, moneyMode === "withdraw");
moneyForm.account.addEventListener("input", refreshMoneyPreview);
moneyForm.addEventListener("reset", () => setTimeout(refreshMoneyPreview));

moneyForm.addEventListener("submit", e => {
	e.preventDefault();
	submitting(moneyForm, async () => {
		const amount = Number(moneyForm.amount.value);
		const { account } = await api(moneyMode, { account: moneyForm.account.value.trim(), amount: moneyForm.amount.value });
		await refresh();
		toast(`${moneyMode === "deposit" ? "Deposited" : "Withdrew"} ${money.format(amount)}. New balance ${money.format(account.balance)}`);
		moneyForm.amount.value = "";
		refreshMoneyPreview();
	});
});

// ================================================================ transfer

const transferForm = $("#transfer-form");

function showTransferForm(from) {
	transferForm.reset();
	$(".form-error", transferForm).textContent = "";
	transferForm.from.value = from || "";
	refreshTransferPreview();
	(from ? transferForm.to : transferForm.from).focus();
}

function refreshTransferPreview() {
	updatePreview($("[data-preview=from]"), transferForm.from.value, true);
	updatePreview($("[data-preview=to]"), transferForm.to.value, false);
}
transferForm.addEventListener("input", refreshTransferPreview);
transferForm.addEventListener("reset", () => setTimeout(refreshTransferPreview));

transferForm.addEventListener("submit", e => {
	e.preventDefault();
	submitting(transferForm, async () => {
		const from = transferForm.from.value.trim();
		const to = transferForm.to.value.trim();
		const amount = Number(transferForm.amount.value);
		await api("transfer", { from, to, amount: transferForm.amount.value });
		await refresh();
		toast(`Sent ${money.format(amount)} from ${from} to ${to}`);
		transferForm.amount.value = "";
		refreshTransferPreview();
	});
});

// ================================================================ jump to account (top bar)

const jump = $("#jump");
const jumpList = $("#jump-results");
let jumpIndex = 0;

function jumpMatches() {
	const q = jump.value.trim().toLowerCase();
	if (!q) return [];
	return state.accounts.filter(a => a.accNum.includes(q) || a.name.toLowerCase().includes(q)).slice(0, 6);
}

function renderJump() {
	const matches = jumpMatches();
	jumpIndex = Math.min(jumpIndex, Math.max(0, matches.length - 1));
	jump.setAttribute("aria-expanded", String(Boolean(jump.value.trim())));
	if (!jump.value.trim()) {
		jumpList.innerHTML = "";
		return;
	}
	jumpList.innerHTML = matches.length
		? matches.map((a, i) => `<li role="option" data-acc="${esc(a.accNum)}" aria-selected="${i === jumpIndex}">
			<span class="badge type-${esc(a.type)}"></span><b>${esc(a.name)}</b><span class="mono">${esc(a.accNum)}</span>
			<span class="bal">${money.format(a.balance)}</span></li>`).join("")
		: `<li class="none">No matching account</li>`;
}

function closeJump() {
	jump.value = "";
	renderJump();
}

function goToAccount(accNum) {
	closeJump();
	jump.blur();
	location.hash = "account/" + accNum;
}

jump.addEventListener("input", () => { jumpIndex = 0; renderJump(); });
jump.addEventListener("keydown", e => {
	const matches = jumpMatches();
	if (e.key === "ArrowDown" || e.key === "ArrowUp") {
		e.preventDefault();
		jumpIndex = (jumpIndex + (e.key === "ArrowDown" ? 1 : -1) + matches.length) % Math.max(1, matches.length);
		renderJump();
	} else if (e.key === "Enter" && matches[jumpIndex]) {
		goToAccount(matches[jumpIndex].accNum);
	} else if (e.key === "Escape") {
		closeJump();
		jump.blur();
	}
});
jump.addEventListener("blur", () => setTimeout(() => { jumpList.innerHTML = ""; }, 150));
jump.addEventListener("focus", renderJump);
jumpList.addEventListener("mousedown", e => {
	const item = e.target.closest("[data-acc]");
	if (item) {
		e.preventDefault();
		goToAccount(item.dataset.acc);
	}
});
document.addEventListener("keydown", e => {
	if (e.key === "/" && !$("#app-view").hidden && !e.target.closest("input, textarea")) {
		e.preventDefault();
		jump.focus();
	}
});

// ================================================================ theme

function isDark() {
	const set = document.documentElement.dataset.theme;
	return set ? set === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
}

function updateThemeIcon() {
	$("#theme-toggle use").setAttribute("href", isDark() ? "#i-sun" : "#i-moon");
}

matchMedia("(prefers-color-scheme: dark)").addEventListener("change", updateThemeIcon);

$("#theme-toggle").addEventListener("click", () => {
	const theme = isDark() ? "light" : "dark";
	document.documentElement.dataset.theme = theme;
	try { localStorage.setItem("theme", theme); } catch (e) { /* private mode */ }
	updateThemeIcon();
});

// ================================================================ tooltip, toast, dialog

function showTooltip(x, y, title, rows, note) {
	const tip = $("#tooltip");
	tip.replaceChildren();
	const head = document.createElement("div");
	head.className = "tooltip-title";
	head.textContent = note ? `${title} · ${note}` : title;
	tip.append(head);
	for (const r of rows) {
		const row = document.createElement("div");
		row.className = "tooltip-row";
		const key = document.createElement("i");
		key.style.background = r.color;
		const value = document.createElement("b");
		value.textContent = r.value;
		const label = document.createElement("span");
		label.textContent = r.label;
		row.append(key, value, label);
		tip.append(row);
	}
	tip.hidden = false;
	const { width, height } = tip.getBoundingClientRect();
	let left = x + 14;
	let top = y - height - 12;
	if (left + width > innerWidth - 8) left = x - width - 14;
	if (top < 8) top = y + 18;
	tip.style.left = Math.max(8, left) + "px";
	tip.style.top = top + "px";
}

function hideTooltip() {
	$("#tooltip").hidden = true;
}

function toast(message) {
	const el = $("#toast");
	$("span", el).textContent = message;
	el.classList.add("show");
	clearTimeout(toast.timer);
	toast.timer = setTimeout(() => el.classList.remove("show"), 3500);
}

/** Resolves true if the user confirms. */
function confirmDialog(title, text, okLabel) {
	const modal = $("#modal");
	$("#modal-title").textContent = title;
	$("#modal-text").textContent = text;
	$("#modal-ok").textContent = okLabel;
	modal.hidden = false;
	$("#modal-cancel").focus();
	return new Promise(resolve => {
		function done(result) {
			modal.hidden = true;
			modal.removeEventListener("click", onClick);
			document.removeEventListener("keydown", onKey);
			resolve(result);
		}
		function onClick(e) {
			if (e.target.id === "modal-ok") done(true);
			else if (e.target.id === "modal-cancel" || e.target === modal) done(false);
		}
		function onKey(e) {
			if (e.key === "Escape") done(false);
		}
		modal.addEventListener("click", onClick);
		document.addEventListener("keydown", onKey);
	});
}

// ================================================================ start

(async function init() {
	updateThemeIcon();
	try {
		state.types = (await api("types")).types;
		buildOpenForm();
		await showApp();
	} catch (err) {
		if (err instanceof Unauthorized) {
			showLogin();
		} else {
			// usually the page was opened from a file or another server (e.g. VS Code Live Server) that has no /api
			document.body.textContent = "Could not reach the Java server (" + err.message + "). "
				+ "Start it with \"java -cp bin WebApplication\" from the project folder and open http://localhost:8080 "
				+ "(not Live Server or the index.html file).";
		}
	}
})();
