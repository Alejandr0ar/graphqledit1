package org.zaproxy.addon.graphqlEdit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.httpclient.URI;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.zap.extension.httppanel.view.impl.models.http.request.RequestBodyStringHttpPanelViewModel;

/**
 * Modelo unificado para peticiones GraphQL.
 * - POST: lee/escribe el body JSON (comportamiento heredado).
 * - GET:  lee/escribe los parámetros query y variables de la URL.
 */
public class GraphqlRequestViewModel extends RequestBodyStringHttpPanelViewModel {

    @Override
    public String getData() {
        if (isGetRequest()) {
            return getDataFromUrl();
        }
        return super.getData();
    }

    @Override
    public void setData(String data) {
        if (isGetRequest()) {
            setDataToUrl(data);
        } else {
            super.setData(data);
        }
    }

    /** Detecta si el mensaje actual es GET con parámetro query en la URL. */
    public static boolean isGetWithGraphql(HttpMessage msg) {
        if (msg == null) return false;
        if (!"GET".equalsIgnoreCase(msg.getRequestHeader().getMethod())) return false;
        try {
            String qs = msg.getRequestHeader().getURI().getQuery();
            return qs != null && (qs.contains("query=") || qs.contains("query%3D"));
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------

    private boolean isGetRequest() {
        if (getMessage() == null || !(getMessage() instanceof HttpMessage)) return false;
        return "GET".equalsIgnoreCase(((HttpMessage) getMessage()).getRequestHeader().getMethod());
    }

    /**
     * Lee query y variables de la URL y los devuelve como JSON:
     * {"query": "...", "variables": {...}}
     */
    private String getDataFromUrl() {
        try {
            HttpMessage msg = (HttpMessage) getMessage();
            String qs = msg.getRequestHeader().getURI().getQuery();
            if (qs == null) return "";

            String query = extractParam(qs, "query");
            if (query == null) return "";

            String variables = extractParam(qs, "variables");

            JsonObject obj = new JsonObject();
            obj.addProperty("query", query);
            if (variables != null && !variables.trim().isEmpty()) {
                try {
                    obj.add("variables", JsonParser.parseString(variables));
                } catch (JsonSyntaxException e) {
                    // variables no es JSON válido — ignorar
                }
            }
            return new GsonBuilder().disableHtmlEscaping().create().toJson(obj);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Parsea el JSON de data y actualiza los parámetros query y variables en la URL.
     */
    private void setDataToUrl(String data) {
        try {
            HttpMessage msg = (HttpMessage) getMessage();
            JsonObject obj = JsonParser.parseString(data).getAsJsonObject();

            URI uri = msg.getRequestHeader().getURI();
            String qs = uri.getQuery();
            if (qs == null) qs = "";

            if (obj.has("query")) {
                qs = replaceParam(qs, "query", obj.get("query").getAsString());
            }
            if (obj.has("variables")) {
                qs = replaceParam(qs, "variables", obj.get("variables").toString());
            }

            // Reconstruye la URI con el nuevo query string
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();

            StringBuilder newUri = new StringBuilder(scheme).append("://").append(host);
            if (port != -1) newUri.append(':').append(port);
            if (path != null) newUri.append(path);
            if (!qs.isEmpty()) newUri.append('?').append(qs);

            msg.getRequestHeader().setURI(new URI(newUri.toString(), true));
        } catch (Exception e) {
            // ignore
        }
    }

    // -------------------------------------------------------------------------

    private static String extractParam(String queryString, String name) {
        for (String part : queryString.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String key = part.substring(0, eq);
            if (name.equals(key)) {
                try {
                    return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    return part.substring(eq + 1);
                }
            }
        }
        return null;
    }

    private static String replaceParam(String queryString, String name, String value) {
        try {
            String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
            Pattern p = Pattern.compile("(^|&)" + Pattern.quote(name) + "=[^&]*");
            Matcher m = p.matcher(queryString);
            if (m.find()) {
                return m.replaceFirst(m.group(1) + name + "=" + encoded);
            }
            return queryString.isEmpty()
                    ? name + "=" + encoded
                    : queryString + "&" + name + "=" + encoded;
        } catch (Exception e) {
            return queryString;
        }
    }
}
