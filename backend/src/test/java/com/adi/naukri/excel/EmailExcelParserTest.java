package com.adi.naukri.excel;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EmailExcelParserTest {
    @TempDir Path tmp;
    EmailExcelParser parser = new EmailExcelParser();

    @Test
    void parses_valid_rows() throws Exception {
        Path f = tmp.resolve("v.xlsx");
        Fixtures.write(f, "Emails", new String[][]{
            {"email", "remarks"},
            {"a@x.com", "primary"},
            {"b@x.com", null}
        });
        List<ParsedEmailRow> rows = parser.parse(Files.newInputStream(f));
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(ParsedEmailRow::valid));
        assertEquals("a@x.com", rows.get(0).email());
    }

    @Test
    void flags_invalid_email() throws Exception {
        Path f = tmp.resolve("i.xlsx");
        Fixtures.write(f, "Emails", new String[][]{
            {"email"},
            {"not-an-email"},
            {""},
            {"ok@x.com"}
        });
        List<ParsedEmailRow> rows = parser.parse(Files.newInputStream(f));
        assertEquals(3, rows.size());
        assertFalse(rows.get(0).valid());
        assertFalse(rows.get(1).valid());
        assertTrue (rows.get(2).valid());
    }

    @Test
    void rejects_missing_sheet() throws Exception {
        Path f = tmp.resolve("bad.xlsx");
        Fixtures.write(f, "Other", new String[][]{{"email"},{"a@x.com"}});
        assertThrows(ExcelFormatException.class, () -> parser.parse(Files.newInputStream(f)));
    }

    @Test
    void rejects_missing_header() throws Exception {
        Path f = tmp.resolve("h.xlsx");
        Fixtures.write(f, "Emails", new String[][]{{"address"},{"a@x.com"}});
        assertThrows(ExcelFormatException.class, () -> parser.parse(Files.newInputStream(f)));
    }
}
