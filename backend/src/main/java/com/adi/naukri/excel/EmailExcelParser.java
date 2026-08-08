package com.adi.naukri.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class EmailExcelParser {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final String SHEET = "Emails";
    private static final String HDR_EMAIL = "email";
    private static final String HDR_NAME = "name";
    private static final String HDR_REMARKS = "remarks";

    public List<ParsedEmailRow> parse(InputStream xlsx) {
        try (Workbook wb = new XSSFWorkbook(xlsx)) {
            Sheet sh = wb.getSheet(SHEET);
            if (sh == null) throw new ExcelFormatException("Expected sheet '" + SHEET + "'");
            Row header = sh.getRow(0);
            if (header == null) throw new ExcelFormatException("Header row missing");

            int emailCol = -1, nameCol = -1, remarksCol = -1;
            for (Cell c : header) {
                String v = c.getStringCellValue().trim().toLowerCase(Locale.ROOT);
                if (v.equals(HDR_EMAIL))   emailCol   = c.getColumnIndex();
                if (v.equals(HDR_NAME))    nameCol    = c.getColumnIndex();
                if (v.equals(HDR_REMARKS)) remarksCol = c.getColumnIndex();
            }
            if (emailCol < 0) throw new ExcelFormatException("Missing required header 'email'");

            List<ParsedEmailRow> out = new ArrayList<>();
            for (int r = 1; r <= sh.getLastRowNum(); r++) {
                Row row = sh.getRow(r);
                if (row == null) continue;
                String email   = cell(row, emailCol);
                String name    = nameCol    < 0 ? null : cell(row, nameCol);
                String remarks = remarksCol < 0 ? null : cell(row, remarksCol);
                if (email == null || email.isBlank()) {
                    out.add(new ParsedEmailRow(r + 1, "", name, remarks, false, "empty"));
                } else if (!EMAIL.matcher(email).matches()) {
                    out.add(new ParsedEmailRow(r + 1, email, name, remarks, false, "invalid format"));
                } else {
                    out.add(new ParsedEmailRow(r + 1, email, name, remarks, true, null));
                }
            }
            return out;
        } catch (ExcelFormatException e) { throw e;
        } catch (Exception e) { throw new ExcelFormatException("Could not read xlsx: " + e.getMessage()); }
    }

    private static String cell(Row r, int i) {
        Cell c = r.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        c.setCellType(CellType.STRING);
        String v = c.getStringCellValue();
        return v == null ? null : v.trim();
    }
}
