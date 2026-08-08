package com.adi.naukri.excel;

/**
 * One parsed row from the accounts Excel.
 *
 * @param rowNumber 1-based row number in the sheet (for user-facing errors).
 * @param email     account email (required, validated).
 * @param name      account holder's name — used to locate the local resume file
 *                  (glob {@code <resumeFolder>/<name>*.pdf}). May be {@code null}
 *                  if the sheet has no Name column; UI falls back to the email
 *                  local-part in that case.
 * @param remarks   optional free-text remarks column.
 * @param valid     {@code true} if the row is well-formed.
 * @param error     validation error message when {@code !valid}; else {@code null}.
 *
 * Author: Adikarthik Gupta C B
 */
public record ParsedEmailRow(
        int rowNumber,
        String email,
        String name,
        String remarks,
        boolean valid,
        String error) {}
