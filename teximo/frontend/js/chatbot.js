// Teximo's career assistant sends questions to the backend, which calls OpenRouter.
(function () {
  const root = document.createElement("div");
  root.innerHTML = `
    <div class="assistant-panel" id="assistantPanel" role="dialog" aria-label="Teximo personal assistant">
      <div class="assistant-head">
        <div>
          <div class="fw-bold">Teximo AI Assistant</div>
          <small class="opacity-75">Your personal career assistant</small>
        </div>
        <button class="btn-close btn-close-white" id="assistantClose" aria-label="Close"></button>
      </div>
      <div class="assistant-msgs" id="assistantMsgs"></div>
      <div class="assistant-chips" id="assistantChips">
        <button class="btn btn-sm btn-outline-secondary">How do I improve my resume?</button>
        <button class="btn btn-sm btn-outline-secondary">Common HR interview questions</button>
        <button class="btn btn-sm btn-outline-secondary">How to prepare for placements?</button>
      </div>
      <form class="assistant-form" id="assistantForm">
        <input class="form-control" id="assistantInput" maxlength="2000" placeholder="Type your question…" autocomplete="off">
        <button class="btn btn-amber" type="submit" id="assistantSend">Send</button>
      </form>
    </div>
    <button class="assistant-launcher" id="assistantOpen" aria-label="Open personal assistant">
      <span class="assistant-round" aria-hidden="true">
        <svg class="assistant-giraffe" viewBox="0 0 100 100" role="img">
          <path d="M38 75c0-9 4-14 4-24V39h18v13c0 9 4 14 4 23v14H38z" fill="#f2b84b" stroke="#b77918" stroke-width="2"/>
          <ellipse cx="27" cy="39" rx="11" ry="7" fill="#f2b84b" stroke="#b77918" stroke-width="2" transform="rotate(-25 27 39)"/>
          <ellipse cx="73" cy="39" rx="11" ry="7" fill="#f2b84b" stroke="#b77918" stroke-width="2" transform="rotate(25 73 39)"/>
          <path d="M38 33 35 17M62 33l3-16" stroke="#b77918" stroke-width="5" stroke-linecap="round"/>
          <circle cx="35" cy="15" r="5" fill="#8a5119"/><circle cx="65" cy="15" r="5" fill="#8a5119"/>
          <ellipse cx="50" cy="48" rx="25" ry="24" fill="#ffd36b" stroke="#b77918" stroke-width="2"/>
          <ellipse cx="38" cy="41" rx="8" ry="10" fill="#fff"/>
          <ellipse cx="62" cy="41" rx="8" ry="10" fill="#fff"/>
          <g class="assistant-eye"><circle cx="40" cy="43" r="4.5" fill="#2b1a05"/><circle cx="41.5" cy="41.5" r="1.4" fill="#fff"/></g>
          <g class="assistant-eye"><circle cx="60" cy="43" r="4.5" fill="#2b1a05"/><circle cx="61.5" cy="41.5" r="1.4" fill="#fff"/></g>
          <ellipse cx="50" cy="59" rx="14" ry="10" fill="#ffe7a6"/>
          <ellipse cx="45" cy="57" rx="2" ry="1.5" fill="#8a5119"/><ellipse cx="55" cy="57" rx="2" ry="1.5" fill="#8a5119"/>
          <path d="M45 62q5 5 10 0" fill="none" stroke="#8a5119" stroke-width="2" stroke-linecap="round"/>
          <path d="m32 51 7-3 4 6-7 4zm30-4 7 3-4 7-7-4zM44 25l5-3 4 5-5 4zm12 15 5-3 4 5-5 4z" fill="#d9962c"/>
        </svg>
      </span>
      <span class="assistant-label">Ask me anything</span>
    </button>`;
  document.body.appendChild(root);

  const panel = document.getElementById("assistantPanel");
  const msgs = document.getElementById("assistantMsgs");
  const input = document.getElementById("assistantInput");
  const history = [];

  function add(role, text) {
    const message = document.createElement("div");
    message.className = "assistant-message " + (role === "user" ? "me" : "bot");
    message.textContent = text;
    msgs.appendChild(message);
    msgs.scrollTop = msgs.scrollHeight;
    return message;
  }

  function toggle(open) {
    panel.classList.toggle("open", open);
    if (open) {
      if (!msgs.children.length) {
        add("assistant", `Hi "${Session.name || "there"}"! I'm your personal assistant. Ask me about resumes, interviews, placements or jobs.`);
      }
      input.focus();
    }
  }

  document.getElementById("assistantOpen").onclick = () => toggle(!panel.classList.contains("open"));
  document.getElementById("assistantClose").onclick = () => toggle(false);

  async function send(text) {
    text = text.trim();
    if (!text) return;
    add("user", text);
    const responseMessage = add("assistant", "Thinking…");
    const button = document.getElementById("assistantSend");
    button.disabled = true;
    try {
      const response = await api("/chat", { method: "POST", body: { message: text, history } });
      responseMessage.textContent = response.reply || "I couldn't think of an answer. Try rephrasing?";
      history.push({ role: "user", content: text }, { role: "assistant", content: responseMessage.textContent });
    } catch (error) {
      responseMessage.textContent = error.message;
    } finally {
      button.disabled = false;
      msgs.scrollTop = msgs.scrollHeight;
    }
  }

  document.getElementById("assistantForm").onsubmit = event => {
    event.preventDefault();
    const text = input.value;
    input.value = "";
    send(text);
  };
  document.getElementById("assistantChips").onclick = event => {
    if (event.target.tagName === "BUTTON") send(event.target.textContent);
  };

  window.askTeximoAssistant = question => { toggle(true); if (question) send(question); };
})();
