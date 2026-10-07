"use strict";

// ---------- Parámetros (referenciales, se actualizan cada año) ----------
const PARAMS = {
  imm: 539000,          // Ingreso mínimo mensual
  topeAfpUF: 87.8,      // Tope imponible AFP / salud (UF)
  topeCesUF: 131.9,     // Tope imponible seguro de cesantía (UF)
  topeIndemUF: 90,      // Tope remuneración para indemnizaciones (UF)
  iva: 0.19,
  retencionHonorarios: 0.1525,
};

// Valores de respaldo si no se puede consultar la API
const indicadores = { uf: 39500, utm: 69500, dolar: 950, euro: 1100, clp: 1 };

// Tabla impuesto único de segunda categoría (mensual, en UTM)
const TRAMOS = [
  { hasta: 13.5, factor: 0,     rebaja: 0 },
  { hasta: 30,   factor: 0.04,  rebaja: 0.54 },
  { hasta: 50,   factor: 0.08,  rebaja: 1.74 },
  { hasta: 70,   factor: 0.135, rebaja: 4.49 },
  { hasta: 90,   factor: 0.23,  rebaja: 11.14 },
  { hasta: 120,  factor: 0.304, rebaja: 17.8 },
  { hasta: 310,  factor: 0.35,  rebaja: 23.32 },
  { hasta: Infinity, factor: 0.40, rebaja: 38.82 },
];

// ---------- Utilidades ----------
const clp = (n) => new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 }).format(Math.round(n || 0));
const num = (n, d = 2) => new Intl.NumberFormat("es-CL", { maximumFractionDigits: d }).format(n || 0);
const val = (form, name) => {
  const el = form.elements[name];
  if (!el) return 0;
  if (el.type === "number") return parseFloat(el.value) || 0;
  return el.value;
};

function table(rows) {
  return "<table>" + rows.map(([label, value, cls = ""]) =>
    `<tr class="${cls}"><td>${label}</td><td>${value}</td></tr>`).join("") + "</table>";
}
function big(label, value) {
  return `<p class="big">${value}</p><p class="big-label">${label}</p>`;
}

function impuestoUnico(baseTributable, utm) {
  const enUTM = baseTributable / utm;
  const t = TRAMOS.find((t) => enUTM <= t.hasta);
  return Math.max(0, (baseTributable * t.factor) - (t.rebaja * utm));
}

