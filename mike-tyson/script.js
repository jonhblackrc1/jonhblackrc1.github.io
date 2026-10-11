/* ==========================================================
   IRON MIKE — script compartido
   ========================================================== */
(function () {
  "use strict";
  document.documentElement.classList.remove("no-js");
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- Menú hamburguesa ---------- */
  var toggle = document.querySelector(".nav-toggle");
  var links = document.getElementById("nav-links");
  if (toggle && links) {
    toggle.addEventListener("click", function () {
      var open = toggle.getAttribute("aria-expanded") === "true";
      toggle.setAttribute("aria-expanded", String(!open));
      links.classList.toggle("open", !open);
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && links.classList.contains("open")) {
        toggle.setAttribute("aria-expanded", "false");
        links.classList.remove("open");
        toggle.focus();
      }
    });
  }

  /* ---------- Campana de round ---------- */
  var bell = document.querySelector(".bell");
  if (bell) {
    bell.addEventListener("click", function () {
      bell.classList.remove("ring");
      void bell.offsetWidth;
      bell.classList.add("ring");
      try {
        var Ctx = window.AudioContext || window.webkitAudioContext;
        var ctx = new Ctx();
        [0, 0.28, 0.56].forEach(function (t) {
          var osc = ctx.createOscillator();
          var gain = ctx.createGain();
          osc.type = "triangle";
          osc.frequency.value = 1320;
          gain.gain.setValueAtTime(0.0001, ctx.currentTime + t);
          gain.gain.exponentialRampToValueAtTime(0.35, ctx.currentTime + t + 0.01);
          gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + t + 0.25);
          osc.connect(gain).connect(ctx.destination);
          osc.start(ctx.currentTime + t);
          osc.stop(ctx.currentTime + t + 0.26);
        });
      } catch (err) { /* sin audio, solo animación */ }
    });
  }

  /* ---------- Aparición al hacer scroll + contadores ---------- */
  function animateCount(el) {
    var target = parseFloat(el.getAttribute("data-count"));
    var prefix = el.getAttribute("data-prefix") || "";
    var suffix = el.getAttribute("data-suffix") || "";
    if (reduceMotion) { el.textContent = prefix + target + suffix; return; }
    var start = null, dur = 1300;
    function step(ts) {
      if (!start) start = ts;
      var p = Math.min((ts - start) / dur, 1);
      var eased = 1 - Math.pow(1 - p, 3);
      el.textContent = prefix + Math.round(target * eased) + suffix;
      if (p < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  }

  var revealEls = document.querySelectorAll(".reveal");
  var countEls = document.querySelectorAll("[data-count]");
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var el = entry.target;
        if (el.hasAttribute("data-count")) animateCount(el);
        else el.classList.add("in");
        io.unobserve(el);
      });
    }, { threshold: 0.15 });
    revealEls.forEach(function (el) { io.observe(el); });
    countEls.forEach(function (el) { io.observe(el); });
  } else {
    revealEls.forEach(function (el) { el.classList.add("in"); });
    countEls.forEach(animateCount);
  }

  /* ---------- Línea de tiempo interactiva ---------- */
  document.querySelectorAll(".tl-toggle").forEach(function (btn) {
    var panel = document.getElementById(btn.getAttribute("aria-controls"));
    panel.hidden = true; // sin JS el contenido queda visible
    btn.addEventListener("click", function () {
      var open = btn.getAttribute("aria-expanded") === "true";
      btn.setAttribute("aria-expanded", String(!open));
      panel.hidden = open;
      btn.textContent = open ? "+ Ver más" : "− Ver menos";
    });
  });

  /* ---------- Filtros de combates ---------- */
  var filterBtns = document.querySelectorAll(".filter-btn");
  var fights = document.querySelectorAll(".fight");
  var emptyMsg = document.querySelector(".empty-msg");
  filterBtns.forEach(function (btn) {
    btn.addEventListener("click", function () {
      var f = btn.getAttribute("data-filter");
      filterBtns.forEach(function (b) { b.setAttribute("aria-pressed", String(b === btn)); });
      var shown = 0;
      fights.forEach(function (card) {
        var tags = (card.getAttribute("data-tags") || "").split(" ");
        var visible = f === "todos" || tags.indexOf(f) !== -1;
        card.hidden = !visible;
        if (visible) shown++;
      });
      if (emptyMsg) emptyMsg.hidden = shown !== 0;
      var status = document.getElementById("filter-status");
      if (status) status.textContent = shown + " combates mostrados";
    });
  });

  /* ---------- Gráficos de patrimonio (Chart.js) ---------- */
  if (!window.Chart) {
    document.querySelectorAll(".chart-box").forEach(function (box) {
      box.innerHTML = '<p class="note">No se pudo cargar el gráfico. Revisa tu conexión y recarga la página.</p>';
    });
  } else {
    Chart.defaults.color = "#b9b3a8";
    Chart.defaults.font.family = "Inter, system-ui, sans-serif";
    Chart.defaults.borderColor = "rgba(242,237,227,.1)";

    var trend = document.getElementById("chart-trend");
    if (trend) {
      new Chart(trend, {
        type: "line",
        data: {
          labels: ["1985", "1988", "1990", "1992", "1995", "1997", "2001", "2003", "2006", "2010", "2015", "2020", "2024", "2026"],
          datasets: [{
            label: "Patrimonio neto estimado (millones USD, ilustrativo)",
            data: [0, 50, 70, 40, 25, 60, 30, -23, -10, 0, 3, 10, 20, 25],
            borderColor: "#d4af37",
            backgroundColor: "rgba(212,175,55,.15)",
            fill: true,
            tension: 0.3,
            pointRadius: 5,
            pointBackgroundColor: function (ctx) { return ctx.raw < 0 ? "#c8102e" : "#d4af37"; }
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          animation: reduceMotion ? false : { duration: 900 },
          plugins: {
            legend: { labels: { color: "#f2ede3" } },
            tooltip: {
              callbacks: {
                label: function (c) { return " ~" + c.raw + " M USD (estimación ilustrativa)"; },
                afterLabel: function (c) {
                  var notes = {
                    "1988": "Bolsa de ~20 M ante Spinks",
                    "1992": "Condena y prisión",
                    "1997": "Bolsas de ~30 M ante Holyfield",
                    "2003": "Declara bancarrota (~23 M en deudas)",
                    "2024": "Bolsa reportada de ~20 M ante Jake Paul"
                  };
                  return notes[c.label] || "";
                }
              }
            }
          },
          scales: {
            y: { title: { display: true, text: "Millones USD (aprox.)" } }
          }
        }
      });
    }

    var purses = document.getElementById("chart-purses");
    if (purses) {
      new Chart(purses, {
        type: "bar",
        data: {
          labels: ["Spinks 1988", "Bruno II 1996", "Holyfield I 1996", "Holyfield II 1997", "Lewis 2002", "Jake Paul 2024"],
          datasets: [{
            label: "Bolsa reportada (millones USD, aprox.)",
            data: [20, 30, 30, 30, 17.5, 20],
            backgroundColor: ["#c8102e", "#c8102e", "#c8102e", "#c8102e", "#c8102e", "#d4af37"],
            borderRadius: 3
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          animation: reduceMotion ? false : { duration: 900 },
          plugins: { legend: { labels: { color: "#f2ede3" } } },
          scales: { y: { beginAtZero: true, title: { display: true, text: "Millones USD (aprox.)" } } }
        }
      });
    }
  }

  /* ---------- Quiz ---------- */
  var quiz = document.getElementById("quiz");
  if (quiz) {
    var questions = [
      { q: "¿A qué edad se convirtió Tyson en el campeón de peso pesado más joven de la historia?",
        a: ["18 años", "20 años", "22 años", "24 años"], c: 1,
        e: "Tenía 20 años y 4 meses cuando venció a Trevor Berbick en 1986." },
      { q: "¿Quién fue el entrenador y mentor que lo adoptó como hijo?",
        a: ["Kevin Rooney", "Don King", "Cus D'Amato", "Angelo Dundee"], c: 2,
        e: "Cus D'Amato lo acogió en Catskill, Nueva York, y llegó a ser su tutor legal." },
      { q: "¿Cuánto duró la pelea contra Michael Spinks en 1988?",
        a: ["91 segundos", "3 asaltos", "45 segundos", "5 minutos"], c: 0,
        e: "Tyson noqueó a Spinks en apenas 91 segundos del primer asalto." },
      { q: "¿Dónde sufrió su primera derrota profesional, ante Buster Douglas?",
        a: ["Las Vegas", "Atlantic City", "Londres", "Tokio"], c: 3,
        e: "Fue en el Tokyo Dome, el 11 de febrero de 1990, una de las mayores sorpresas del deporte." },
      { q: "¿Qué animales exóticos fueron famosos por vivir en su mansión?",
        a: ["Leones", "Tigres de Bengala", "Osos", "Cocodrilos"], c: 1,
        e: "Tuvo tigres de Bengala como mascotas; también cría palomas desde niño." }
    ];
    var idx = 0, score = 0;
    var qEl = quiz.querySelector(".quiz-q");
    var optsEl = quiz.querySelector(".options");
    var fbEl = quiz.querySelector(".quiz-feedback");
    var nextBtn = quiz.querySelector(".quiz-next");
    var countEl = quiz.querySelector(".quiz-count");
    var scoreEl = quiz.querySelector(".quiz-score");
    var barEl = quiz.querySelector(".quiz-progress span");
    var bodyEl = quiz.querySelector(".quiz-body");
    var resultEl = quiz.querySelector(".quiz-result");

    function render() {
      var item = questions[idx];
      countEl.textContent = "Round " + (idx + 1) + " / " + questions.length;
      scoreEl.textContent = "Puntos: " + score;
      barEl.style.width = (idx / questions.length * 100) + "%";
      qEl.textContent = item.q;
      fbEl.textContent = "";
      nextBtn.hidden = true;
      optsEl.innerHTML = "";
      item.a.forEach(function (text, i) {
        var b = document.createElement("button");
        b.type = "button";
        b.className = "option";
        b.textContent = text;
        b.addEventListener("click", function () { answer(i, b); });
        optsEl.appendChild(b);
      });
      optsEl.querySelector(".option").focus({ preventScroll: true });
    }

    function answer(i, btn) {
      var item = questions[idx];
      var all = optsEl.querySelectorAll(".option");
      all.forEach(function (b) { b.disabled = true; });
      all[item.c].classList.add("correct");
      if (i === item.c) {
        score++;
        fbEl.textContent = "¡Directo a la mandíbula! " + item.e;
      } else {
        btn.classList.add("wrong");
        fbEl.textContent = "Fallaste el golpe. " + item.e;
      }
      scoreEl.textContent = "Puntos: " + score;
      nextBtn.textContent = idx === questions.length - 1 ? "Ver resultado" : "Siguiente round";
      nextBtn.hidden = false;
      nextBtn.focus();
    }

    function finish() {
      barEl.style.width = "100%";
      bodyEl.hidden = true;
      resultEl.hidden = false;
      resultEl.querySelector(".score").textContent = score + "/" + questions.length;
      var msgs = [
        "Knockout en el primer asalto… ¡vuelve a recorrer el sitio y pide la revancha!",
        "Te faltó guardia. Repasa la historia y vuelve al ring.",
        "Aguantaste de pie, pero perdiste por puntos.",
        "Buena pelea: ganaste por decisión dividida.",
        "Casi perfecto: victoria por nocaut técnico.",
        "¡Campeón indiscutido! Sabes tanto como Cus D'Amato."
      ];
      resultEl.querySelector(".verdict").textContent = msgs[score];
      resultEl.querySelector(".quiz-restart").focus();
    }

    nextBtn.addEventListener("click", function () {
      if (idx < questions.length - 1) { idx++; render(); } else { finish(); }
    });
    resultEl.querySelector(".quiz-restart").addEventListener("click", function () {
      idx = 0; score = 0; resultEl.hidden = true; bodyEl.hidden = false; render();
    });

    // primer render sin robar el foco
    (function () {
      var item = questions[0];
      countEl.textContent = "Round 1 / " + questions.length;
      scoreEl.textContent = "Puntos: 0";
      qEl.textContent = item.q;
      item.a.forEach(function (text, i) {
        var b = document.createElement("button");
        b.type = "button";
        b.className = "option";
        b.textContent = text;
        b.addEventListener("click", function () { answer(i, b); });
        optsEl.appendChild(b);
      });
    })();
  }
})();
