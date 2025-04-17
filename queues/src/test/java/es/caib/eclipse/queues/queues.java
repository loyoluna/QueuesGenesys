package es.caib.eclipse.queues;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class queues {

    private static final String BEARER_TOKEN = "token";
    private static final String QUEUES_URL = "https://api/queues";
    private static final String SCRIPT_URL = "https://api/scripts/";
    private static final String CSV_FILE = "colas_genesys.csv";  // Nombre del archivo CSV

    public static void main(String[] args) {
        try {
            obtenerColas();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void obtenerColas() throws Exception {
        int pageNumber = 1;
        int pageSize = 100;
        String sortOrder = "asc";
        String sortBy = "name";

        // Crear el archivo CSV e incluir los encabezados
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CSV_FILE))) {
            writer.write("ID_Cola,Nombre_Cola,División,ID_Script,Nombre_Script\n");

            while (true) {
                String urlConParametros = QUEUES_URL + "?pageNumber=" + pageNumber +
                        "&pageSize=" + pageSize +
                        "&sortOrder=" + sortOrder +
                        "&sortBy=" + sortBy;

                try (CloseableHttpClient client = HttpClients.createDefault()) {
                    HttpGet get = new HttpGet(urlConParametros);
                    get.setHeader("Authorization", "Bearer " + BEARER_TOKEN);

                    try (CloseableHttpResponse response = client.execute(get)) {
                        int statusCode = response.getCode();
                        if (statusCode != 200) {
                            String errorResponse = leerRespuesta(response);
                            System.out.println("Error: " + statusCode + " - " + errorResponse);
                            return;
                        }

                        String jsonResponse = leerRespuesta(response);
                        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                        JsonArray queues = jsonObject.getAsJsonArray("entities");

                        if (queues == null || queues.size() == 0) {
                            System.out.println("No se encontraron colas.");
                            return;
                        }

                        for (int i = 0; i < queues.size(); i++) {
                            JsonObject queue = queues.get(i).getAsJsonObject();
                            String idCola = queue.get("id").getAsString();
                            String nombreCola = queue.get("name").getAsString();

                            // Obtener el nombre de la división
                            JsonObject divisionObj = queue.has("division") && !queue.get("division").isJsonNull()
                                    ? queue.getAsJsonObject("division")
                                    : null;
                            String divisionNombre = (divisionObj != null && divisionObj.has("name")) 
                                    ? divisionObj.get("name").getAsString() 
                                    : "N/A";

                            // Procesar defaultScripts
                            JsonObject defaultScriptsObj = queue.has("defaultScripts") && !queue.get("defaultScripts").isJsonNull()
                                    ? queue.getAsJsonObject("defaultScripts")
                                    : null;

                            String scriptId = "N/A";
                            String scriptNombre = "N/A";

                            if (defaultScriptsObj != null) {
                                for (String key : defaultScriptsObj.keySet()) {
                                    JsonObject scriptObj = defaultScriptsObj.getAsJsonObject(key);
                                    scriptId = scriptObj.has("id") && !scriptObj.get("id").isJsonNull()
                                            ? scriptObj.get("id").getAsString()
                                            : "N/A";

                                    scriptNombre = obtenerNombreScript(scriptId);
                                    break; // Solo tomamos el primer script encontrado
                                }
                            }

                            // Escribir los datos en el archivo CSV
                            writer.write(idCola + "," + nombreCola + "," + divisionNombre + "," + scriptId + "," + scriptNombre + "\n");
                        }

                        // Verificar si hay más páginas
                        boolean hasMorePages = jsonObject.has("nextUri") && !jsonObject.get("nextUri").isJsonNull();
                        if (!hasMorePages) {
                            break;
                        }
                        pageNumber++;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String obtenerNombreScript(String scriptId) {
        if (scriptId.equals("N/A")) {
            return "N/A";
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(SCRIPT_URL + scriptId);
            get.setHeader("Authorization", "Bearer " + BEARER_TOKEN);

            try (CloseableHttpResponse response = client.execute(get)) {
                int statusCode = response.getCode();
                if (statusCode != 200) {
                    System.out.println("Error al obtener script " + scriptId + ": " + statusCode);
                    return "N/A";
                }

                String jsonResponse = leerRespuesta(response);
                JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

                return jsonObject.has("name") ? jsonObject.get("name").getAsString() : "N/A";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "N/A";
        }
    }

    
    private static String leerRespuesta(CloseableHttpResponse response) throws IOException {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            try (InputStream inputStream = entity.getContent();
                 Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
                return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            }
        }
        return "";
    }
}
