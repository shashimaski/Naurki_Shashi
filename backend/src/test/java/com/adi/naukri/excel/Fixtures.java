package com.adi.naukri.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;
import java.nio.file.Path;

public class Fixtures {
    public static void write(Path out, String sheetName, String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(out.toFile())) {
            Sheet sh = wb.createSheet(sheetName);
            for (int r = 0; r < rows.length; r++) {
                Row row = sh.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(fos);
        }
    }
}
