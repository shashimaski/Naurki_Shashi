package com.adi.naukri.automation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ResumeRenamer}.
 *
 * <p>Covers both the original "append _yyyy-MM-dd" fallback and the smart
 * in-place date replacement added 2026-07-16 to match Naukri's own filename
 * convention (e.g., "Arpitha S 15.07.2026 yahoo.pdf").</p>
 *
 * Author: Adikarthik Gupta C B
 */
class ResumeRenamerTest {
    @TempDir Path tmp;
    final ResumeRenamer r = new ResumeRenamer();

    // ── Original fallback behaviour (no date pattern in filename) ─────────

    @Test
    void appends_date_and_preserves_extension() throws Exception {
        Path src = Files.writeString(tmp.resolve("JohnDoe_Resume.pdf"), "pdf");
        Path out = r.rename(src, LocalDate.of(2026, 7, 14));
        assertEquals("JohnDoe_Resume_2026-07-14.pdf", out.getFileName().toString());
        assertTrue(Files.exists(out));
        assertFalse(Files.exists(src));
    }

    @Test
    void handles_docx() throws Exception {
        Path src = Files.writeString(tmp.resolve("cv.docx"), "docx");
        Path out = r.rename(src, LocalDate.of(2026, 1, 5));
        assertEquals("cv_2026-01-05.docx", out.getFileName().toString());
    }

    @Test
    void collision_gets_suffix() throws Exception {
        Files.writeString(tmp.resolve("cv_2026-07-14.pdf"), "existing");
        Path src = Files.writeString(tmp.resolve("cv.pdf"), "new");
        Path out = r.rename(src, LocalDate.of(2026, 7, 14));
        assertEquals("cv_2026-07-14-1.pdf", out.getFileName().toString());
    }

    // ── Smart in-place date preservation (2026-07-16) ─────────────────────

    @Test
    void preserves_dd_dot_mm_dot_yyyy_pattern() throws Exception {
        Path src = Files.writeString(tmp.resolve("Arpitha S 15.07.2026 yahoo.pdf"), "pdf");
        Path out = r.rename(src, LocalDate.of(2026, 7, 16));
        assertEquals("Arpitha S 16.07.2026 yahoo.pdf", out.getFileName().toString());
    }

    @Test
    void preserves_dd_dash_mm_dash_yyyy_pattern() throws Exception {
        Path src = Files.writeString(tmp.resolve("resume 05-01-2026.pdf"), "pdf");
        Path out = r.rename(src, LocalDate.of(2026, 3, 9));
        assertEquals("resume 09-03-2026.pdf", out.getFileName().toString());
    }

    @Test
    void preserves_yyyy_dash_mm_dash_dd_pattern() throws Exception {
        Path src = Files.writeString(tmp.resolve("resume-2026-07-14.pdf"), "pdf");
        Path out = r.rename(src, LocalDate.of(2026, 7, 16));
        assertEquals("resume-2026-07-16.pdf", out.getFileName().toString());
    }

    @Test
    void unit_computeRenameString_ddmmyyyy_dot() {
        String actual = ResumeRenamer.renameString(
                "Arpitha S 15.07.2026 yahoo.pdf", LocalDate.of(2026, 7, 16));
        assertEquals("Arpitha S 16.07.2026 yahoo.pdf", actual);
    }

    @Test
    void unit_computeRenameString_no_date_fallback_appends() {
        String actual = ResumeRenamer.renameString(
                "MyResume.pdf", LocalDate.of(2026, 7, 16));
        assertEquals("MyResume_2026-07-16.pdf", actual);
    }

    @Test
    void unit_computeRenameString_yyyy_mm_dd() {
        String actual = ResumeRenamer.renameString(
                "resume-2025-12-31.pdf", LocalDate.of(2026, 1, 1));
        assertEquals("resume-2026-01-01.pdf", actual);
    }
}
