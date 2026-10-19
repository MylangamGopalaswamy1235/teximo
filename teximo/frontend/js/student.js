Session.require("STUDENT");

const view = document.getElementById("view");
document.getElementById("who").textContent = Session.name || "Student";
document.getElementById("logout").onclick = e => { e.preventDefault(); Session.logout(); };

const views = { dashboard, profile, jobs, drives, applications, resume, notifications, complaints };

document.getElementById("menu").onclick = e => {
  const a = e.target.closest("[data-view]");
  if (a) show(a.dataset.view);
};

function show(name) {
  document.querySelectorAll("#menu .nav-link").forEach(l => l.classList.toggle("active", l.dataset.view === name));
  view.innerHTML = '<div class="text-center text-muted py-5">Loading…</div>';
  guard(() => views[name]()).then(() => window.scrollTo(0, 0));
}

async function refreshNotifBadge() {
  try {
    const list = await api("/student/notifications");
    const unread = list.filter(n => !n.read).length;
    const b = document.getElementById("notifCount");
    b.textContent = unread; b.classList.toggle("d-none", unread === 0);
  } catch (_) {}
}

const chips = csv => (csv || "").split(/[,;]/).map(s => s.trim()).filter(Boolean)
  .map(s => '<span class="skill-chip">' + esc(s) + "</span>").join("") || '<span class="text-muted">—</span>';

// ============ Dashboard ============
async function dashboard() {
  const [p, apps, drv, ints, notes] = await Promise.all([
    api("/student/profile"), api("/student/applications"), api("/student/drives"),
    api("/student/interviews"), api("/student/notifications")]);
  const eligible = drv.filter(d => d.eligible && !d.applied);
  const upcoming = ints.filter(i => new Date(i.scheduledAt) > new Date());
  const complete = ["phone", "college", "branch", "cgpa", "graduationYear", "skills"].filter(k => p[k]).length;
  view.innerHTML = `
    <h3 class="page-title">Welcome, ${esc(p.name)} 👋</h3>
    <div class="row g-3 mb-4">
      <div class="col-6 col-lg-3"><div class="tx-card stat-card"><div class="num">${apps.length}</div><div class="lbl">Applications</div></div></div>
      <div class="col-6 col-lg-3"><div class="tx-card stat-card"><div class="num">${eligible.length}</div><div class="lbl">Open drives you're eligible for</div></div></div>
      <div class="col-6 col-lg-3"><div class="tx-card stat-card"><div class="num">${upcoming.length}</div><div class="lbl">Upcoming interviews</div></div></div>
      <div class="col-6 col-lg-3"><div class="tx-card stat-card"><div class="num" style="font-size:1.2rem">${badge(p.placementStatus)}</div><div class="lbl mt-2">Placement status</div></div></div>
    </div>
    ${complete < 6 ? `<div class="alert alert-warning">Your profile is ${Math.round(complete / 6 * 100)}% complete. Fill it in so eligibility and job matches work properly.
      <a href="#" onclick="show('profile');return false">Update profile</a></div>` : ""}
    <div class="row g-3">
      <div class="col-lg-6"><div class="tx-card p-3 h-100"><h6 class="fw-bold">Upcoming interviews</h6>
        ${upcoming.length ? upcoming.map(i => `<div class="border-bottom py-2"><div class="fw-semibold">${esc(i.driveTitle)}</div>
          <small class="text-muted">${fmtDateTime(i.scheduledAt)} · ${esc(i.round)} · ${esc(i.mode)}${i.venueOrLink ? " · " + esc(i.venueOrLink) : ""}</small></div>`).join("")
          : '<div class="text-muted">No interviews scheduled yet.</div>'}</div></div>
      <div class="col-lg-6"><div class="tx-card p-3 h-100"><h6 class="fw-bold">Latest notifications</h6>
        ${notes.slice(0, 4).map(n => `<div class="border-bottom py-2"><div class="fw-semibold">${esc(n.title)}</div><small class="text-muted">${esc(n.message)}</small></div>`).join("")
          || '<div class="text-muted">Nothing new.</div>'}</div></div>
    </div>`;
  refreshNotifBadge();
}

