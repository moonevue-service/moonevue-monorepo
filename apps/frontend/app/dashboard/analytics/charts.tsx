"use client";

/**
 * Lightweight, dependency-free SVG charts tailored for the analytics dashboard.
 * Each component is responsive (measures its container) and supports hover.
 */

import { useEffect, useRef, useState } from "react";

/* ------------------------------------------------------------------ */
/* Shared palette                                                      */
/* ------------------------------------------------------------------ */

export const CHART = {
  ink: "#171717",
  inkSoft: "#525252",
  grid: "#e5e5e5",
  axis: "#a3a3a3",
  green: "#16a34a",
  greenSoft: "rgba(22,163,74,0.14)",
  amber: "#d97706",
  red: "#dc2626",
  blue: "#2563eb",
  blueSoft: "rgba(37,99,235,0.14)",
  grossFill: "rgba(82,82,82,0.10)",
  grossStroke: "#a3a3a3",
  netFill: "rgba(23,23,23,0.16)",
  netStroke: "#171717",
};

/* ------------------------------------------------------------------ */
/* Width measuring hook                                                */
/* ------------------------------------------------------------------ */

function useMeasuredWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      const w = entries[0]?.contentRect.width ?? 0;
      setWidth(w);
    });
    ro.observe(el);
    setWidth(el.clientWidth);
    return () => ro.disconnect();
  }, []);
  return [ref, width] as const;
}

/* ------------------------------------------------------------------ */
/* Sparkline                                                           */
/* ------------------------------------------------------------------ */

export function Sparkline({
  values,
  color = CHART.ink,
  width = 120,
  height = 34,
  fill = true,
}: {
  values: number[];
  color?: string;
  width?: number;
  height?: number;
  fill?: boolean;
}) {
  if (!values.length) return null;
  const max = Math.max(...values, 0);
  const min = Math.min(...values, 0);
  const span = max - min || 1;
  const stepX = values.length > 1 ? width / (values.length - 1) : 0;
  const y = (v: number) => height - 2 - ((v - min) / span) * (height - 4);
  const pts = values.map((v, i) => `${i * stepX},${y(v)}`);
  const line = `M ${pts.join(" L ")}`;
  const area = `${line} L ${width},${height} L 0,${height} Z`;
  const gid = `spark-${Math.random().toString(36).slice(2)}`;
  return (
    <svg width={width} height={height} style={{ display: "block" }}>
      <defs>
        <linearGradient id={gid} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.22} />
          <stop offset="100%" stopColor={color} stopOpacity={0} />
        </linearGradient>
      </defs>
      {fill && <path d={area} fill={`url(#${gid})`} stroke="none" />}
      <path
        d={line}
        fill="none"
        stroke={color}
        strokeWidth={1.8}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </svg>
  );
}

/* ------------------------------------------------------------------ */
/* Revenue trend (area + line + moving average + forecast)             */
/* ------------------------------------------------------------------ */

export interface TrendDatum {
  label: string;
  gross: number;
  net: number;
}

