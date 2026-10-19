// Already logged in? Go straight to the right dashboard.
if (Session.token) location.href = Session.role === "ADMIN" ? "admin.html" : "student.html";

let portal = "STUDENT";
const $ = id => document.getElementById(id);
const alertBox = $("alertBox");

function showError(msg) { alertBox.textContent = msg; alertBox.classList.remove("d-none"); }
function clearError() { alertBox.classList.add("d-none"); }

function setMode(register) {
  $("loginForm").classList.toggle("d-none", register);
  $("registerForm").classList.toggle("d-none", !register);
  $("switchText").textContent = register ? "Already registered?" : "New student?";
  $("switchLink").textContent = register ? "Log in" : "Register here";
  clearError();
}

document.querySelectorAll("#portalTabs .nav-link").forEach(btn => {
  btn.onclick = () => {
    document.querySelectorAll("#portalTabs .nav-link").forEach(b => b.classList.remove("active"));
    btn.classList.add("active");
    portal = btn.dataset.portal;
    // Registration exists for students only
    $("switchRow").classList.toggle("d-none", portal === "ADMIN");
    setMode(false);
  };
});

$("switchLink").onclick = e => {
  e.preventDefault();
  setMode($("registerForm").classList.contains("d-none"));
};

function go(session) {
  Session.save(session);
  location.href = session.role === "ADMIN" ? "admin.html" : "student.html";
}

$("loginForm").onsubmit = async e => {
  e.preventDefault(); clearError();
  const btn = $("loginBtn"); btn.disabled = true;
  try {
    go(await api("/auth/login", { method: "POST", body: { email: $("email").value, password: $("password").value, portal } }));
  } catch (err) { showError(err.message); }
  finally { btn.disabled = false; }
};

$("registerForm").onsubmit = async e => {
  e.preventDefault(); clearError();
  const btn = $("registerBtn"); btn.disabled = true;
  try {
    go(await api("/auth/register", { method: "POST", body: {
      name: $("rName").value, email: $("rEmail").value, password: $("rPassword").value,
      college: $("rCollege").value, branch: $("rBranch").value
    } }));
  } catch (err) { showError(err.message); }
  finally { btn.disabled = false; }
};
