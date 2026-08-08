// ── Types ─────────────────────────────────────────────────────────────────────

export interface ProgressRingProps {
  value: number;
  total: number;
  size?: number;
  strokeWidth?: number;
}

// ── Component ─────────────────────────────────────────────────────────────────

export function ProgressRing({
  value,
  total,
  size = 120,
  strokeWidth = 10
}: ProgressRingProps): JSX.Element {
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const progress = total > 0 ? Math.min(value / total, 1) : 0;
  const dashOffset = circumference * (1 - progress);
  const center = size / 2;
  const gradientId = "progress-ring-gradient";

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      aria-label={`${value} of ${total} complete`}
      className="block"
    >
      <defs>
        <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#22d3ee" />
          <stop offset="100%" stopColor="#a855f7" />
        </linearGradient>
      </defs>

      {/* Track circle */}
      <circle
        cx={center}
        cy={center}
        r={radius}
        fill="none"
        stroke="rgba(255,255,255,0.08)"
        strokeWidth={strokeWidth}
      />

      {/* Progress arc */}
      <circle
        cx={center}
        cy={center}
        r={radius}
        fill="none"
        stroke={`url(#${gradientId})`}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeDasharray={circumference}
        strokeDashoffset={dashOffset}
        transform={`rotate(-90 ${center} ${center})`}
        style={{ transition: "stroke-dashoffset 0.4s ease" }}
      />

      {/* Centre label */}
      <text
        x={center}
        y={center}
        textAnchor="middle"
        dominantBaseline="central"
        className="fill-text-primary font-head text-sm"
        fontSize={size * 0.14}
        fill="#e6edf3"
        fontFamily="JetBrains Mono, ui-monospace, monospace"
      >
        {value}/{total}
      </text>
    </svg>
  );
}
