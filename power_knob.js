// === Power Knob Component ===
// Ручка-потенциометр для отображения мощности в блоке Температуры
// Вызов: PowerKnob.init('power-knob-container'); PowerKnob.update(3000);

const PowerKnob = (function() {
    // Настройки
    const MIN_POWER = 100;
    const MAX_POWER = 7000;
    const STEP_MAJOR = 1000;
    const STEP_MINOR = 100;
    const START_ANGLE = 135;
    const SWEEP_ANGLE = 270;
    const CX = 110, CY = 110;
    const TICK_OUTER_R = 68;
    const TICK_MAJOR_LEN = 10;
    const TICK_MINOR_LEN = 5;
    const LABEL_R = 82;
    const KNOB_R = 50;

    let currentPower = 3000;
    let initialized = false;

    // Создание SVG-компонента внутри контейнера
    function init(containerId) {
        const container = document.getElementById(containerId);
        if (!container) return;

        container.innerHTML = '';
        container.style.cssText = 'display:flex;justify-content:center;padding:4px 0 2px;';

        const svg = createSVG();
        container.appendChild(svg);
        createTicks(svg);
        createKnurling(svg);
        initialized = true;
        update(currentPower);
    }

    function createSVG() {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 220 220');
        svg.style.cssText = 'width:100%;max-width:200px;height:auto;';

        // Defs
        svg.innerHTML = `
            <defs>
                <radialGradient id="pkMetal" cx="45%" cy="40%" r="55%">
                    <stop offset="0%" style="stop-color:#3a3f4a"/>
                    <stop offset="40%" style="stop-color:#2a2f38"/>
                    <stop offset="80%" style="stop-color:#1a1e26"/>
                    <stop offset="100%" style="stop-color:#14181f"/>
                </radialGradient>
                <radialGradient id="pkHighlight" cx="38%" cy="35%" r="40%">
                    <stop offset="0%" style="stop-color:rgba(255,255,255,0.12)"/>
                    <stop offset="100%" style="stop-color:rgba(255,255,255,0)"/>
                </radialGradient>
                <radialGradient id="pkEdge" cx="50%" cy="50%" r="50%">
                    <stop offset="85%" style="stop-color:rgba(255,255,255,0)"/>
                    <stop offset="95%" style="stop-color:rgba(255,255,255,0.04)"/>
                    <stop offset="100%" style="stop-color:rgba(0,0,0,0.3)"/>
                </radialGradient>
                <radialGradient id="pkGlow" cx="50%" cy="50%" r="50%">
                    <stop offset="0%" style="stop-color:rgba(0,240,255,0.06)"/>
                    <stop offset="100%" style="stop-color:rgba(0,240,255,0)"/>
                </radialGradient>
                <filter id="pkShadow"><feGaussianBlur in="SourceGraphic" stdDeviation="3"/></filter>
            </defs>
            <!-- Подложка -->
            <circle cx="${CX}" cy="${CY}" r="108" fill="url(#pkGlow)"/>
            <!-- Деления -->
            <g id="pkTicks"></g>
            <!-- Тень ручки -->
            <circle cx="${CX}" cy="${CY + 2}" r="${KNOB_R}" fill="rgba(0,0,0,0.4)" filter="url(#pkShadow)"/>
            <!-- Тело ручки -->
            <circle cx="${CX}" cy="${CY}" r="${KNOB_R}" fill="url(#pkMetal)" stroke="rgba(255,255,255,0.06)" stroke-width="1"/>
            <!-- Насечки -->
            <g id="pkKnurl" opacity="0.3"></g>
            <!-- Блик и скос -->
            <circle cx="${CX}" cy="${CY}" r="${KNOB_R}" fill="url(#pkHighlight)"/>
            <circle cx="${CX}" cy="${CY}" r="${KNOB_R}" fill="url(#pkEdge)"/>
            <!-- Втулка -->
            <circle cx="${CX}" cy="${CY}" r="11" fill="#1a1e26" stroke="rgba(255,255,255,0.08)" stroke-width="1"/>
            <circle cx="${CX}" cy="${CY}" r="7" fill="#0d1015" stroke="rgba(255,255,255,0.04)" stroke-width="0.5"/>
            <!-- Указатель -->
            <g id="pkIndicator">
                <line x1="${CX}" y1="${CY - KNOB_R + 8}" x2="${CX}" y2="${CY - KNOB_R + 22}" stroke="rgba(0,240,255,0.4)" stroke-width="3.5" stroke-linecap="round"/>
                <line x1="${CX}" y1="${CY - KNOB_R + 8}" x2="${CX}" y2="${CY - KNOB_R + 22}" stroke="rgba(255,255,255,0.9)" stroke-width="2" stroke-linecap="round"/>
            </g>
        `;
        return svg;
    }

    // Насечки на ручке
    function createKnurling(svg) {
        const g = svg.querySelector('#pkKnurl');
        if (!g) return;
        const ns = 'http://www.w3.org/2000/svg';
        const count = 60, innerR = KNOB_R - 6, outerR = KNOB_R - 1;
        for (let i = 0; i < count; i++) {
            const rad = (i / count) * 2 * Math.PI;
            const line = document.createElementNS(ns, 'line');
            line.setAttribute('x1', CX + innerR * Math.cos(rad));
            line.setAttribute('y1', CY + innerR * Math.sin(rad));
            line.setAttribute('x2', CX + outerR * Math.cos(rad));
            line.setAttribute('y2', CY + outerR * Math.sin(rad));
            line.setAttribute('stroke', 'rgba(255,255,255,0.5)');
            line.setAttribute('stroke-width', '0.5');
            g.appendChild(line);
        }
    }

    // Деления и подписи
    function createTicks(svg) {
        const g = svg.querySelector('#pkTicks');
        if (!g) return;
        const ns = 'http://www.w3.org/2000/svg';
        const totalSteps = (MAX_POWER - MIN_POWER) / STEP_MINOR;

        // Нулевая риска
        const zeroRad = START_ANGLE * Math.PI / 180;
        addTick(g, ns, zeroRad, TICK_MAJOR_LEN, '#ff8c00', 2);
        addLabel(g, ns, zeroRad, START_ANGLE, '0', '#ff8c00');

        // Основные деления
        for (let i = 0; i <= totalSteps; i++) {
            const power = MIN_POWER + i * STEP_MINOR;
            const frac = i / totalSteps;
            const angle = START_ANGLE + frac * SWEEP_ANGLE;
            const rad = angle * Math.PI / 180;
            const isMajor = (power % STEP_MAJOR === 0);

            addTick(g, ns, rad, isMajor ? TICK_MAJOR_LEN : TICK_MINOR_LEN,
                    isMajor ? '#ff8c00' : '#00f0ff', isMajor ? 2 : 0.8);

            if (isMajor) {
                addLabel(g, ns, rad, angle, (power / 1000).toString(), '#ff8c00');
            }
        }
    }

    function addTick(parent, ns, rad, len, color, width) {
        const line = document.createElementNS(ns, 'line');
        line.setAttribute('x1', CX + TICK_OUTER_R * Math.cos(rad));
        line.setAttribute('y1', CY + TICK_OUTER_R * Math.sin(rad));
        line.setAttribute('x2', CX + (TICK_OUTER_R - len) * Math.cos(rad));
        line.setAttribute('y2', CY + (TICK_OUTER_R - len) * Math.sin(rad));
        line.setAttribute('stroke', color);
        line.setAttribute('stroke-width', width);
        line.setAttribute('stroke-linecap', 'round');
        parent.appendChild(line);
    }

    function addLabel(parent, ns, rad, angle, text, color) {
        const lx = CX + LABEL_R * Math.cos(rad);
        const ly = CY + LABEL_R * Math.sin(rad);
        const el = document.createElementNS(ns, 'text');
        el.setAttribute('x', lx);
        el.setAttribute('y', ly);
        el.setAttribute('fill', color);
        el.setAttribute('font-family', "'Courier New', monospace");
        el.setAttribute('font-size', '9');
        el.setAttribute('font-weight', '600');
        el.setAttribute('text-anchor', 'middle');
        el.setAttribute('dominant-baseline', 'middle');
        const rot = (angle > 180 && angle < 360) ? (angle - 180) : angle;
        el.setAttribute('transform', 'rotate(' + rot + ', ' + lx + ', ' + ly + ')');
        el.textContent = text;
        parent.appendChild(el);
    }

    // Обновление положения указателя
    function update(power) {
        currentPower = power;
        if (!initialized) return;
        const clamped = Math.max(MIN_POWER, Math.min(MAX_POWER, power));
        const frac = (clamped - MIN_POWER) / (MAX_POWER - MIN_POWER);
        const angle = START_ANGLE + frac * SWEEP_ANGLE;

        const indicator = document.getElementById('pkIndicator');
        if (indicator) {
            indicator.setAttribute('transform', 'rotate(' + (angle + 90) + ', ' + CX + ', ' + CY + ')');
        }
    }

    return { init, update, get initialized() { return initialized; } };
})();
