package com.adi.naukri.automation;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renames a downloaded resume to reflect today's date.
 *
 * <p><strong>Smart date preservation (2026-07-16):</strong> if the original
 * filename already contains a date pattern (either {@code DD.MM.YYYY}, {@code DD-MM-YYYY},
 * {@code DD/MM/YYYY}, or {@code YYYY-MM-DD}), the date is replaced <em>in place</em>
 * using the same separator, preserving the surrounding filename structure. This
 * matches Naukri's convention where a resume named
 * {@code "Arpitha S 15.07.2026 yahoo.pdf"} should become
 * {@code "Arpitha S 16.07.2026 yahoo.pdf"} on the next run, not
 * {@code "Arpitha S 15.07.2026 yahoo_2026-07-16.pdf"}.</p>
 *
 * <p>If no date pattern is found, the original behaviour is preserved: append
 * {@code _yyyy-MM-dd} before the extension. Collision suffix ({@code -1}, {@code -2}, ...)
 * is applied when the target path already exists.</p>
 *
 * Author: Adikarthik Gupta C B
 */
@Component
public class ResumeRenamer {

    private static final DateTimeFormatter APPEND_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Priority-ordered — earlier patterns are attempted first.
    // Group 1: day, Group 2: separator, Group 3: month, Group 4: year — for DMY variants.
    // For YMD variant: Group 1: year, Group 2: sep, Group 3: month, Group 4: day.
    private static final Pattern DMY = Pattern.compile("\\b(\\d{1,2})([.\\-/])(\\d{1,2})\\2(\\d{4})\\b");
    private static final Pattern YMD = Pattern.compile("\\b(\\d{4})([.\\-/])(\\d{1,2})\\2(\\d{1,2})\\b");

    public Path rename(Path src, LocalDate today) throws IOException {
        String name = src.getFileName().toString();
        Path parent = src.getParent();

        String renamed = renameString(name, today);
        Path candidate = parent.resolve(renamed);

        // Collision suffix: if renamed == original OR the file with the new name
        // already exists, add -1, -2, ... before the extension.
        int n = 1;
        while (Files.exists(candidate) && !candidate.equals(src)) {
            candidate = parent.resolve(withSuffix(renamed, "-" + n));
            n++;
        }

        // If the renamed name is identical to the source name (no date found AND
        // fallback produced same string, which shouldn't happen, but still), keep the source.
        if (candidate.equals(src)) {
            return src;
        }
        return Files.move(src, candidate, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Compute the target filename for {@code name} given today's date.
     * Package-private for testing.
     */
    static String renameString(String name, LocalDate today) {
        String dd  = String.format("%02d", today.getDayOfMonth());
        String mm  = String.format("%02d", today.getMonthValue());
        String yyyy = String.valueOf(today.getYear());

        // Try DD<sep>MM<sep>YYYY first (Naukri's own convention: "15.07.2026").
        Matcher m1 = DMY.matcher(name);
        if (m1.find()) {
            String sep = m1.group(2);
            String replacement = dd + sep + mm + sep + yyyy;
            return m1.replaceFirst(Matcher.quoteReplacement(replacement));
        }

        // Then YYYY<sep>MM<sep>DD.
        Matcher m2 = YMD.matcher(name);
        if (m2.find()) {
            String sep = m2.group(2);
            String replacement = yyyy + sep + mm + sep + dd;
            return m2.replaceFirst(Matcher.quoteReplacement(replacement));
        }

        // Fallback: append _yyyy-MM-dd before the extension.
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        String ext  = dot < 0 ? ""   : name.substring(dot);
        return stem + "_" + today.format(APPEND_FMT) + ext;
    }

    /** Insert {@code suffix} just before the file extension (if any). */
    private static String withSuffix(String name, String suffix) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return name + suffix;
        return name.substring(0, dot) + suffix + name.substring(dot);
    }
}
