import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.URLEncoder;

public class BarberShopsScraper {
    private static final String API_KEY = "fd708dd5-5eeb-421b-929d-d22dd942f557";
    private static final String BASE_URL = "https://search-maps.yandex.ru/v1/";
    private static final String QUERY = "барбершопы в минске";
    private static final String OUTPUT_FILE = "barbershops.txt";
    private static final String EXCEL_FILE = "barbershops.xlsx";
    private static final int RESULTS_PER_REQUEST = 10;

    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();
        int offset = 0;
        boolean hasMoreResults = true;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE));
             Workbook workbook = new XSSFWorkbook()) { // Create Excel workbook
            Sheet sheet = workbook.createSheet("Barber Shops"); // Create Excel sheet

            while (hasMoreResults) {
                String encodedQuery = URLEncoder.encode(QUERY, "UTF-8");
                String url = BASE_URL + "?text=" + encodedQuery + "&type=biz&lang=ru_RU&apikey=" + API_KEY + "&results=" + RESULTS_PER_REQUEST + "&skip=" + offset;

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Unexpected code " + response);
                    }

                    String jsonData = response.body().string();
                    JsonElement jsonElement = JsonParser.parseString(jsonData);
                    JsonObject jsonObject = jsonElement.getAsJsonObject();
                    JsonArray features = jsonObject.getAsJsonArray("features");

                    if (features.size() == 0) {
                        hasMoreResults = false;
                    }

                    for (int i = 0; i < features.size(); i++) {
                        JsonObject featureObject = features.get(i).getAsJsonObject();
                        JsonObject properties = featureObject.getAsJsonObject("properties");
                        String name = properties.getAsJsonObject("CompanyMetaData").get("name").getAsString();
                        String address = properties.getAsJsonObject("CompanyMetaData").get("address").getAsString();

                        String phone = "";
                        JsonElement phonesElement = properties.getAsJsonObject("CompanyMetaData").get("Phones");
                        if (phonesElement != null && phonesElement.isJsonArray()) {
                            JsonArray phonesArray = phonesElement.getAsJsonArray();
                            if (phonesArray.size() > 0) {
                                JsonElement phoneElement = phonesArray.get(0);
                                if (phoneElement != null && phoneElement.isJsonObject()) {
                                    JsonElement formattedPhoneElement = phoneElement.getAsJsonObject().get("formatted");
                                    if (formattedPhoneElement != null && formattedPhoneElement.isJsonPrimitive()) {
                                        phone = formattedPhoneElement.getAsString();
                                    }
                                }
                            }
                        }

                        // Write to text file
                        writer.write("Название: " + name + "\n");
                        writer.write("Адрес: " + address + "\n");
                        writer.write("Телефон: " + phone + "\n");
                        writer.write("------------------------------------\n");

                        // Write to Excel file
                        Row row = sheet.createRow(offset + i);
                        row.createCell(0).setCellValue(name);
                        row.createCell(1).setCellValue(address);
                        row.createCell(2).setCellValue(phone);
                    }

                    offset += RESULTS_PER_REQUEST;
                } catch (IOException e) {
                    e.printStackTrace();
                    hasMoreResults = false;
                }
            }

            // Save Excel file
            try (FileOutputStream fos = new FileOutputStream(EXCEL_FILE)) {
                workbook.write(fos);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