// ---------- Calculadoras ----------
const calculators = {
  sueldo(f) {
    const uf = indicadores.uf, utm = indicadores.utm;
    f.querySelector(".isapre-only").classList.toggle("hidden", val(f, "salud") !== "isapre");

    const base = val(f, "base");
    const topeGrat = (4.75 * PARAMS.imm) / 12;
    const grat = val(f, "grat") === "legal" ? Math.min(base * 0.25, topeGrat) : 0;
    const imponible = base + grat + val(f, "otrosImp");
    const impTope = Math.min(imponible, PARAMS.topeAfpUF * uf);

    const tasaAfp = 10 + parseFloat(val(f, "afp"));
    const afp = impTope * tasaAfp / 100;
    const salud7 = impTope * 0.07;
    const salud = val(f, "salud") === "isapre" ? Math.max(salud7, val(f, "planUF") * uf) : salud7;
    const ces = val(f, "contrato") === "indef" ? Math.min(imponible, PARAMS.topeCesUF * uf) * 0.006 : 0;

    // La rebaja de salud para el impuesto se limita al 7% legal
    const tributable = Math.max(0, imponible - afp - Math.min(salud, salud7) - ces);
    const impuesto = impuestoUnico(tributable, utm);
    const noImp = val(f, "noImp");
    const liquido = imponible - afp - salud - ces - impuesto + noImp;

    return big("Sueldo líquido estimado", clp(liquido)) + table([
      ["Sueldo base", clp(base)],
      ["Gratificación", clp(grat)],
      ["Otros imponibles", clp(val(f, "otrosImp"))],
      ["<b>Total imponible</b>", clp(imponible)],
      [`AFP (${num(tasaAfp)}%)`, "−" + clp(afp), "neg"],
      ["Salud", "−" + clp(salud), "neg"],
      ["Seguro de cesantía", "−" + clp(ces), "neg"],
      ["Impuesto único", "−" + clp(impuesto), "neg"],
      ["No imponibles", clp(noImp)],
      ["Líquido a pagar", clp(liquido), "total"],
    ]) + `<p class="note">Topes imponibles: ${PARAMS.topeAfpUF} UF (AFP/salud) y ${PARAMS.topeCesUF} UF (cesantía). Ingreso mínimo usado: ${clp(PARAMS.imm)}.</p>`;
  },

  finiquito(f) {
    const ini = f.elements.inicio.valueAsDate, fin = f.elements.termino.valueAsDate;
    if (!ini || !fin || fin <= ini) return `<p class="warn">Ingresa fechas válidas (el término debe ser posterior al inicio).</p>`;

    const sueldo = val(f, "sueldo");
    const baseIndem = Math.min(sueldo, PARAMS.topeIndemUF * indicadores.uf);

    let meses = (fin.getUTCFullYear() - ini.getUTCFullYear()) * 12 + (fin.getUTCMonth() - ini.getUTCMonth());
    if (fin.getUTCDate() < ini.getUTCDate()) meses--;
    const anios = Math.floor(meses / 12), resto = meses % 12;

    const es161 = val(f, "causal") === "161";
    let aniosIndem = 0;
    if (es161 && anios >= 1) aniosIndem = Math.min(11, anios + (resto >= 6 ? 1 : 0));
    const indem = aniosIndem * baseIndem;
    const aviso = es161 && val(f, "aviso") === "no" ? baseIndem : 0;

    // Días hábiles → aprox. días corridos (factor 7/5) × sueldo diario
    const diario = sueldo / 30;
    const vacHabiles = val(f, "vac");
    const vacaciones = vacHabiles * 1.4 * diario;
    const diasMes = val(f, "diasMes") * diario;
    const total = indem + aviso + vacaciones + diasMes;

    return big("Total finiquito estimado", clp(total)) + table([
      ["Antigüedad", `${anios} años y ${resto} meses`],
      [`Indemnización por años de servicio (${aniosIndem} años)`, clp(indem)],
      ["Indemnización sustitutiva del aviso previo", clp(aviso)],
      [`Vacaciones pendientes (${num(vacHabiles)} días hábiles)`, clp(vacaciones)],
      ["Remuneración días trabajados", clp(diasMes)],
      ["Total", clp(total), "total"],
    ]) + `<p class="note">Indemnización con tope de ${PARAMS.topeIndemUF} UF mensuales y 11 años. Se acumulan 1,25 días hábiles de vacaciones por mes trabajado (15 al año). Las vacaciones se aproximan convirtiendo días hábiles a corridos.</p>`;
  },

  conversor(f) {
    ["uf", "utm", "dolar", "euro"].forEach((k) => {
      const v = parseFloat(f.elements[k].value);
      if (v > 0) indicadores[k] = v;
    });
    const monto = val(f, "monto");
    const desde = val(f, "desde"), hacia = val(f, "hacia");
    const resultado = (monto * indicadores[desde]) / indicadores[hacia];
    const etiqueta = { uf: "UF", utm: "UTM", dolar: "USD", euro: "EUR", clp: "CLP" };
    const fmt = (n, k) => k === "clp" ? clp(n) : `${num(n, 4)} ${etiqueta[k]}`;
    return big(`${fmt(monto, desde)} equivalen a`, fmt(resultado, hacia)) + table([
      ["1 UF", clp(indicadores.uf)],
      ["1 UTM", clp(indicadores.utm)],
      ["1 USD", clp(indicadores.dolar)],
      ["1 EUR", clp(indicadores.euro)],
    ]);
  },

  envio(f) {
    const vol = (val(f, "largo") * val(f, "ancho") * val(f, "alto")) / val(f, "factor");
    const real = val(f, "peso");
    const cobrable = Math.max(1, Math.ceil(Math.max(vol, real)));
    const zona = parseFloat(val(f, "zona"));
    const flete = (val(f, "tarifaBase") + (cobrable - 1) * val(f, "porKg")) * zona;
    const seguro = val(f, "declarado") * 0.01;
    const total = flete + seguro;
    return big("Costo de envío estimado", clp(total)) + table([
      ["Peso real", `${num(real)} kg`],
      ["Peso volumétrico", `${num(vol)} kg`],
      ["Peso cobrable (redondeado)", `${cobrable} kg`],
      ["Flete", clp(flete)],
      ["Seguro (1% del valor declarado)", clp(seguro)],
      ["Total", clp(total), "total"],
    ]) + `<p class="note">Los couriers cobran el mayor entre peso real y volumétrico (L×A×H / factor). Ajusta tarifa base y por kg con los precios de tu empresa de envío.</p>`;
  },

  cobrar(f) {
    const semanas = Math.max(1, 52 - val(f, "vacaciones"));
    const horasMes = (val(f, "horas") * semanas) / 12;
    const boleta = val(f, "doc") === "boleta";
    const deseado = val(f, "deseado");
    // Con boleta, el cliente retiene; hay que cobrar más bruto para recibir el líquido deseado
    const ingresoNecesario = (boleta ? deseado / (1 - PARAMS.retencionHonorarios) : deseado) + val(f, "gastos");
    const hora = (ingresoNecesario / horasMes) * (1 + val(f, "margen") / 100);
    const proyecto = hora * val(f, "horasProy");
    const rows = [
      ["Horas facturables al mes", num(horasMes, 1)],
      ["Ingreso bruto mensual necesario", clp(ingresoNecesario)],
      ["Valor hora", clp(hora)],
      ["Valor día (8 h)", clp(hora * 8)],
      [`Proyecto (${num(val(f, "horasProy"), 1)} h)`, clp(proyecto), "total"],
    ];
    if (!boleta) rows.push(["Proyecto + IVA (lo que paga el cliente)", clp(proyecto * (1 + PARAMS.iva)), "total"]);
    return big(boleta ? "Valor hora bruto (boleta)" : "Valor hora neto (sin IVA)", clp(hora)) + table(rows) +
      `<p class="note">Considera que no todas las horas de trabajo son facturables (ventas, administración, aprendizaje).</p>`;
  },

  iva(f) {
    const m = val(f, "monto");
    const neto = val(f, "modo") === "neto" ? m : m / (1 + PARAMS.iva);
    const iva = neto * PARAMS.iva;
    return big("Total con IVA", clp(neto + iva)) + table([
      ["Neto", clp(neto)],
      ["IVA (19%)", clp(iva)],
      ["Total", clp(neto + iva), "total"],
    ]);
  },

  honorarios(f) {
    const m = val(f, "monto"), tasa = val(f, "tasa") / 100;
    const bruto = val(f, "modo") === "bruto" ? m : m / (1 - tasa);
    const ret = bruto * tasa;
    return big("Líquido a recibir", clp(bruto - ret)) + table([
      ["Monto bruto de la boleta", clp(bruto)],
      [`Retención (${num(tasa * 100)}%)`, "−" + clp(ret), "neg"],
      ["Líquido", clp(bruto - ret), "total"],
    ]) + `<p class="note">La retención aumenta gradualmente: 15,25% en 2026, 16% en 2027 y 17% desde 2028. Se usa para pagar impuestos y cotizaciones en la Operación Renta.</p>`;
  },

  credito(f) {
    const P = val(f, "monto"), i = val(f, "tasa") / 100, n = Math.max(1, Math.round(val(f, "plazo")));
    const cuota = i === 0 ? P / n : (P * i) / (1 - Math.pow(1 + i, -n));
    const total = cuota * n + val(f, "gastos");
    const tasaAnual = (Math.pow(1 + i, 12) - 1) * 100;
    return big("Cuota mensual", clp(cuota)) + table([
      ["Monto solicitado", clp(P)],
      ["Tasa anual equivalente", `${num(tasaAnual)}%`],
      ["Total de intereses", clp(cuota * n - P)],
      ["Gastos y seguros", clp(val(f, "gastos"))],
      ["Costo total del crédito", clp(total), "total"],
    ]) + `<p class="note">Compara siempre la CAE (Carga Anual Equivalente) que entrega el banco, que incluye todos los costos.</p>`;
  },

  ahorro(f) {
    const inicial = val(f, "inicial"), aporte = val(f, "aporte");
    const r = Math.pow(1 + val(f, "tasa") / 100, 1 / 12) - 1;
    const anios = Math.max(1, Math.round(val(f, "anios")));
    let saldo = inicial;
    const hitos = [];
    for (let m = 1; m <= anios * 12; m++) {
      saldo = saldo * (1 + r) + aporte;
      if (m % 12 === 0 && (m / 12 <= 3 || (m / 12) % 5 === 0 || m / 12 === anios)) hitos.push([`Año ${m / 12}`, clp(saldo)]);
    }
    const aportado = inicial + aporte * anios * 12;
    return big(`Saldo final en ${anios} años`, clp(saldo)) + table([
      ...hitos,
      ["Total aportado", clp(aportado)],
      ["Ganancia por intereses", clp(saldo - aportado), "total"],
    ]);
  },

  cuenta(f) {
    const total = val(f, "total"), p = val(f, "propina") / 100, n = Math.max(1, Math.round(val(f, "personas")));
    const red = parseFloat(val(f, "redondeo"));
    const conPropina = total * (1 + p);
    const porPersona = Math.ceil(conPropina / n / red) * red;
    return big("Cada persona paga", clp(porPersona)) + table([
      ["Consumo", clp(total)],
      [`Propina (${num(p * 100)}%)`, clp(total * p)],
      ["Total con propina", clp(conPropina)],
      ["Recaudado al redondear", clp(porPersona * n)],
      ["Sobrante", clp(porPersona * n - conPropina), "total"],
    ]);
  },
};

