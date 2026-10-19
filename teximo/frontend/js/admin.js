Session.require("ADMIN");

const view = document.getElementById("view");
document.getElementById("who").textContent = Session.name || "Admin";
document.getElementById("logout").onclick = e => { e.preventDefault(); Session.logout(); };

const views = { stats, students, companies, drives, applications, interviews, complaints, settings };

document.getElementById("menu").onclick = e => {
  const a = e.target.closest("[data-view]");
  if (a) show(a.dataset.view);
};
let pendingView = null;
let renderingView = false;
function show(name) {
  pendingView = name;
  if (renderingView) return;
  renderingView = true;
  void (async () => {
    while (pendingView) {
      const currentView = pendingView;
      pendingView = null;
      document.querySelectorAll("#menu .nav-link").forEach(l => l.classList.toggle("active", l.dataset.view === currentView));
      view.innerHTML = '<div class="text-center text-muted py-5">Loading…</div>';
      view.onclick = null; view.onchange = null; view.onsubmit = null;
      await guard(() => views[currentView]());
      if (!pendingView) window.scrollTo(0, 0);
    }
    renderingView = false;
  })();
}
const formObj = f => Object.fromEntries(new FormData(f));
const nullIfBlank = o => { for (const k in o) if (o[k] === "") o[k] = null; return o; };

