// Shared helpers: API calls, session handling, toasts, safe HTML escaping.
const Session = {
  get token() { return sessionStorage.getItem("teximo_token"); },
  get role() { return sessionStorage.getItem("teximo_role"); },
  get name() { return sessionStorage.getItem("teximo_name"); },
  save(s) {
    sessionStorage.setItem("teximo_token", s.token);
    sessionStorage.setItem("teximo_role", s.role);
    sessionStorage.setItem("teximo_name", s.name);
  },
  clear() { sessionStorage.clear(); },
  /** Send the visitor back to the login page unless they hold the right role. */
  require(role) {
    if (!this.token || this.role !== role) { this.clear(); location.href = "index.html"; }
  },
  logout() { this.clear(); location.href = "index.html"; }
};

async function api(path, { method = "GET", body, form } = {}) {
  const headers = {};
  if (Session.token) headers["Authorization"] = "Bearer " + Session.token;
  let payload;
  if (form) payload = form;
  else if (body !== undefined) { headers["Content-Type"] = "application/json"; payload = JSON.stringify(body); }

  let res;
  try {
    res = await fetch(window.TEXIMO_API + path, { method, headers, body: payload });
  } catch (e) {
    throw new Error("Cannot reach the server. Is the backend running?");
  }
  let data = null;
  try { data = await res.json(); } catch (_) {}
  if (res.status === 401 && Session.token) { Session.logout(); throw new Error("Session expired."); }
  if (!res.ok) throw new Error((data && data.error) || "Request failed (" + res.status + ").");
  return data;
}

// Escape everything that comes from the server or the user before putting it in innerHTML.
function esc(v) {
  return String(v ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function toast(message, type = "success") {
  let box = document.getElementById("toastBox");
  if (!box) {
    box = document.createElement("div");
    box.id = "toastBox";
    box.className = "toast-container position-fixed top-0 end-0 p-3";
    box.style.zIndex = 2000;
    document.body.appendChild(box);
  }
  const el = document.createElement("div");
  el.className = "toast align-items-center text-bg-" + (type === "error" ? "danger" : type) + " border-0";
  el.innerHTML = '<div class="d-flex"><div class="toast-body">' + esc(message) +
    '</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>';
  box.appendChild(el);
  const t = new bootstrap.Toast(el, { delay: 4000 });
  t.show();
  el.addEventListener("hidden.bs.toast", () => el.remove());
}

const fmtDate = d => d ? new Date(d).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "—";
const fmtDateTime = d => d ? new Date(d).toLocaleString(undefined, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—";

function badge(status) {
  const map = {
    APPLIED: "secondary", SHORTLISTED: "info", INTERVIEW: "primary", SELECTED: "success", REJECTED: "danger",
    OPEN: "warning", IN_REVIEW: "info", RESOLVED: "success",
    NOT_PLACED: "secondary", IN_PROCESS: "info", PLACED: "success"
  };
  return '<span class="badge text-bg-' + (map[status] || "secondary") + '">' + esc(String(status).replace("_", " ")) + "</span>";
}

/** Run an async action and show errors as toasts. */
async function guard(fn) {
  try { return await fn(); } catch (e) { toast(e.message, "error"); }
}