// ---------- Interfaz ----------
const panels = [...document.querySelectorAll(".panel")];
const tabs = document.getElementById("tabs");

function render(panel) {
  const form = panel.querySelector("form");
  panel.querySelector("[data-out]").innerHTML = calculators[panel.id](form);
}

function activate(id, push = true) {
  const target = panels.find((p) => p.id === id) || panels[0];
  panels.forEach((p) => p.classList.toggle("active", p === target));
  tabs.querySelectorAll("button").forEach((b) => b.setAttribute("aria-selected", b.dataset.id === target.id));
  if (push) history.replaceState(null, "", "#" + target.id);
  render(target);
}

panels.forEach((panel) => {
  const b = document.createElement("button");
  b.type = "button";
  b.role = "tab";
  b.dataset.id = panel.id;
  b.textContent = panel.dataset.title;
  b.addEventListener("click", () => activate(panel.id));
  tabs.appendChild(b);

  const form = panel.querySelector("form");
  form.addEventListener("input", () => render(panel));
  form.addEventListener("submit", (e) => e.preventDefault());
});

// Fechas por defecto del finiquito: hace 3 años y medio hasta hoy
(function () {
  const f = document.querySelector("#finiquito form");
  const hoy = new Date();
  const inicio = new Date(hoy);
  inicio.setMonth(inicio.getMonth() - 42);
  f.elements.termino.value = hoy.toISOString().slice(0, 10);
  f.elements.inicio.value = inicio.toISOString().slice(0, 10);
})();

function pintarIndicadores(fuente) {
  ["uf", "utm", "dolar", "euro"].forEach((k) => {
    document.getElementById("ind-" + k).textContent = clp(indicadores[k]).replace(/\$\s?/, "$");
    document.querySelector(`#conversor form`).elements[k].value = indicadores[k];
  });
  document.getElementById("ind-fuente").textContent = fuente;
}

async function cargarIndicadores() {
  pintarIndicadores("Valores referenciales");
  try {
    const res = await fetch("https://mindicador.cl/api");
    if (!res.ok) throw new Error(res.status);
    const d = await res.json();
    ["uf", "utm", "dolar", "euro"].forEach((k) => { if (d[k] && d[k].valor) indicadores[k] = d[k].valor; });
    const fecha = d.uf && d.uf.fecha ? new Date(d.uf.fecha).toLocaleDateString("es-CL") : "";
    pintarIndicadores(`Fuente: mindicador.cl ${fecha}`);
  } catch (e) {
    pintarIndicadores("Sin conexión a mindicador.cl: usando valores referenciales (editables en el conversor)");
  }
  activate(location.hash.slice(1), false);
}

activate(location.hash.slice(1), false);
cargarIndicadores();
