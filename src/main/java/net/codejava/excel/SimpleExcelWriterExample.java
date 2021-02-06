package net.codejava.excel;
 
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
 
/**
 * A very simple program that writes some data to an Excel file
 * using the Apache POI library.
 * @author www.codejava.net
 *
 */
public class SimpleExcelWriterExample {
	
	public static void write(List<List<Object>> bookData, String filepath) throws IOException {
        
       try (XSSFWorkbook workbook = new XSSFWorkbook();
    		FileOutputStream outputStream = new FileOutputStream(filepath)) {
           XSSFSheet sheet = workbook.createSheet("Sheet 1");

           int rowCount = 0;
           for(List<Object> dataRow : bookData) {
               Row row = sheet.createRow(++rowCount);
               
              int columnCount = 0;

              for (Object field : dataRow) {
                  Cell cell = row.createCell(++columnCount);
                  if (field instanceof String) {
                      cell.setCellValue((String) field);
                  } else if (field instanceof Integer) {
                      cell.setCellValue((Integer) field);
                  } else if (field instanceof Double) {
                      cell.setCellValue((Double) field);
                  }
              }
           }
           workbook.write(outputStream);
       }
	}
 
    public static void main(String[] args) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Java Books");
         
        Object[][] bookData = {
                {"Head First Java", "Kathy Serria", 79},
                {"Effective Java", "Joshua Bloch", 36},
                {"Clean Code", "Robert martin", 42},
                {"Thinking in Java", "Bruce Eckel", 35},
        };
 
        int rowCount = 0;
         
        for (Object[] aBook : bookData) {
            Row row = sheet.createRow(++rowCount);
             
            int columnCount = 0;
             
            for (Object field : aBook) {
                Cell cell = row.createCell(++columnCount);
                if (field instanceof String) {
                    cell.setCellValue((String) field);
                } else if (field instanceof Integer) {
                    cell.setCellValue((Integer) field);
                }
            }
             
        }
         
         
        try (FileOutputStream outputStream = new FileOutputStream("JavaBooks.xlsx")) {
            workbook.write(outputStream);
        }
    }
 
}