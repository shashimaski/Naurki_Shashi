/**
 * Relative time formatter for job history display.
 *
 * Created by: Adikarthik Gupta C B
 */

/**
 * Returns a human-readable relative time string such as "just now", "2m ago", "1h ago".
 * Input is a Unix timestamp in milliseconds (Date.now()).
 */
export function relativeTime(timestampMs: number): string {
  const deltaSeconds = Math.floor((Date.now() - timestampMs) / 1000);

  if (deltaSeconds < 10) return "just now";
  if (deltaSeconds < 60) return `${deltaSeconds}s ago`;

  const deltaMinutes = Math.floor(deltaSeconds / 60);
  if (deltaMinutes < 60) return `${deltaMinutes}m ago`;

  const deltaHours = Math.floor(deltaMinutes / 60);
  if (deltaHours < 24) return `${deltaHours}h ago`;

  const deltaDays = Math.floor(deltaHours / 24);
  return `${deltaDays}d ago`;
}
