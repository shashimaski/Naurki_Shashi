/**
 * ExcelDropzone — drag-and-drop / click file picker for xlsx uploads.
 *
 * Props:
 *   onParsed — called with the ParsedEmailRow[] returned by /api/parse-excel
 *
 * Behaviour:
 *   • Drag-and-drop OR click to pick .xlsx file.
 *   • Posts to /api/parse-excel via parseExcel() helper in api/rest.ts.
 *   • Renders a preview table of all rows.
 *   • Invalid rows are shown in a separate "Invalid rows" panel.
 *   • Emits onParsed(rows) after a successful parse.
 *   • Shows an inline error alert on API failure.
 *
 * Created by: Adikarthik Gupta C B
 */
import { useRef, useState, DragEvent, ChangeEvent } from "react";
import { parseExcel } from "../api/rest";
import type { ParsedEmailRow } from "../api/types";

interface Props {
  onParsed: (rows: ParsedEmailRow[]) => void;
}

export default function ExcelDropzone({ onParsed }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [rows, setRows] = useState<ParsedEmailRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function processFile(file: File) {
    setError(null);
    setLoading(true);
    try {
      const parsed = await parseExcel(file);
      setRows(parsed);
      onParsed(parsed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to parse file");
    } finally {
      setLoading(false);
    }
  }

  function handleDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (file) processFile(file);
  }

  function handleChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) processFile(file);
  }

  // Use a more robust approach: rely on ParsedEmailRow shape
  // The FE type doesn't have a `valid` field — we check by pattern or trust the BE
  // Since types.ts ParsedEmailRow has { email, name?, rowIndex } we display all rows
  // and split invalid by checking for email pattern
  const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const validRowsFiltered   = rows?.filter(r => EMAIL_RE.test(r.email)) ?? [];
  const invalidRowsFiltered = rows?.filter(r => !EMAIL_RE.test(r.email)) ?? [];

  return (
    <div className="flex flex-col gap-3">
      {/* Drop zone */}
      <div
        role="region"
        aria-label="Excel file drop zone"
        className={`border-2 border-dashed rounded-lg p-6 text-center cursor-pointer transition-colors ${
          dragging ? "border-accent bg-accent/10" : "border-white/20 hover:border-accent/50"
        }`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          className="hidden"
          onChange={handleChange}
        />
        {loading ? (
          <p className="text-text-muted text-sm">Parsing…</p>
        ) : (
          <>
            <p className="text-sm text-text-muted">
              Drag &amp; drop an <strong>.xlsx</strong> file here, or click to browse
            </p>
            <p className="text-xs text-text-muted mt-1">
              Expected columns: <code>email</code>, <code>remarks</code> (optional)
            </p>
          </>
        )}
      </div>

      {/* Error */}
      {error && (
        <p role="alert" className="text-red-400 text-sm">
          {error}
        </p>
      )}

      {/* Preview table */}
      {rows && rows.length > 0 && (
        <div className="flex flex-col gap-2">
          {validRowsFiltered.length > 0 && (
            <div>
              <h4 className="text-xs text-text-muted uppercase tracking-wider mb-1">
                Valid rows ({validRowsFiltered.length})
              </h4>
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="text-left border-b border-white/10">
                    <th className="py-1 pr-4 text-text-muted font-normal">Row</th>
                    <th className="py-1 pr-4 text-text-muted font-normal">Email</th>
                    {rows.some(r => r.name) && (
                      <th className="py-1 text-text-muted font-normal">Name</th>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {validRowsFiltered.map(r => (
                    <tr key={`valid-${r.rowIndex}`} className="border-b border-white/5">
                      <td className="py-1 pr-4 text-text-muted">{r.rowIndex}</td>
                      <td className="py-1 pr-4">{r.email}</td>
                      {rows.some(rr => rr.name) && (
                        <td className="py-1">{r.name ?? "—"}</td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {invalidRowsFiltered.length > 0 && (
            <div className="rounded-md bg-red-900/20 border border-red-500/30 p-3">
              <h4 className="text-xs text-red-400 uppercase tracking-wider mb-1">
                Invalid rows ({invalidRowsFiltered.length})
              </h4>
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="text-left border-b border-red-500/20">
                    <th className="py-1 pr-4 text-red-400/70 font-normal">Row</th>
                    <th className="py-1 text-red-400/70 font-normal">Email</th>
                  </tr>
                </thead>
                <tbody>
                  {invalidRowsFiltered.map(r => (
                    <tr key={`invalid-${r.rowIndex}`} className="border-b border-red-500/10">
                      <td className="py-1 pr-4 text-red-400/70">{r.rowIndex}</td>
                      <td className="py-1 text-red-300">{r.email || "(empty)"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
