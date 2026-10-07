# Calculadoras útiles Chile

Sitio web estático (HTML + CSS + JavaScript, sin dependencias) con 10 calculadoras para el día a día en Chile:

1. **Sueldo líquido**: AFP, salud (Fonasa/Isapre), seguro de cesantía, gratificación legal e impuesto único.
2. **Finiquito**: indemnización por años de servicio, mes de aviso, vacaciones pendientes y días trabajados.
3. **Conversor de UF, UTM, dólar y euro**: valores del día desde [mindicador.cl](https://mindicador.cl).
4. **Costo de envío**: peso volumétrico, zona de destino y seguro.
5. **Cuánto cobrar**: valor hora y precio de proyecto para independientes (boleta o factura).
6. **IVA**: agregar o quitar el 19%.
7. **Boleta de honorarios**: bruto ↔ líquido con la retención vigente (15,25% en 2026).
8. **Simulador de crédito**: cuota mensual, intereses y costo total.
9. **Ahorro e interés compuesto**: proyección con aportes mensuales.
10. **Dividir la cuenta**: propina y monto por persona con redondeo.

## Uso

Abre `index.html` en el navegador o publícalo con GitHub Pages
(Settings → Pages → Deploy from branch → `main` / root).

Los parámetros legales (ingreso mínimo, topes imponibles, retención) están al inicio de `app.js`
en el objeto `PARAMS` para actualizarlos cada año.

> Los resultados son referenciales y no reemplazan asesoría legal, contable o tributaria.