function addCsvExport(id, filename, columns, rows) {
  document.getElementById(id).onclick = () => {
    const cell = value => {
      let text = String(value ?? "");
      if (/^[\s]*[=+\-@]/.test(text)) text = "'" + text;
      return '"' + text.replace(/"/g, '""') + '"';
    };
    const csv = [columns.map(([label]) => cell(label)).join(","),
      ...rows.map(row => columns.map(([, value]) => cell(value(row))).join(","))].join("\r\n");
    const blob = new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };
}

// ============ Statistics ============
function bars(obj, color) {
  const entries = Object.entries(obj || {});
  const max = Math.max(1, ...entries.map(e => e[1]));
  return entries.map(([k, v]) => `<div class="mb-2"><div class="d-flex justify-content-between small"><span>${esc(k)}</span><b>${v}</b></div>
    <div class="progress" style="height:8px"><div class="progress-bar bg-${color}" style="width:${v / max * 100}%"></div></div></div>`).join("")
    || '<div class="text-muted small">No data yet.</div>';
}
async function stats() {
  const s = await api("/admin/stats");
  const card = (n, l) => `<div class="col-6 col-lg-3"><div class="tx-card stat-card"><div class="num">${n}</div><div class="lbl">${l}</div></div></div>`;
  view.innerHTML = `<h3 class="page-title">Placement Statistics</h3>
    <div class="row g-3 mb-4">${card(s.students, "Students")}${card(s.placed, "Placed")}${card(s.placementRate + "%", "Placement rate")}${card(s.inProcess, "In process")}
      ${card(s.companies, "Companies")}${card(s.drives, "Drives")}${card(s.applications, "Applications")}${card(s.openComplaints, "Open complaints")}</div>
    <div class="row g-3">
      <div class="col-lg-4"><div class="tx-card p-3 h-100"><h6 class="fw-bold">Students by branch</h6>${bars(s.byBranch, "primary")}</div></div>
      <div class="col-lg-4"><div class="tx-card p-3 h-100"><h6 class="fw-bold">Placed by branch</h6>${bars(s.placedByBranch, "success")}</div></div>
      <div class="col-lg-4"><div class="tx-card p-3 h-100"><h6 class="fw-bold">Applications by status</h6>${bars(s.applicationsByStatus, "warning")}</div></div>
    </div>`;
}

// ============ Students ============
async function students() {
  const list = await api("/admin/students");
  view.innerHTML = `<div class="d-flex justify-content-between align-items-center flex-wrap"><div class="d-flex align-items-center gap-2"><h3 class="page-title">Student Records</h3>
    <button class="btn btn-outline-dark btn-sm mb-3" id="exportStudents">Download CSV (all)</button></div>
    <input class="form-control mb-3" style="max-width:260px" id="q" placeholder="Search name, email, branch…"></div>
    <div class="tx-card p-0 table-responsive"><table class="table table-hover mb-0"><thead><tr>
      <th>Name</th><th>Email</th><th>Branch</th><th>CGPA</th><th>Backlogs</th><th>Batch</th><th>Status</th><th></th></tr></thead>
      <tbody id="rows"></tbody></table></div>`;
  const draw = () => {
    const q = document.getElementById("q").value.toLowerCase();
    document.getElementById("rows").innerHTML = list.filter(s => (s.name + s.email + (s.branch || "")).toLowerCase().includes(q)).map(s => `
      <tr><td>${esc(s.name)}<div class="small text-muted">${esc(s.college || "")}</div></td><td>${esc(s.email)}</td><td>${esc(s.branch || "—")}</td>
      <td>${esc(s.cgpa ?? "—")}</td><td>${esc(s.backlogs ?? 0)}</td><td>${esc(s.graduationYear ?? "—")}</td>
      <td><select class="form-select form-select-sm" data-status="${s.id}" style="min-width:130px">
        ${["NOT_PLACED", "IN_PROCESS", "PLACED"].map(o => `<option ${o === s.placementStatus ? "selected" : ""}>${o}</option>`).join("")}</select></td>
      <td><button class="btn btn-sm btn-outline-danger" data-del="${s.id}">Remove</button></td></tr>`).join("")
      || '<tr><td colspan="8" class="text-center text-muted py-4">No students found.</td></tr>';
  };
  draw();
  addCsvExport("exportStudents", "teximo-students.csv", [
    ["Name", s => s.name], ["College", s => s.college], ["Email", s => s.email], ["Branch", s => s.branch],
    ["CGPA", s => s.cgpa], ["Backlogs", s => s.backlogs], ["Batch", s => s.graduationYear],
    ["Placement status", s => s.placementStatus]
  ], list);
  document.getElementById("q").oninput = draw;
  view.onchange = e => { const sel = e.target.closest("[data-status]"); if (sel)
    guard(async () => { await api("/admin/students/" + sel.dataset.status + "/status", { method: "PUT", body: { status: sel.value } }); toast("Status updated."); }); };
  view.onclick = e => { const b = e.target.closest("[data-del]"); if (b && confirm("Remove this student record permanently?"))
    guard(async () => { await api("/admin/students/" + b.dataset.del, { method: "DELETE" }); students(); }); };
}

// ============ Companies ============
async function companies() {
  const list = await api("/admin/companies");
  const picker = (field, label, placeholder) => `
    <div class="col-md-6">
      <label class="form-label" for="company-${field}">${label}</label>
      <div class="input-group">
        <input class="form-control" id="company-${field}" name="${field}" placeholder="${placeholder}"
          ${field === "name" ? "required" : ""} list="live-${field}-options" autocomplete="off">
        <button class="btn btn-outline-secondary" type="button" data-show-options="${field}" aria-label="Show ${label} options" title="Show options">▾</button>
      </div>
      <datalist id="live-${field}-options"></datalist>
      ${field === "location" ? '<div class="form-text">Choose a city or business area in India. Suggestions are common work locations, not verified company offices.</div>' : ""}
    </div>`;
  view.innerHTML = `<div class="d-flex justify-content-between align-items-center"><h3 class="page-title">Companies</h3><button class="btn btn-outline-dark btn-sm mb-3" id="exportCompanies">Download CSV</button></div>
    <form id="cf" class="tx-card p-3 mb-4"><div class="row g-2">
      ${picker("name", "Company name", "Company name")}
      ${picker("industry", "Industry", "Industry")}
      ${picker("location", "Location", "City, state, country")}
      ${picker("website", "Website", "https://example.com")}
      <div class="col-12">
        <label class="form-label" for="company-description">Short description</label>
        <input class="form-control" id="company-description" name="description" placeholder="Short description">
      </div>
      <div class="col-12 d-flex justify-content-end"><button class="btn btn-amber px-4">Add company</button></div>
    </div></form>
    <div class="tx-card p-0 table-responsive"><table class="table mb-0"><thead><tr><th>Name</th><th>Industry</th><th>Location</th><th>Website</th><th></th></tr></thead>
    <tbody>${list.map(c => `<tr><td class="fw-semibold">${esc(c.name)}</td><td>${esc(c.industry || "—")}</td><td>${esc(c.location || "—")}</td>
      <td>${/^https?:\/\//i.test(c.website || "") ? `<a href="${esc(c.website)}" target="_blank" rel="noopener noreferrer">${esc(c.website)}</a>` : esc(c.website || "—")}</td>
      <td class="text-end"><button class="btn btn-sm btn-outline-danger" data-del="${c.id}">Delete</button></td></tr>`).join("")
      || '<tr><td colspan="5" class="text-center text-muted py-4">No companies yet.</td></tr>'}</tbody></table></div>
    <section class="mt-4"><h5>Live hiring companies from RapidAPI</h5>
      <div class="small text-muted mb-2">Employer names from current job listings; these are not saved to the database.</div>
      <div id="liveCompanies" class="text-muted">Loading live companies…</div></section>`;
  addCsvExport("exportCompanies", "teximo-companies.csv", [
    ["Name", c => c.name], ["Industry", c => c.industry], ["Location", c => c.location], ["Website", c => c.website],
    ["Source", c => c.source || "Saved company"]
  ], list);
  const liveCompanies = document.getElementById("liveCompanies");
  let live = [];
  const industrySuggestions = [
    "Information Technology", "Banking & Finance", "Consulting", "Manufacturing & Engineering",
    "Healthcare", "Retail & E-commerce", "Education", "Telecommunications", "Automotive",
    "Energy", "Government & Public Sector", "Media & Entertainment"
  ];
  const indianAreaSuggestions = [
    "OMR, Chennai, Tamil Nadu, India", "Guindy, Chennai, Tamil Nadu, India",
    "T. Nagar, Chennai, Tamil Nadu, India", "Whitefield, Bengaluru, Karnataka, India",
    "Electronic City, Bengaluru, Karnataka, India", "Outer Ring Road, Bengaluru, Karnataka, India",
    "HITEC City, Hyderabad, Telangana, India", "Gachibowli, Hyderabad, Telangana, India",
    "BKC, Mumbai, Maharashtra, India", "Powai, Mumbai, Maharashtra, India",
    "Andheri, Mumbai, Maharashtra, India", "Hinjawadi, Pune, Maharashtra, India",
    "Kharadi, Pune, Maharashtra, India", "Cyber City, Gurugram, Haryana, India",
    "Noida Sector 62, Uttar Pradesh, India", "Salt Lake, Kolkata, West Bengal, India",
    "New Town, Kolkata, West Bengal, India", "Kochi Infopark, Kerala, India",
    "Technopark, Thiruvananthapuram, Kerala, India", "GIFT City, Gandhinagar, Gujarat, India"
  ];
  const setCompanyDetails = name => {
    const selected = live.find(company => company.name.toLowerCase() === name.trim().toLowerCase());
    if (!selected) return;
    for (const field of ["industry", "location", "website"]) {
      if (selected[field]) document.getElementById(`company-${field}`).value = selected[field];
    }
  };
  document.getElementById("company-name").addEventListener("change", e => setCompanyDetails(e.target.value));
  document.getElementById("company-name").addEventListener("input", e => setCompanyDetails(e.target.value));
  try {
    live = await api("/admin/live-companies");
    for (const field of ["name", "industry", "location", "website"]) {
      const values = [...new Set(live.map(company => String(company[field] || "").trim()).filter(Boolean))];
      const options = field === "industry" && !values.length ? industrySuggestions
        : field === "location" ? [...new Set([...indianAreaSuggestions, ...values])] : values;
      document.getElementById(`live-${field}-options`).innerHTML =
        options.map(value => `<option value="${esc(value)}"></option>`).join("");
    }
    view.onclick = e => {
      const showOptions = e.target.closest("[data-show-options]");
      if (showOptions) {
        const input = document.getElementById(`company-${showOptions.dataset.showOptions}`);
        input.focus();
        if (typeof input.showPicker === "function") input.showPicker();
        return;
      }
      const remove = e.target.closest("[data-del]");
      if (remove && confirm("Delete this company?"))
        guard(async () => { await api("/admin/companies/" + remove.dataset.del, { method: "DELETE" }); companies(); });
    };
    liveCompanies.innerHTML = live.length
      ? `<div class="list-group">${live.map(c => `<div class="list-group-item d-flex justify-content-between gap-3">
          <span class="fw-semibold">${esc(c.name)}</span><span class="small text-muted text-end">${esc([c.industry, c.location].filter(Boolean).join(" · "))}${/^https?:\/\//i.test(c.website || "") ? ` · <a href="${esc(c.website)}" target="_blank" rel="noopener noreferrer">Website</a>` : ""}</span></div>`).join("")}</div>`
      : '<div class="text-muted">No employer names were returned with the current job results.</div>';
    addCsvExport("exportCompanies", "teximo-companies.csv", [
      ["Name", c => c.name], ["Industry", c => c.industry], ["Location", c => c.location], ["Website", c => c.website],
      ["Source", c => c.source || "Saved company"]
    ], list.concat(live.map(c => ({ ...c, source: "Live JSearch" }))));
    if (live.length) document.getElementById("exportCompanies").textContent = "Download CSV (saved + live)";
  } catch (error) {
    liveCompanies.innerHTML = `<div class="alert alert-warning mb-0">Live company data is unavailable: ${esc(error.message)}</div>`;
  }
  document.getElementById("cf").onsubmit = e => { e.preventDefault();
    guard(async () => { await api("/admin/companies", { method: "POST", body: formObj(e.target) }); toast("Company added."); companies(); }); };
  if (!live.length) view.onclick = e => {
    const showOptions = e.target.closest("[data-show-options]");
    if (showOptions) {
      const input = document.getElementById(`company-${showOptions.dataset.showOptions}`);
      input.focus();
      if (typeof input.showPicker === "function") input.showPicker();
      return;
    }
    const b = e.target.closest("[data-del]");
    if (b && confirm("Delete this company?"))
      guard(async () => { await api("/admin/companies/" + b.dataset.del, { method: "DELETE" }); companies(); });
  };
}

// ============ Drives ============
async function drives() {
  const [list, cos] = await Promise.all([api("/admin/drives"), api("/admin/companies")]);
  let live = [];
  let liveError = "";
  try {
    live = await api("/admin/live-companies");
  } catch (error) {
    liveError = error.message;
  }
  view.innerHTML = `<div class="d-flex justify-content-between align-items-center"><h3 class="page-title">Placement Drives</h3><button class="btn btn-outline-dark btn-sm mb-3" id="exportDrives">Download CSV</button></div>
    ${cos.length || live.length ? "" : `<div class="alert alert-warning">${liveError ? `Live companies are unavailable: ${esc(liveError)}. ` : ""}Add a company first, then create a drive for it.</div>`}
    <form id="df" class="tx-card p-3 mb-4"><div class="row g-2">
      <div class="col-md-3"><label class="form-label small mb-0" for="driveCompanySelect">Company</label><select class="form-select" id="driveCompanySelect" name="companyId" required>
        <option value="">Choose…</option>
        ${cos.length ? `<optgroup label="Saved companies">${cos.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join("")}</optgroup>` : ""}
        ${live.length ? `<optgroup label="Live hiring companies">${live.map((c, index) => cos.some(saved => saved.name.toLowerCase() === c.name.toLowerCase())
          ? "" : `<option value="live:${index}">${esc(c.name)}${c.location ? ` — ${esc(c.location)}` : ""}</option>`).join("")}</optgroup>` : ""}</select>
        <div class="form-text">Selecting a live company adds it to your saved Companies first.</div></div>
      ${liveError ? `<div class="col-12"><div class="small text-warning">Live company suggestions unavailable: ${esc(liveError)}</div></div>` : ""}
      <div class="col-md-3"><label class="form-label small mb-0">Title</label><input class="form-control" name="title" placeholder="Software Engineer" required></div>
      <div class="col-md-2"><label class="form-label small mb-0">Type</label><select class="form-select" name="type"><option>PLACEMENT</option><option>INTERNSHIP</option></select></div>
      <div class="col-md-2"><label class="form-label small mb-0">CTC / Stipend</label><input class="form-control" name="ctc" placeholder="6 LPA"></div>
      <div class="col-md-2"><label class="form-label small mb-0">Batch</label><input class="form-control" type="number" name="graduationYear" placeholder="2027"></div>
      <div class="col-md-3"><label class="form-label small mb-0">Drive date</label><input class="form-control" type="date" name="driveDate"></div>
      <div class="col-md-3"><label class="form-label small mb-0">Last date to apply</label><input class="form-control" type="date" name="lastDateToApply"></div>
      <div class="col-md-2"><label class="form-label small mb-0">Min CGPA</label><input class="form-control" type="number" step="0.1" min="0" max="10" name="minCgpa" value="6"></div>
      <div class="col-md-2"><label class="form-label small mb-0">Max backlogs</label><input class="form-control" type="number" min="0" name="maxBacklogs" value="0"></div>
      <div class="col-md-2"><label class="form-label small mb-0">Branches</label><input class="form-control" name="allowedBranches" placeholder="CSE, IT (blank = all)"></div>
      <div class="col-md-6"><label class="form-label small mb-0">Required skills</label><input class="form-control" name="requiredSkills" placeholder="java, sql, react"></div>
      <div class="col-md-6"><label class="form-label small mb-0">Description</label><input class="form-control" name="description"></div>
      <div class="col-12"><button class="btn btn-amber">Create drive &amp; notify eligible students</button></div></div></form>
    <div class="tx-card p-0 table-responsive"><table class="table mb-0"><thead><tr><th>Company / Drive</th><th>Dates</th><th>Eligibility</th><th></th></tr></thead>
    <tbody>${list.map(d => `<tr><td><div class="fw-semibold">${esc(d.companyName)}</div><div class="small">${esc(d.title)} · ${esc(d.type)} ${d.ctc ? "· " + esc(d.ctc) : ""}</div></td>
      <td class="small">Drive ${fmtDate(d.driveDate)}<br>Apply by ${fmtDate(d.lastDateToApply)}</td>
      <td class="small">CGPA ≥ ${esc(d.minCgpa ?? 0)} · backlogs ≤ ${esc(d.maxBacklogs ?? 0)}<br>${d.allowedBranches ? esc(d.allowedBranches) : "All branches"}${d.graduationYear ? " · " + esc(d.graduationYear) : ""}</td>
      <td class="text-end"><button class="btn btn-sm btn-outline-danger" data-del="${d.id}">Delete</button></td></tr>`).join("")
      || '<tr><td colspan="4" class="text-center text-muted py-4">No drives yet.</td></tr>'}</tbody></table></div>`;
  addCsvExport("exportDrives", "teximo-placement-drives.csv", [
    ["Company", d => d.companyName], ["Title", d => d.title], ["Type", d => d.type], ["CTC / stipend", d => d.ctc],
    ["Drive date", d => d.driveDate], ["Last date to apply", d => d.lastDateToApply], ["Minimum CGPA", d => d.minCgpa],
    ["Maximum backlogs", d => d.maxBacklogs], ["Allowed branches", d => d.allowedBranches], ["Graduation year", d => d.graduationYear]
  ], list);
  document.getElementById("driveCompanySelect").onchange = e => {
    const value = e.target.value;
    if (!value.startsWith("live:")) return;
    const selected = live[Number(value.slice(5))];
    if (!selected) { e.target.value = ""; return; }
    e.target.disabled = true;
    guard(async () => {
      const saved = await api("/admin/companies", { method: "POST", body: {
        name: selected.name, industry: selected.industry, location: selected.location,
        website: selected.website, description: ""
      } });
      const option = document.createElement("option");
      option.value = saved.id;
      option.textContent = saved.name;
      option.selected = true;
      let group = e.target.querySelector('optgroup[label="Saved companies"]');
      if (!group) {
        group = document.createElement("optgroup");
        group.label = "Saved companies";
        e.target.insertBefore(group, e.target.querySelector('optgroup[label="Live hiring companies"]'));
      }
      group.appendChild(option);
      const liveOption = [...e.target.options].find(item => item.value === value);
      if (liveOption) liveOption.remove();
      toast("Live company added to saved Companies.");
      return true;
    }).then(saved => { if (!saved) e.target.value = ""; })
      .finally(() => { e.target.disabled = false; });
  };
  document.getElementById("df").onsubmit = e => { e.preventDefault();
    guard(async () => { await api("/admin/drives", { method: "POST", body: nullIfBlank(formObj(e.target)) }); toast("Drive created."); drives(); }); };
  view.onclick = e => { const b = e.target.closest("[data-del]"); if (b && confirm("Delete this drive?"))
    guard(async () => { await api("/admin/drives/" + b.dataset.del, { method: "DELETE" }); drives(); }); };
}

// ============ Applications ============
async function applications() {
  const list = await api("/admin/applications");
  view.innerHTML = `<div class="d-flex justify-content-between align-items-center"><h3 class="page-title">Applications</h3><button class="btn btn-outline-dark btn-sm mb-3" id="exportApplications">Download CSV</button></div>
    <div class="tx-card p-0 table-responsive"><table class="table mb-0"><thead><tr><th>Student</th><th>Company</th><th>Drive</th><th>Applied</th><th>Status</th></tr></thead>
    <tbody>${list.map(a => `<tr><td>${esc(a.studentName)}</td><td>${esc(a.companyName)}</td><td>${esc(a.driveTitle)}</td><td>${fmtDate(a.appliedAt)}</td>
      <td><select class="form-select form-select-sm" style="min-width:140px" data-app="${a.id}">
        ${["APPLIED", "SHORTLISTED", "INTERVIEW", "SELECTED", "REJECTED"].map(o => `<option ${o === a.status ? "selected" : ""}>${o}</option>`).join("")}</select></td></tr>`).join("")
      || '<tr><td colspan="5" class="text-center text-muted py-4">No applications yet.</td></tr>'}</tbody></table></div>`;
  addCsvExport("exportApplications", "teximo-applications.csv", [
    ["Student", a => a.studentName], ["Company", a => a.companyName], ["Drive", a => a.driveTitle],
    ["Applied", a => a.appliedAt], ["Status", a => a.status]
  ], list);
  view.onchange = e => { const sel = e.target.closest("[data-app]"); if (sel)
    guard(async () => { await api("/admin/applications/" + sel.dataset.app + "/status", { method: "PUT", body: { status: sel.value } }); toast("Status updated and student notified."); }); };
}

// ============ Interviews ============
async function interviews() {
  const [list, drv, apps] = await Promise.all([api("/admin/interviews"), api("/admin/drives"), api("/admin/applications")]);
  const eligibleApps = apps.filter(a => ["SHORTLISTED", "INTERVIEW", "APPLIED"].includes(a.status));
  view.innerHTML = `<div class="d-flex justify-content-between align-items-center"><h3 class="page-title">Interview Schedule</h3><button class="btn btn-outline-dark btn-sm mb-3" id="exportInterviews">Download CSV</button></div>
    <form id="inf" class="tx-card p-3 mb-4"><div class="row g-2">
      <div class="col-md-5"><label class="form-label small mb-0">Student application</label><select class="form-select" id="appSel" required>
        <option value="">Choose…</option>${eligibleApps.map(a => `<option value="${a.id}" data-d="${a.driveId}" data-s="${a.studentId}">${esc(a.studentName)} — ${esc(a.companyName)} (${esc(a.status)})</option>`).join("")}</select></div>
      <div class="col-md-3"><label class="form-label small mb-0">Date &amp; time</label><input class="form-control" type="datetime-local" name="scheduledAt" required></div>
      <div class="col-md-2"><label class="form-label small mb-0">Round</label><input class="form-control" name="round" value="Technical"></div>
      <div class="col-md-2"><label class="form-label small mb-0">Mode</label><select class="form-select" name="mode"><option>ONLINE</option><option>ONSITE</option></select></div>
      <div class="col-md-9"><input class="form-control" name="venueOrLink" placeholder="Venue or meeting link"></div>
      <div class="col-md-3"><button class="btn btn-amber w-100">Schedule &amp; notify</button></div></div></form>
    <div class="tx-card p-0 table-responsive"><table class="table mb-0"><thead><tr><th>Student</th><th>Drive</th><th>When</th><th>Round</th><th>Mode</th><th>Venue / link</th><th></th></tr></thead>
    <tbody>${list.map(i => `<tr><td>${esc(i.studentName)}</td><td>${esc(i.driveTitle)}</td><td>${fmtDateTime(i.scheduledAt)}</td><td>${esc(i.round)}</td><td>${esc(i.mode)}</td><td>${esc(i.venueOrLink || "—")}</td>
      <td class="text-end"><button class="btn btn-sm btn-outline-danger" data-del="${i.id}">Cancel</button></td></tr>`).join("")
      || '<tr><td colspan="7" class="text-center text-muted py-4">No interviews scheduled.</td></tr>'}</tbody></table></div>`;
  addCsvExport("exportInterviews", "teximo-interviews.csv", [
    ["Student", i => i.studentName], ["Drive", i => i.driveTitle], ["Scheduled at", i => i.scheduledAt],
    ["Round", i => i.round], ["Mode", i => i.mode], ["Venue or link", i => i.venueOrLink]
  ], list);
  document.getElementById("inf").onsubmit = e => { e.preventDefault();
    const opt = document.getElementById("appSel").selectedOptions[0];
    const body = { ...formObj(e.target), driveId: opt.dataset.d, studentId: opt.dataset.s };
    guard(async () => { await api("/admin/interviews", { method: "POST", body }); toast("Interview scheduled."); interviews(); }); };
  view.onclick = e => { const b = e.target.closest("[data-del]"); if (b && confirm("Cancel this interview?"))
    guard(async () => { await api("/admin/interviews/" + b.dataset.del, { method: "DELETE" }); interviews(); }); };
}

// ============ Complaints ============
async function complaints() {
  const list = await api("/admin/complaints");
  view.innerHTML = `<h3 class="page-title">Student Complaints</h3>` + (list.map(c => `
    <div class="tx-card p-3 mb-3"><div class="d-flex justify-content-between"><div><div class="fw-bold">${esc(c.subject)}</div>
      <small class="text-muted">${esc(c.studentName)} · ${fmtDateTime(c.createdAt)}</small></div>${badge(c.status)}</div>
      <div class="small my-2">${esc(c.message)}</div>
      <form data-c="${c.id}" class="row g-2"><div class="col-md-6"><input class="form-control form-control-sm" name="adminResponse" placeholder="Reply to student" value="${esc(c.adminResponse || "")}"></div>
        <div class="col-md-3"><select class="form-select form-select-sm" name="status">${["OPEN", "IN_REVIEW", "RESOLVED"].map(o => `<option ${o === c.status ? "selected" : ""}>${o}</option>`).join("")}</select></div>
        <div class="col-md-3"><button class="btn btn-sm btn-amber w-100">Update</button></div></form></div>`).join("")
    || '<div class="text-muted">No complaints. 🎉</div>');
  view.onsubmit = e => { const f = e.target.closest("[data-c]"); if (!f) return; e.preventDefault();
    guard(async () => { await api("/admin/complaints/" + f.dataset.c, { method: "PUT", body: formObj(f) }); toast("Complaint updated."); complaints(); }); };
}

// ============ API & Database settings ============
async function settings() {
  const config = await api("/admin/config");
  const badgeSet = saved => saved
    ? '<span class="badge text-bg-success">Saved</span>'
    : '<span class="badge text-bg-secondary">Not set</span>';
  const secretInput = (id, label, saved, placeholder) => `
    <div class="mb-3">
      <label class="form-label d-flex justify-content-between" for="${id}">
        <span>${label}</span>${badgeSet(saved)}
      </label>
      <div class="secret-input">
        <input class="form-control" type="password" id="${id}" autocomplete="new-password" spellcheck="false"
          placeholder="${saved ? "Saved — type to replace" : placeholder}">
        <button type="button" class="btn btn-sm btn-light" data-secret-eye="${id}">Show</button>
      </div>
    </div>`;
  const endpointInput = (id, label, value, placeholder, source) => `
    <div class="mb-3">
      <label class="form-label" for="${id}">${label}</label>
      <input class="form-control" type="url" id="${id}" value="${esc(value || "")}"
        placeholder="${placeholder}" autocomplete="off" spellcheck="false">
      <div class="form-text">${source}. Paste only the URL after <code>--url</code>, not the cURL command or headers. Query parameters are removed automatically. Never paste the API key here.</div>
    </div>`;

  view.innerHTML = `<h3 class="page-title">API &amp; Database Configuration</h3>
    <div class="alert alert-info small">API keys are sent to the backend and encrypted at rest. Saved keys are never sent back to this page. Leave a key field blank to keep its saved value.</div>
    <div class="row g-3">
      <div class="col-lg-6"><section class="tx-card p-4 h-100">
        <h5 class="fw-bold mb-3">RapidAPI</h5>
        <div class="small text-muted mb-2">Paste the key for each RapidAPI integration below. Saved values remain encrypted and masked.</div>
        ${secretInput("rapidJobSearchKey", "Job search RapidAPI key", config.rapidApiJobSearchKeySet || config.rapidApiKeySet, "Paste job search key")}
        ${secretInput("rapidJobSalaryKey", "Job salary RapidAPI key", config.rapidApiJobSalaryKeySet, "Paste job salary key")}
        ${secretInput("rapidCompanyJobSearchKey", "Company job search RapidAPI key", config.rapidApiCompanyJobSearchKeySet, "Paste company job search key")}
        ${secretInput("rapidJobDetailsKey", "Job details RapidAPI key", config.rapidApiJobDetailsKeySet, "Paste job details key")}
        ${endpointInput("rapidJobSearchUrl", "JSearch job-search endpoint URL", config.rapidApiJobSearchUrl, "https://...rapidapi.com/search", "RapidAPI → JSearch → Job Search → Code Snippets")}
        ${endpointInput("rapidJobDetailsUrl", "JSearch job-details endpoint URL", config.rapidApiJobDetailsUrl, "https://...rapidapi.com/job-details", "RapidAPI → JSearch → Job Details → Code Snippets")}
        ${endpointInput("rapidJobSalaryUrl", "JSearch job-salary endpoint URL", config.rapidApiJobSalaryUrl, "https://...rapidapi.com/job-salary", "RapidAPI → JSearch → Job Salary → Code Snippets")}
        ${endpointInput("rapidCompanyJobSalaryUrl", "JSearch company-job-salary endpoint URL", config.rapidApiCompanyJobSalaryUrl, "https://...rapidapi.com/company-job-salary", "RapidAPI → JSearch → Company Job Salary → Code Snippets")}
        <div class="small text-muted mb-3">These endpoint URLs are saved for configuration. Currently, only job search is connected to a live request and covered by the connection test.</div>
        <button class="btn btn-outline-dark btn-sm" data-config-test="rapidapi">Test RapidAPI job search</button>
      </section></div>
      <div class="col-lg-6"><section class="tx-card p-4 h-100">
        <h5 class="fw-bold mb-3">OpenRouter</h5>
        ${secretInput("openRouterKey", "OpenRouter API key", config.openRouterKeySet, "Paste OpenRouter key")}
        <div class="mb-3"><label class="form-label" for="openRouterModel">Model (optional)</label>
          <input class="form-control" id="openRouterModel" value="${esc(config.openRouterModel)}" placeholder="openai/gpt-4o-mini"></div>
        <button class="btn btn-outline-dark btn-sm" data-config-test="openrouter">Test OpenRouter</button>
      </section></div>
      <div class="col-12"><section class="tx-card p-4">
        <h5 class="fw-bold mb-3">MySQL</h5>
        <div class="row">
          <div class="col-lg-4">${secretInput("dbUrl", "JDBC URL", config.dbUrlSet, "jdbc:mysql://host:3306/teximo")}</div>
          <div class="col-lg-4">${secretInput("dbUsername", "Database username", config.dbUsernameSet, "Database username")}</div>
          <div class="col-lg-4">${secretInput("dbPassword", "Database password", config.dbPasswordSet, "Database password")}</div>
        </div>
        <button class="btn btn-outline-dark btn-sm" data-config-test="database">Test database connection</button>
        <div class="small text-muted mt-2">Database changes take effect after restarting the backend.</div>
      </section></div>
    </div>
    <button class="btn btn-amber mt-3 px-4" id="saveConfig">Save securely</button>
    <div id="configResult" class="mt-3" aria-live="polite"></div>`;

  view.onclick = e => {
    const eye = e.target.closest("[data-secret-eye]");
    if (eye) {
      const input = document.getElementById(eye.dataset.secretEye);
      const reveal = input.type === "password";
      input.type = reveal ? "text" : "password";
      eye.textContent = reveal ? "Hide" : "Show";
      return;
    }

    const testButton = e.target.closest("[data-config-test]");
    if (testButton) {
      testButton.disabled = true;
      const result = document.getElementById("configResult");
      result.textContent = "Testing…";
      api("/admin/config/test/" + testButton.dataset.configTest, { method: "POST", body: {} })
        .then(response => { result.innerHTML = `<div class="alert alert-success">${esc(response.message)}</div>`; })
        .catch(error => { result.innerHTML = `<div class="alert alert-danger">${esc(error.message)}</div>`; })
        .finally(() => { testButton.disabled = false; });
      return;
    }

    if (e.target.closest("#saveConfig")) {
      guard(async () => {
        const value = id => document.getElementById(id).value;
        const saved = await api("/admin/config", { method: "PUT", body: {
          rapidApiJobSearchKey: value("rapidJobSearchKey"),
          rapidApiJobSalaryKey: value("rapidJobSalaryKey"),
          rapidApiCompanyJobSearchKey: value("rapidCompanyJobSearchKey"),
          rapidApiJobDetailsKey: value("rapidJobDetailsKey"),
          rapidApiJobSearchUrl: value("rapidJobSearchUrl"),
          rapidApiJobDetailsUrl: value("rapidJobDetailsUrl"),
          rapidApiJobSalaryUrl: value("rapidJobSalaryUrl"),
          rapidApiCompanyJobSalaryUrl: value("rapidCompanyJobSalaryUrl"),
          openRouterKey: value("openRouterKey"),
          openRouterModel: value("openRouterModel"),
          dbUrl: value("dbUrl"),
          dbUsername: value("dbUsername"),
          dbPassword: value("dbPassword")
        } });
        toast(saved.message || "Configuration saved securely.");
        await settings();
      });
    }
  };
}

show("stats");