// ============ Profile ============
async function profile() {
  const p = await api("/student/profile");
  view.innerHTML = `
    <h3 class="page-title">My Profile</h3>
    <form id="pf" class="tx-card p-4">
      <div class="row g-3">
        <div class="col-md-6"><label class="form-label">Full name</label><input class="form-control" name="name" value="${esc(p.name)}" required></div>
        <div class="col-md-6"><label class="form-label">Email (login)</label><input class="form-control" value="${esc(p.email)}" disabled></div>
        <div class="col-md-6"><label class="form-label">Phone</label><input class="form-control" name="phone" value="${esc(p.phone)}"></div>
        <div class="col-md-6"><label class="form-label">LinkedIn / portfolio URL</label><input class="form-control" name="linkedinUrl" value="${esc(p.linkedinUrl)}"></div>
        <div class="col-md-6"><label class="form-label">College</label><input class="form-control" name="college" value="${esc(p.college)}"></div>
        <div class="col-md-3"><label class="form-label">Degree</label><input class="form-control" name="degree" value="${esc(p.degree)}" placeholder="B.Tech"></div>
        <div class="col-md-3"><label class="form-label">Branch</label><input class="form-control" name="branch" value="${esc(p.branch)}" placeholder="CSE"></div>
        <div class="col-md-4"><label class="form-label">Graduation year</label><input class="form-control" type="number" name="graduationYear" value="${esc(p.graduationYear)}"></div>
        <div class="col-md-4"><label class="form-label">CGPA (0–10)</label><input class="form-control" type="number" step="0.01" min="0" max="10" name="cgpa" value="${esc(p.cgpa)}"></div>
        <div class="col-md-4"><label class="form-label">Active backlogs</label><input class="form-control" type="number" min="0" name="backlogs" value="${esc(p.backlogs ?? 0)}"></div>
        <div class="col-12"><label class="form-label">Skills <small class="text-muted">(comma separated: Java, SQL, React)</small></label>
          <input class="form-control" name="skills" value="${esc(p.skills)}"><div class="mt-2">${chips(p.skills)}</div></div>
        <div class="col-12"><label class="form-label">Education details</label>
          <textarea class="form-control" rows="3" name="education" placeholder="10th, 12th, diploma, projects, certifications…">${esc(p.education)}</textarea></div>
      </div>
      <button class="btn btn-amber mt-3 px-4">Save profile</button>
    </form>`;
  document.getElementById("pf").onsubmit = e => {
    e.preventDefault();
    guard(async () => {
      await api("/student/profile", { method: "PUT", body: Object.fromEntries(new FormData(e.target)) });
      toast("Profile saved.");
      profile();
    });
  };
}

// ============ Jobs & internships (RapidAPI JSearch via backend) ============
const ringColor = s => s >= 70 ? "#d6f2dc;color:#1d6b34" : s >= 40 ? "#fff0cc;color:#8a5a00" : "#fde9e7;color:#a02c20";

function jobCard(j) {
  return `<div class="tx-card p-3 mb-3"><div class="d-flex gap-3">
    ${j.logo ? `<img class="job-logo" src="${esc(j.logo)}" alt="" onerror="this.style.display='none'">` : ""}
    <div class="flex-grow-1">
      <div class="fw-bold">${esc(j.title)}</div>
      <div class="text-muted small">${esc(j.company)} · ${esc(j.location || "Location not listed")}${j.remote ? " · Remote" : ""}${j.type ? " · " + esc(j.type) : ""}</div>
      <div class="small mt-2 clamp-3">${esc(j.description)}</div>
      ${j.missingSkills && j.missingSkills.length ? `<div class="mt-2 small">Skill gaps: ${j.missingSkills.map(s => `<span class="skill-chip miss">${esc(s)}</span>`).join("")}</div>` : ""}
      <div class="small text-muted mt-1">💡 ${esc(j.suggestion || "")}</div>
      <div class="mt-2 d-flex flex-wrap gap-2 align-items-center">
        ${/^https?:\/\//i.test(j.applyLink) ? `<a class="btn btn-sm btn-outline-dark" target="_blank" rel="noopener noreferrer" href="${esc(j.applyLink)}">View original posting ↗</a>` : ""}
        <small class="text-muted">Source: ${esc(j.source || "—")}${j.postedAt ? " · " + fmtDate(j.postedAt) : ""}</small>
      </div>
      <div class="small text-muted mt-1">You apply on the original site yourself. Teximo never applies or uploads anything for you.</div>
    </div>
    <div class="match-ring" style="background:${ringColor(j.matchScore)}" title="Skill match">${j.matchScore}%</div>
  </div></div>`;
}

