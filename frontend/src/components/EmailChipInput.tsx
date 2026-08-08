/**
 * EmailChipInput — a tag/chip editor for (name, email) pairs.
 *
 * Props:
 *   value    — current list of {name, email} entries
 *   onChange — called with the new list whenever a chip is added or removed
 *
 * Behaviour:
 *   • User types a name into the "Name" field and an email into the "Email"
 *     field, then hits Enter (from either field) or clicks "Add" to commit
 *     as a chip.
 *   • Rejects invalid email format with an inline red error message.
 *   • Rejects blank name.
 *   • Rejects duplicate email silently.
 *   • Backspace on the empty email field removes the last chip.
 *
 * Created by: Adikarthik Gupta C B
 */
import { useState, useRef, KeyboardEvent } from "react";
import type { AccountInput } from "../api/types";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

interface Props {
  value: AccountInput[];
  onChange: (next: AccountInput[]) => void;
}

export default function EmailChipInput({ value, onChange }: Props) {
  const [nameDraft, setNameDraft]   = useState("");
  const [emailDraft, setEmailDraft] = useState("");
  const [error, setError]           = useState<string | null>(null);
  const emailInputRef = useRef<HTMLInputElement>(null);

  function tryCommit() {
    const name  = nameDraft.trim();
    const email = emailDraft.trim().replace(/,$/, "").trim();
    if (!name && !email) {
      setError(null);
      return;
    }
    if (!name) {
      setError("Name is required");
      return;
    }
    if (!email) {
      setError("Email is required");
      return;
    }
    if (!EMAIL_RE.test(email)) {
      setError(`"${email}" is not a valid email address`);
      return;
    }
    if (value.some(a => a.email === email)) {
      setNameDraft("");
      setEmailDraft("");
      setError(null);
      return;
    }
    onChange([...value, { name, email }]);
    setNameDraft("");
    setEmailDraft("");
    setError(null);
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      e.preventDefault();
      tryCommit();
      return;
    }
    if (e.key === "Backspace" && emailDraft === "" && nameDraft === "" && value.length > 0) {
      onChange(value.slice(0, -1));
    }
  }

  function removeChip(idx: number) {
    onChange(value.filter((_, i) => i !== idx));
  }

  return (
    <div className="flex flex-col gap-2">
      {/* ── Existing chips ── */}
      {value.length > 0 && (
        <div className="flex flex-wrap gap-1 items-center border border-white/10 rounded-md bg-surface px-2 py-2 min-h-[2.5rem]">
          {value.map((chip, i) => (
            <span
              key={`${chip.email}-${i}`}
              className="flex items-center gap-1 bg-accent/20 text-accent text-sm rounded px-2 py-0.5"
            >
              <span className="font-medium">{chip.name}</span>
              <span className="text-text-muted">&lt;{chip.email}&gt;</span>
              <button
                type="button"
                aria-label={`Remove ${chip.email}`}
                className="hover:text-red-400 leading-none"
                onClick={(e) => { e.stopPropagation(); removeChip(i); }}
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}

      {/* ── New-chip input row ── */}
      <div className="flex flex-col sm:flex-row gap-2">
        <input
          data-testid="chip-name-input"
          type="text"
          className="input flex-1"
          placeholder="Name (e.g. Arpitha S)"
          value={nameDraft}
          onChange={(e) => { setNameDraft(e.target.value); setError(null); }}
          onKeyDown={handleKeyDown}
        />
        <input
          ref={emailInputRef}
          data-testid="chip-input"
          type="text"
          className="input flex-1"
          placeholder="email@example.com"
          value={emailDraft}
          onChange={(e) => { setEmailDraft(e.target.value); setError(null); }}
          onKeyDown={handleKeyDown}
        />
        <button
          type="button"
          data-testid="chip-add"
          className="btn-secondary text-sm px-3"
          onClick={tryCommit}
        >
          Add
        </button>
      </div>

      {error && (
        <p role="alert" className="text-red-400 text-xs px-1">
          {error}
        </p>
      )}
    </div>
  );
}