export function RevenueTrendChart({
  data,
  forecast = [],
  movingAvg,
  height = 300,
  formatValue,
  formatTooltip,
}: {
  data: TrendDatum[];
  /** Future gross values plotted as a dashed continuation. */
  forecast?: { label: string; value: number }[];
  /** Moving-average series aligned with `data` (nullable items skipped). */
  movingAvg?: (number | null)[];
  height?: number;
  formatValue: (v: number) => string;
  formatTooltip?: (d: TrendDatum, index: number) => React.ReactNode;
}) {
  const [ref, width] = useMeasuredWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);

  const pad = { top: 16, right: 16, bottom: 26, left: 58 };
  const w = Math.max(width, 280);
  const innerW = w - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;

  const all = [
    ...data.map((d) => d.gross),
    ...data.map((d) => d.net),
    ...forecast.map((f) => f.value),
  ];
  const maxV = Math.max(...all, 0) || 1;

  const total = data.length + forecast.length;
  const x = (i: number) =>
    pad.left + (total <= 1 ? innerW / 2 : (i / (total - 1)) * innerW);
  const y = (v: number) => pad.top + innerH - (v / maxV) * innerH;

  const linePath = (vals: number[], startIndex = 0) =>
    vals
      .map((v, i) => `${i === 0 ? "M" : "L"} ${x(startIndex + i)} ${y(v)}`)
      .join(" ");

  const grossLine = linePath(data.map((d) => d.gross));
  const grossArea =
    data.length > 0
      ? `${grossLine} L ${x(data.length - 1)} ${y(0)} L ${x(0)} ${y(0)} Z`
      : "";
  const netLine = linePath(data.map((d) => d.net));
  const netArea =
    data.length > 0
      ? `${netLine} L ${x(data.length - 1)} ${y(0)} L ${x(0)} ${y(0)} Z`
      : "";

  const forecastLine =
    forecast.length > 0 && data.length > 0
      ? `M ${x(data.length - 1)} ${y(data[data.length - 1].gross)} ` +
        forecast
          .map((f, i) => `L ${x(data.length + i)} ${y(f.value)}`)
          .join(" ")
      : "";

  const maPath =
    movingAvg && movingAvg.length
      ? movingAvg
          .map((v, i) => (v == null ? null : `${x(i)} ${y(v)}`))
          .filter(Boolean)
          .map((p, i) => `${i === 0 ? "M" : "L"} ${p}`)
          .join(" ")
      : "";

  const yTicks = [0, 0.25, 0.5, 0.75, 1];
  const xLabelEvery = Math.ceil(total / 7) || 1;

  return (
    <div ref={ref} style={{ width: "100%", position: "relative" }}>
      <svg width={w} height={height} role="img" style={{ display: "block" }}>
        <defs>
          <linearGradient id="trend-gross" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={CHART.inkSoft} stopOpacity={0.16} />
            <stop offset="100%" stopColor={CHART.inkSoft} stopOpacity={0} />
          </linearGradient>
          <linearGradient id="trend-net" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={CHART.ink} stopOpacity={0.22} />
            <stop offset="100%" stopColor={CHART.ink} stopOpacity={0.02} />
          </linearGradient>
        </defs>

        {/* gridlines + y labels */}
        {yTicks.map((t) => {
          const yy = pad.top + innerH - t * innerH;
          return (
            <g key={t}>
              <line
                x1={pad.left}
                y1={yy}
                x2={w - pad.right}
                y2={yy}
                stroke={CHART.grid}
                strokeWidth={1}
              />
              <text
                x={pad.left - 8}
                y={yy + 4}
                textAnchor="end"
                fontSize={10.5}
                fill={CHART.axis}
              >
                {formatValue(maxV * t)}
              </text>
            </g>
          );
        })}

        {/* forecast region highlight */}
        {forecast.length > 0 && data.length > 0 && (
          <>
            <rect
              x={x(data.length - 1)}
              y={pad.top}
              width={Math.max(0, x(total - 1) - x(data.length - 1))}
              height={innerH}
              fill="rgba(22,163,74,0.07)"
            />
            <text
              x={(x(data.length - 1) + x(total - 1)) / 2}
              y={pad.top + 11}
              textAnchor="middle"
              fontSize={10}
              fontWeight={600}
              fill={CHART.green}
            >
              Projeção
            </text>
          </>
        )}

        {/* areas */}
        {grossArea && <path d={grossArea} fill="url(#trend-gross)" />}
        {netArea && <path d={netArea} fill="url(#trend-net)" />}

        {/* lines */}
        {grossLine && (
          <path
            d={grossLine}
            fill="none"
            stroke={CHART.grossStroke}
            strokeWidth={1.6}
            strokeLinejoin="round"
          />
        )}
        {netLine && (
          <path
            d={netLine}
            fill="none"
            stroke={CHART.netStroke}
            strokeWidth={2.2}
            strokeLinejoin="round"
          />
        )}
        {maPath && (
          <path
            d={maPath}
            fill="none"
            stroke={CHART.blue}
            strokeWidth={1.6}
            strokeDasharray="2 3"
            strokeLinejoin="round"
          />
        )}
        {forecastLine && (
          <path
            d={forecastLine}
            fill="none"
            stroke={CHART.green}
            strokeWidth={2}
            strokeDasharray="5 4"
            strokeLinejoin="round"
          />
        )}

        {/* forecast points */}
        {forecast.map((f, i) => (
          <circle
            key={`fc-${i}`}
            cx={x(data.length + i)}
            cy={y(f.value)}
            r={2.5}
            fill={CHART.green}
          />
        ))}

        {/* x labels */}
        {data.map((d, i) =>
          i % xLabelEvery === 0 ? (
            <text
              key={d.label + i}
              x={x(i)}
              y={height - 8}
              textAnchor="middle"
              fontSize={10}
              fill={CHART.axis}
            >
              {d.label}
            </text>
          ) : null,
        )}
        {forecast.map((f, i) => (
          <text
            key={`fl-${i}`}
            x={x(data.length + i)}
            y={height - 8}
            textAnchor="middle"
            fontSize={10}
            fill={CHART.green}
          >
            {f.label}
          </text>
        ))}

        {/* hover guide + dots */}
        {hover != null && hover < data.length && (
          <g>
            <line
              x1={x(hover)}
              y1={pad.top}
              x2={x(hover)}
              y2={pad.top + innerH}
              stroke={CHART.axis}
              strokeWidth={1}
              strokeDasharray="3 3"
            />
            <circle
              cx={x(hover)}
              cy={y(data[hover].gross)}
              r={3.5}
              fill="#fff"
              stroke={CHART.grossStroke}
              strokeWidth={2}
            />
            <circle
              cx={x(hover)}
              cy={y(data[hover].net)}
              r={3.5}
              fill="#fff"
              stroke={CHART.netStroke}
              strokeWidth={2}
            />
          </g>
        )}

        {/* hover hit areas */}
        {data.map((d, i) => (
          <rect
            key={`hit-${i}`}
            x={x(i) - innerW / Math.max(total - 1, 1) / 2}
            y={pad.top}
            width={innerW / Math.max(total - 1, 1) || innerW}
            height={innerH}
            fill="transparent"
            onMouseEnter={() => setHover(i)}
            onMouseLeave={() => setHover((h) => (h === i ? null : h))}
          />
        ))}
      </svg>

      {hover != null && hover < data.length && (
        <div
          style={{
            position: "absolute",
            left: Math.min(Math.max(x(hover), 80), w - 80),
            top: 4,
            transform: "translateX(-50%)",
            background: "rgba(23,23,23,0.92)",
            color: "#fff",
            padding: "6px 10px",
            borderRadius: 8,
            fontSize: 12,
            lineHeight: 1.5,
            pointerEvents: "none",
            whiteSpace: "nowrap",
            boxShadow: "0 4px 16px rgba(0,0,0,0.2)",
            zIndex: 2,
          }}
        >
          {formatTooltip ? (
            formatTooltip(data[hover], hover)
          ) : (
            <>
              <strong>{data[hover].label}</strong>
              <br /> Bruto: {formatValue(data[hover].gross)}
              <br /> Líquido: {formatValue(data[hover].net)}
            </>
          )}
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Volume bars                                                         */
/* ------------------------------------------------------------------ */

export function VolumeBarChart({
  data,
  height = 300,
  color = CHART.ink,
}: {
  data: { label: string; value: number }[];
  height?: number;
  color?: string;
}) {
  const [ref, width] = useMeasuredWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);

  const pad = { top: 16, right: 16, bottom: 26, left: 44 };
  const w = Math.max(width, 280);
  const innerW = w - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;
  const maxV = Math.max(...data.map((d) => d.value), 0) || 1;
  const slot = innerW / Math.max(data.length, 1);
  const barW = Math.max(2, Math.min(28, slot * 0.62));
  const yTicks = [0, 0.5, 1];
  const xLabelEvery = Math.ceil(data.length / 8) || 1;

  return (
    <div ref={ref} style={{ width: "100%", position: "relative" }}>
      <svg width={w} height={height} style={{ display: "block" }}>
        {yTicks.map((t) => {
          const yy = pad.top + innerH - t * innerH;
          return (
            <g key={t}>
              <line
                x1={pad.left}
                y1={yy}
                x2={w - pad.right}
                y2={yy}
                stroke={CHART.grid}
                strokeWidth={1}
              />
              <text
                x={pad.left - 8}
                y={yy + 4}
                textAnchor="end"
                fontSize={10.5}
                fill={CHART.axis}
              >
                {Math.round(maxV * t)}
              </text>
            </g>
          );
        })}
        {data.map((d, i) => {
          const h = (d.value / maxV) * innerH;
          const cx = pad.left + slot * i + slot / 2;
          return (
            <g key={d.label + i}>
              <rect
                x={cx - barW / 2}
                y={pad.top + innerH - h}
                width={barW}
                height={Math.max(0, h)}
                rx={3}
                fill={hover === i ? CHART.ink : color}
                opacity={hover == null || hover === i ? 1 : 0.55}
                onMouseEnter={() => setHover(i)}
                onMouseLeave={() => setHover((p) => (p === i ? null : p))}
              />
              {i % xLabelEvery === 0 && (
                <text
                  x={cx}
                  y={height - 8}
                  textAnchor="middle"
                  fontSize={10}
                  fill={CHART.axis}
                >
                  {d.label}
                </text>
              )}
            </g>
          );
        })}
      </svg>
      {hover != null && (
        <div
          style={{
            position: "absolute",
            left: Math.min(
              Math.max(pad.left + slot * hover + slot / 2, 60),
              w - 60,
            ),
            top: 4,
            transform: "translateX(-50%)",
            background: "rgba(23,23,23,0.92)",
            color: "#fff",
            padding: "5px 9px",
            borderRadius: 8,
            fontSize: 12,
            pointerEvents: "none",
            whiteSpace: "nowrap",
            zIndex: 2,
          }}
        >
          <strong>{data[hover].label}</strong> · {data[hover].value}
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Donut                                                               */
/* ------------------------------------------------------------------ */

export interface DonutSlice {
  label: string;
  value: number;
  color: string;
}

export function DonutChart({
  data,
  size = 180,
  thickness = 26,
  centerTop,
  centerBottom,
}: {
  data: DonutSlice[];
  size?: number;
  thickness?: number;
  centerTop?: string;
  centerBottom?: string;
}) {
  const total = data.reduce((a, d) => a + d.value, 0);
  const r = (size - thickness) / 2;
  const cx = size / 2;
  const cy = size / 2;
  const circ = 2 * Math.PI * r;
  let offset = 0;

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <circle
        cx={cx}
        cy={cy}
        r={r}
        fill="none"
        stroke={CHART.grid}
        strokeWidth={thickness}
      />
      {total > 0 &&
        data.map((d, i) => {
          const frac = d.value / total;
          const dash = frac * circ;
          const el = (
            <circle
              key={i}
              cx={cx}
              cy={cy}
              r={r}
              fill="none"
              stroke={d.color}
              strokeWidth={thickness}
              strokeDasharray={`${dash} ${circ - dash}`}
              strokeDashoffset={-offset}
              transform={`rotate(-90 ${cx} ${cy})`}
              strokeLinecap="butt"
            />
          );
          offset += dash;
          return el;
        })}
      {centerTop && (
        <text
          x={cx}
          y={cy - 2}
          textAnchor="middle"
          fontSize={20}
          fontWeight={700}
          fill={CHART.ink}
        >
          {centerTop}
        </text>
      )}
      {centerBottom && (
        <text
          x={cx}
          y={cy + 16}
          textAnchor="middle"
          fontSize={11}
          fill={CHART.axis}
        >
          {centerBottom}
        </text>
      )}
    </svg>
  );
}

/* ------------------------------------------------------------------ */
/* Gauge (semicircle)                                                  */
/* ------------------------------------------------------------------ */

export function ScoreGauge({
  value,
  size = 200,
  label,
  color,
}: {
  value: number; // 0..100
  size?: number;
  label?: string;
  color?: string;
}) {
  const v = Math.max(0, Math.min(100, value));
  const stroke = 16;
  const r = (size - stroke) / 2;
  const cx = size / 2;
  const cy = size / 2;
  const circ = Math.PI * r; // semicircle length
  const dash = (v / 100) * circ;
  const auto = v >= 75 ? CHART.green : v >= 50 ? CHART.amber : CHART.red;
  const c = color ?? auto;
  const height = size / 2 + 24;
  return (
    <svg width={size} height={height} viewBox={`0 0 ${size} ${height}`}>
      <path
        d={`M ${stroke / 2} ${cy} A ${r} ${r} 0 0 1 ${size - stroke / 2} ${cy}`}
        fill="none"
        stroke={CHART.grid}
        strokeWidth={stroke}
        strokeLinecap="round"
      />
      <path
        d={`M ${stroke / 2} ${cy} A ${r} ${r} 0 0 1 ${size - stroke / 2} ${cy}`}
        fill="none"
        stroke={c}
        strokeWidth={stroke}
        strokeLinecap="round"
        strokeDasharray={`${dash} ${circ - dash}`}
      />
      <text
        x={cx}
        y={cy - 4}
        textAnchor="middle"
        fontSize={34}
        fontWeight={800}
        fill={CHART.ink}
      >
        {Math.round(v)}
      </text>
      {label && (
        <text
          x={cx}
          y={cy + 16}
          textAnchor="middle"
          fontSize={12}
          fill={CHART.axis}
        >
          {label}
        </text>
      )}
    </svg>
  );
}

/* ------------------------------------------------------------------ */
/* Horizontal funnel bar                                               */
/* ------------------------------------------------------------------ */

export function FunnelBar({
  stages,
}: {
  stages: { label: string; value: number; color: string }[];
}) {
  const max = Math.max(...stages.map((s) => s.value), 1);
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      {stages.map((s, i) => {
        const pct = (s.value / max) * 100;
        const conv =
          i === 0 ? 100 : (s.value / (stages[i - 1].value || 1)) * 100;
        return (
          <div key={s.label}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: 12,
                marginBottom: 2,
              }}
            >
              <span style={{ color: CHART.inkSoft }}>{s.label}</span>
              <span style={{ color: CHART.axis }}>
                {s.value.toLocaleString("pt-BR")}
                {i > 0 && ` · ${conv.toFixed(1)}%`}
              </span>
            </div>
            <div
              style={{
                background: CHART.grid,
                borderRadius: 6,
                height: 18,
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  width: `${Math.max(pct, 1)}%`,
                  height: "100%",
                  background: s.color,
                  borderRadius: 6,
                  transition: "width .3s ease",
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