async function jobs() {
  view.innerHTML = `
    <h3 class="page-title">Jobs &amp; Internships</h3>
    <form id="jf" class="tx-card p-3 mb-3"><div class="row g-2">
      <div class="col-md-4"><input class="form-control" name="q" placeholder="Role or keyword (e.g. Java developer)"></div>
      <div class="col-md-3"><input class="form-control" name="location" placeholder="Location"></div>
      <div class="col-md-2"><select class="form-select" name="type"><option value="">All types</option><option value="INTERNSHIP">Internship</option><option value="FULLTIME">Full-time</option></select></div>
      <div class="col-md-3 d-flex gap-2"><button class="btn btn-amber flex-fill">Search</button>
        <button class="btn btn-outline-dark flex-fill" type="button" id="recBtn">✨ For me</button></div>
    </div></form>
    <div id="jobResults"><div class="text-muted">Search for roles, or tap <b>✨ For me</b> to get recommendations based on your skills and resume.</div></div>`;
  const out = document.getElementById("jobResults");
  const run = async path => {
    out.innerHTML = '<div class="text-muted">Searching…</div>';
    await guard(async () => {
      const list = await api(path);
      out.innerHTML = list.length ? list.map(jobCard).join("") : '<div class="text-muted">No results. Try different keywords.</div>';
    });
    if (out.textContent.trim() === "Searching…") out.innerHTML = "";
  };
  document.getElementById("jf").onsubmit = e => {
    e.preventDefault();
    run("/jobs/search?" + new URLSearchParams(new FormData(e.target)));
  };
  document.getElementById("recBtn").onclick = () => run("/jobs/recommend?type=" + encodeURIComponent(document.querySelector("#jf [name=type]").value));
}

// ============ Placement drives ============
async function drives() {
  const list = await api("/student/drives");
  list.sort((a, b) => (b.eligible - a.eligible) || ((a.applied) - (b.applied)));
  view.innerHTML = `<h3 class="page-title">Placement Drives</h3>` + (list.map(x => {
    const d = x.drive;
    return `<div class="tx-card p-3 mb-3"><div class="d-flex flex-wrap justify-content-between gap-2">
      <div><div class="fw-bold">${esc(d.companyName)} — ${esc(d.title)} <span class="badge text-bg-light border">${esc(d.type)}</span></div>
        <div class="small text-muted">${d.ctc ? "CTC/Stipend: " + esc(d.ctc) + " · " : ""}Drive: ${fmtDate(d.driveDate)} · Apply by: ${fmtDate(d.lastDateToApply)}</div></div>
      <div>${x.applied ? '<span class="badge text-bg-success">Applied</span>'
        : x.eligible ? `<button class="btn btn-amber btn-sm" data-apply="${d.id}">Apply</button>`
        : '<span class="badge text-bg-secondary">Not eligible</span>'}</div></div>
      <div class="small mt-2">${esc(d.description || "")}</div>
      <div class="small mt-2 text-muted">Criteria: min CGPA ${esc(d.minCgpa ?? 0)} · max backlogs ${esc(d.maxBacklogs ?? 0)}${d.allowedBranches ? " · branches: " + esc(d.allowedBranches) : ""}${d.graduationYear ? " · batch " + esc(d.graduationYear) : ""}</div>
      ${!x.eligible ? `<div class="small mt-1 text-danger">Why: ${x.reasons.map(esc).join("; ")}</div>` : ""}
      ${x.missingSkills.length ? `<div class="small mt-1">Skills to build: ${x.missingSkills.map(s => `<span class="skill-chip miss">${esc(s)}</span>`).join("")}</div>` : ""}
    </div>`;
  }).join("") || '<div class="text-muted">No drives have been created yet.</div>');
  view.onclick = e => {
    const b = e.target.closest("[data-apply]");
    if (!b) return;
    b.disabled = true;
    guard(async () => { await api("/student/drives/" + b.dataset.apply + "/apply", { method: "POST" }); toast("Application submitted."); drives(); })
      .finally(() => { b.disabled = false; });
  };
}

