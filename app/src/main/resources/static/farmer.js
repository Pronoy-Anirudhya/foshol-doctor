(() => {
  const api = "";
  let token = null;
  let audioBlob = null;
  let mediaRecorder = null;
  let chunks = [];

  const $ = (id) => document.getElementById(id);
  const login = $("login");
  const app = $("app");
  const loginError = $("login-error");
  const faqError = $("faq-error");

  function showError(el, problem) {
    el.hidden = false;
    if (!problem) {
      el.textContent = "অনুরোধ ব্যর্থ হয়েছে।";
      return;
    }
    el.textContent = (problem.detail || problem.title || "ত্রুটি") +
      (problem.correlationId ? "  [" + problem.correlationId + "]" : "");
  }

  async function call(path, options) {
    const headers = Object.assign({ "X-Correlation-Id": crypto.randomUUID() }, options.headers || {});
    if (token) {
      headers.Authorization = "Bearer " + token;
    }
    const res = await fetch(api + path, Object.assign({}, options, { headers }));
    const type = res.headers.get("content-type") || "";
    const body = type.includes("json") ? await res.json() : null;
    if (!res.ok) {
      const err = body || { title: "HTTP " + res.status };
      throw err;
    }
    return body;
  }

  $("request-otp").addEventListener("click", async () => {
    loginError.hidden = true;
    try {
      await call("/api/v1/auth/otp/request", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phone: $("phone").value.trim() }),
      });
      $("otp-row").classList.remove("hidden");
      $("verify-otp").classList.remove("hidden");
    } catch (err) {
      showError(loginError, err);
    }
  });

  $("verify-otp").addEventListener("click", async () => {
    loginError.hidden = true;
    try {
      const body = await call("/api/v1/auth/otp/verify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phone: $("phone").value.trim(), code: $("otp").value.trim() }),
      });
      token = body.token;
      login.classList.add("hidden");
      app.classList.remove("hidden");
      $("logout").classList.remove("hidden");
      await loadCrops();
    } catch (err) {
      showError(loginError, err);
    }
  });

  $("logout").addEventListener("click", () => {
    token = null;
    audioBlob = null;
    app.classList.add("hidden");
    login.classList.remove("hidden");
    $("logout").classList.add("hidden");
  });

  document.querySelectorAll(".nav-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".nav-btn").forEach((b) => b.removeAttribute("aria-current"));
      btn.setAttribute("aria-current", "page");
      const faq = btn.dataset.view === "faq";
      $("view-faq").classList.toggle("hidden", !faq);
      $("view-case").classList.toggle("hidden", faq);
    });
  });

  async function loadCrops() {
    const crops = await call("/api/v1/crops");
    const select = $("crop");
    select.innerHTML = "";
    crops.forEach((crop) => {
      const opt = document.createElement("option");
      opt.value = crop.id;
      opt.textContent = crop.nameBn;
      select.appendChild(opt);
    });
  }

  const recordBtn = $("record");
  recordBtn.addEventListener("mousedown", startRec);
  recordBtn.addEventListener("touchstart", (e) => { e.preventDefault(); startRec(); });
  window.addEventListener("mouseup", stopRec);
  window.addEventListener("touchend", stopRec);

  async function startRec() {
    if (!navigator.mediaDevices || !window.isSecureContext) {
      $("rec-status").textContent = "মাইক্রোফোন পাওয়া যায়নি (HTTPS প্রয়োজন)।";
      return;
    }
    chunks = [];
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const mime = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
      ? "audio/webm;codecs=opus"
      : (MediaRecorder.isTypeSupported("audio/mp4") ? "audio/mp4" : "audio/webm");
    mediaRecorder = new MediaRecorder(stream, { mimeType: mime });
    mediaRecorder.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
    mediaRecorder.onstop = () => {
      audioBlob = new Blob(chunks, { type: mediaRecorder.mimeType });
      $("search").disabled = false;
      $("rec-status").textContent = "রেকর্ড প্রস্তুত";
      stream.getTracks().forEach((t) => t.stop());
    };
    mediaRecorder.start();
    recordBtn.classList.add("hot");
    $("rec-status").textContent = "রেকর্ড হচ্ছে…";
  }

  function stopRec() {
    if (mediaRecorder && mediaRecorder.state !== "inactive") {
      mediaRecorder.stop();
      recordBtn.classList.remove("hot");
    }
  }

  $("search").addEventListener("click", async () => {
    faqError.hidden = true;
    $("candidates").innerHTML = "";
    $("remedies").innerHTML = "";
    $("transcript").classList.add("hidden");
    if (!audioBlob) {
      return;
    }
    const data = new FormData();
    data.append("cropId", $("crop").value);
    data.append("audio", audioBlob, "clip.webm");
    try {
      const result = await call("/api/v1/faq/voice-search?preferred_language=bn", {
        method: "POST",
        body: data,
      });
      $("transcript").classList.remove("hidden");
      $("transcript").textContent = result.transcription
        ? "শোনা গেছে: " + result.transcription
        : "কোনো কথা শোনা যায়নি।";
      if (result.inconclusive || !result.candidates.length) {
        $("candidates").textContent = "জ্ঞানভাণ্ডারে মিল পাওয়া যায়নি। তালিকা থেকে রোগ বাছুন, অথবা ছবি দিয়ে মামলা করুন।";
        return;
      }
      result.candidates.forEach((c) => {
        const chip = document.createElement("button");
        chip.type = "button";
        chip.className = "chip";
        chip.textContent = c.nameBn;
        chip.addEventListener("click", () => confirmDisease(c, chip));
        $("candidates").appendChild(chip);
      });
    } catch (err) {
      showError(faqError, err);
    }
  });

  async function confirmDisease(candidate, chip) {
    document.querySelectorAll(".chip").forEach((el) => el.classList.remove("picked"));
    chip.classList.add("picked");
    $("remedies").innerHTML = "";
    try {
      const remedies = await call("/api/v1/diseases/" + candidate.diseaseId + "/remedies");
      if (!remedies.length) {
        $("remedies").textContent = "এই রোগে নিবন্ধিত প্রতিকার নেই।";
        return;
      }
      remedies.forEach((r) => {
        const box = document.createElement("article");
        box.className = "remedy";
        const steps = (r.stepsBn || []).map((s) => "<li>" + escapeHtml(s) + "</li>").join("");
        const dose = r.dosageBn ? "<p><strong>মাত্রা:</strong> " + escapeHtml(r.dosageBn) + "</p>" : "";
        const phi = r.phiDays != null ? "<p>PHI: " + r.phiDays + " দিন</p>" : "";
        box.innerHTML =
          "<h2>" + escapeHtml(r.titleBn) + "</h2>" +
          "<p>" + typeLabel(r.type) + "</p>" +
          "<ol>" + steps + "</ol>" + dose + phi;
        $("remedies").appendChild(box);
      });
    } catch (err) {
      showError(faqError, err);
    }
  }

  function typeLabel(type) {
    if (type === "CHEMICAL") return "রাসায়নিক নিয়ন্ত্রণ (নিবন্ধিত)";
    if (type === "ORGANIC") return "জৈব নিয়ন্ত্রণ (নিবন্ধিত)";
    if (type === "CULTURAL") return "চাষাবাদগত নিয়ন্ত্রণ (নিবন্ধিত)";
    if (type === "BIOLOGICAL") return "জৈবিক নিয়ন্ত্রণ (নিবন্ধিত)";
    return type || "";
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;");
  }
})();