// ============ Applications & interviews ============
async function applications() {
  const [apps, ints] = await Promise.all([api("/student/applications"), api("/student/interviews")]);
  view.innerHTML = `
    <h3 class="page-title">My Applications</h3>
    <div class="tx-card p-0 mb-4 table-responsive"><table class="table mb-0">
      <thead><tr><th>Company</th><th>Drive</th><th>Applied</th><th>Status</th></tr></thead>
      <tbody>${apps.map(a => `<tr><td>${esc(a.companyName)}</td><td>${esc(a.driveTitle)}</td><td>${fmtDate(a.appliedAt)}</td><td>${badge(a.status)}</td></tr>`).join("")
        || '<tr><td colspan="4" class="text-muted text-center py-4">No applications yet.</td></tr>'}</tbody></table></div>
    <h5 class="fw-bold">Interview schedule</h5>
    <div class="tx-card p-0 table-responsive"><table class="table mb-0">
      <thead><tr><th>Drive</th><th>When</th><th>Round</th><th>Mode</th><th>Venue / link</th></tr></thead>
      <tbody>${ints.map(i => `<tr><td>${esc(i.driveTitle)}</td><td>${fmtDateTime(i.scheduledAt)}</td><td>${esc(i.round)}</td><td>${esc(i.mode)}</td><td>${esc(i.venueOrLink || "—")}</td></tr>`).join("")
        || '<tr><td colspan="5" class="text-muted text-center py-4">No interviews scheduled.</td></tr>'}</tbody></table></div>`;
}

// ============ Resume studio ============
async function resume() {
  view.innerHTML = `
    <h3 class="page-title">Resume Studio</h3>
    <div class="row g-3">
      <div class="col-lg-6"><div class="tx-card p-3 h-100">
        <h6 class="fw-bold">Analyze my resume (PDF)</h6>
        <p class="small text-muted">Upload a PDF (up to 500 MB). You'll get an ATS-style check, missing skills and AI feedback. Your file is read for analysis only; it is not sent to any job site.</p>
        <form id="rf">
          <input class="form-control mb-2" type="file" name="file" accept="application/pdf,.pdf" required>
          <input class="form-control mb-2" name="targetRole" placeholder="Target role (e.g. Java Developer, Data Analyst)">
          <button class="btn btn-amber" id="rBtn">Analyze</button>
        </form></div></div>
      <div class="col-lg-6"><div class="tx-card p-3 h-100">
        <h6 class="fw-bold">Build / improve with AI</h6>
        <p class="small text-muted">Creates a resume draft from your profile. Review it, fill any [placeholders], and copy it.</p>
        <input class="form-control mb-2" id="bRole" placeholder="Target role">
        <textarea class="form-control mb-2" id="bExtra" rows="3" placeholder="Projects, internships, achievements, certifications…"></textarea>
        <button class="btn btn-outline-dark" id="bBtn">✨ Generate resume</button>
      </div></div>
    </div>
    <div id="rOut" class="mt-4"></div>`;
  const out = document.getElementById("rOut");

  document.getElementById("rf").onsubmit = async e => {
    e.preventDefault();
    const btn = document.getElementById("rBtn"); btn.disabled = true; btn.textContent = "Analyzing…";
    out.innerHTML = "";
    await guard(async () => { renderReport(out, await api("/resume/analyze", { method: "POST", form: new FormData(e.target) })); });
    btn.disabled = false; btn.textContent = "Analyze";
  };
  document.getElementById("bBtn").onclick = async e => {
    const btn = e.target; btn.disabled = true; btn.textContent = "Writing…";
    await guard(async () => {
      const r = await api("/resume/build", { method: "POST", body: { targetRole: document.getElementById("bRole").value, extra: document.getElementById("bExtra").value } });
      out.innerHTML = `<div class="tx-card p-3"><div class="d-flex justify-content-between mb-2"><h6 class="fw-bold mb-0">Your resume draft</h6>
        <div class="d-flex gap-2"><button class="btn btn-sm btn-outline-dark" id="cpy">Copy</button>
        <button class="btn btn-sm btn-outline-dark" id="downloadResumePdf">Download PDF</button></div></div><pre class="resume-out" id="rTxt"></pre></div>`;
      document.getElementById("rTxt").textContent = r.resume;
      document.getElementById("cpy").onclick = () => navigator.clipboard.writeText(r.resume).then(() => toast("Copied."));
      document.getElementById("downloadResumePdf").onclick = async e => {
        const button = e.currentTarget;
        button.disabled = true;
        try {
          const response = await fetch(window.TEXIMO_API + "/resume/pdf", {
            method: "POST",
            headers: { "Authorization": "Bearer " + Session.token, "Content-Type": "application/json" },
            body: JSON.stringify({ resume: r.resume })
          });
          if (!response.ok) {
            let data = {};
            try { data = await response.json(); } catch (_) {}
            throw new Error(data.error || "Could not create the resume PDF.");
          }
          const url = URL.createObjectURL(await response.blob());
          const link = document.createElement("a");
          link.href = url;
          link.download = "teximo-resume.pdf";
          link.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (error) {
          toast(error.message || "Could not download the resume PDF.", "error");
        } finally {
          button.disabled = false;
        }
      };
    });
    btn.disabled = false; btn.textContent = "✨ Generate resume";
  };
}

function renderReport(out, r) {
  const col = r.atsScore >= 75 ? "success" : r.atsScore >= 55 ? "warning" : "danger";
  out.innerHTML = `<div class="tx-card p-3"><div class="d-flex align-items-center gap-3 mb-3">
      <div class="match-ring text-bg-${col}" style="width:70px;height:70px;font-size:1.2rem">${r.atsScore}</div>
      <div><h5 class="fw-bold mb-0">ATS-style score</h5><small class="text-muted">Structure, keywords and completeness checks</small></div></div>
    <div class="row g-3"><div class="col-md-6"><h6 class="text-success">✔ Looks good</h6><ul class="small">${r.strengths.map(s => `<li>${esc(s)}</li>`).join("") || "<li>—</li>"}</ul></div>
      <div class="col-md-6"><h6 class="text-danger">✘ Improve</h6><ul class="small">${r.improvements.map(s => `<li>${esc(s)}</li>`).join("") || "<li>Nothing major. Nice!</li>"}</ul></div></div>
    <h6>Skills found</h6><div class="mb-2">${(r.skillsFound || []).map(s => `<span class="skill-chip ok">${esc(s)}</span>`).join("") || "—"}</div>
    ${r.missingForTargetRole && r.missingForTargetRole.length ? `<h6>Missing for your target role</h6><div class="mb-2">${r.missingForTargetRole.map(s => `<span class="skill-chip miss">${esc(s)}</span>`).join("")}</div>` : ""}
    ${r.aiFeedback ? `<h6 class="mt-3">AI feedback</h6><pre class="resume-out" id="aiTxt"></pre>` : `<div class="alert alert-info small mt-3 mb-0">${esc(r.aiNote || "")}</div>`}
  </div>`;
  if (r.aiFeedback) document.getElementById("aiTxt").textContent = r.aiFeedback;
}

// ============ Notifications ============
async function notifications() {
  const list = await api("/student/notifications");
  view.innerHTML = `<div class="d-flex justify-content-between align-items-center"><h3 class="page-title">Notifications</h3>
    <button class="btn btn-sm btn-outline-dark mb-3" id="markRead">Mark all as read</button></div>
    ${list.map(n => `<div class="tx-card p-3 mb-2 ${n.read ? "" : "border-start border-4 border-warning"}"><div class="fw-semibold">${esc(n.title)}</div>
      <div class="small">${esc(n.message)}</div><small class="text-muted">${fmtDateTime(n.createdAt)}</small></div>`).join("")
      || '<div class="text-muted">No notifications yet.</div>'}`;
  document.getElementById("markRead").onclick = () => guard(async () => { await api("/student/notifications/read", { method: "POST" }); notifications(); });
  refreshNotifBadge();
}

// ============ Complaints ============
async function complaints() {
  const list = await api("/student/complaints");
  view.innerHTML = `
    <h3 class="page-title">Report a Problem</h3>
    <form id="cf" class="tx-card p-3 mb-4">
      <input class="form-control mb-2" name="subject" placeholder="Subject" maxlength="200" required>
      <textarea class="form-control mb-2" name="message" rows="3" placeholder="Describe the problem" maxlength="3000" required></textarea>
      <button class="btn btn-amber">Submit</button></form>
    <h5 class="fw-bold">My complaints</h5>
    ${list.map(c => `<div class="tx-card p-3 mb-2"><div class="d-flex justify-content-between"><div class="fw-semibold">${esc(c.subject)}</div>${badge(c.status)}</div>
      <div class="small mt-1">${esc(c.message)}</div>
      ${c.adminResponse ? `<div class="small mt-2 p-2 bg-light rounded"><b>Admin reply:</b> ${esc(c.adminResponse)}</div>` : ""}
      <small class="text-muted">Submitted ${fmtDateTime(c.createdAt)} · updated ${fmtDateTime(c.updatedAt)}</small></div>`).join("")
      || '<div class="text-muted">You have not reported anything.</div>'}`;
  document.getElementById("cf").onsubmit = e => {
    e.preventDefault();
    guard(async () => { await api("/student/complaints", { method: "POST", body: Object.fromEntries(new FormData(e.target)) }); toast("Complaint submitted."); complaints(); });
  };
}

show("dashboard");
refreshNotifBadge();
