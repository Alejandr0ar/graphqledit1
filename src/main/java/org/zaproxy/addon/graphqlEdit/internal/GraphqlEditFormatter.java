package org.zaproxy.addon.graphqlEdit.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class GraphqlEditFormatter {

    private static final Gson GSON_PRETTY_PRINT =
            new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    /**
     * Formatea una cadena JSON para que sea legible.
     */
    public static String toFormattedJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return "";
        }
        try {
            JsonElement el = JsonParser.parseString(jsonString);
            return GSON_PRETTY_PRINT.toJson(el);
        } catch (JsonSyntaxException e) {
            return jsonString;
        }
    }

    /**
     * Formatea una consulta GraphQL con indentación de 2 espacios.
     * Maneja: bloques {}, argumentos (), strings, comentarios (#), spread (...).
     */
    public static String formatQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        char[] ch = query.trim().toCharArray();
        int len = ch.length;
        StringBuilder out = new StringBuilder();
        int indent = 0;
        int parenDepth = 0;
        boolean pendingNewline = false;
        int i = 0;

        while (i < len) {
            char c = ch[i];

            // Whitespace: normalizamos según el contexto
            if (Character.isWhitespace(c)) {
                if (indent > 0 && parenDepth == 0) {
                    // Dentro de un bloque {}: el whitespace entre campos => newline
                    int j = i + 1;
                    while (j < len && Character.isWhitespace(ch[j])) j++;
                    if (j < len && ch[j] != '}' && ch[j] != ')' && ch[j] != ']' && ch[j] != '{') {
                        pendingNewline = true;
                    }
                } else {
                    // Fuera de bloque: whitespace => espacio simple
                    if (out.length() > 0) {
                        char last = out.charAt(out.length() - 1);
                        if (last != ' ' && last != '\n' && last != '(' && last != '[') {
                            out.append(' ');
                        }
                    }
                }
                i++;
                while (i < len && Character.isWhitespace(ch[i])) i++;
                continue;
            }

            // String literal
            if (c == '"') {
                if (pendingNewline) { flushNewline(out, indent); pendingNewline = false; }
                // Block string """..."""
                if (i + 2 < len && ch[i + 1] == '"' && ch[i + 2] == '"') {
                    out.append("\"\"\"");
                    i += 3;
                    while (i < len) {
                        if (i + 2 < len && ch[i] == '"' && ch[i + 1] == '"' && ch[i + 2] == '"') {
                            out.append("\"\"\"");
                            i += 3;
                            break;
                        }
                        out.append(ch[i++]);
                    }
                } else {
                    out.append(c);
                    i++;
                    while (i < len && ch[i] != '"') {
                        if (ch[i] == '\\' && i + 1 < len) out.append(ch[i++]);
                        out.append(ch[i++]);
                    }
                    if (i < len) { out.append('"'); i++; }
                }
                continue;
            }

            // Comentario #
            if (c == '#') {
                if (pendingNewline) { flushNewline(out, indent); pendingNewline = false; }
                while (i < len && ch[i] != '\n') out.append(ch[i++]);
                pendingNewline = true;
                continue;
            }

            // Spread operator ...
            if (c == '.' && i + 2 < len && ch[i + 1] == '.' && ch[i + 2] == '.') {
                if (pendingNewline) { flushNewline(out, indent); pendingNewline = false; }
                else ensureNoTrailingSpaceAndSingleSpace(out);
                out.append("...");
                i += 3;
                continue;
            }

            // Apertura de bloque {
            if (c == '{') {
                pendingNewline = false;
                trimTrailingChar(out, ' ');
                out.append(" {\n");
                indent++;
                appendIndent(out, indent);
                i++;
                while (i < len && Character.isWhitespace(ch[i])) i++;
                continue;
            }

            // Cierre de bloque }
            if (c == '}') {
                pendingNewline = false;
                trimTrailingWhitespace(out);
                out.append('\n');
                indent = Math.max(0, indent - 1);
                appendIndent(out, indent);
                out.append('}');
                i++;
                while (i < len && Character.isWhitespace(ch[i])) i++;
                if (i < len && ch[i] != '}') pendingNewline = true;
                continue;
            }

            // Apertura de argumentos (
            if (c == '(') {
                pendingNewline = false;
                trimTrailingChar(out, ' ');
                out.append('(');
                parenDepth++;
                i++;
                while (i < len && Character.isWhitespace(ch[i])) i++;
                continue;
            }

            // Cierre de argumentos )
            if (c == ')') {
                pendingNewline = false;
                trimTrailingChar(out, ' ');
                out.append(')');
                parenDepth = Math.max(0, parenDepth - 1);
                i++;
                continue;
            }

            // Apertura de lista [
            if (c == '[') {
                pendingNewline = false;
                trimTrailingChar(out, ' ');
                out.append('[');
                i++;
                while (i < len && Character.isWhitespace(ch[i])) i++;
                continue;
            }

            // Cierre de lista ]
            if (c == ']') {
                pendingNewline = false;
                trimTrailingChar(out, ' ');
                out.append(']');
                i++;
                continue;
            }

            // Coma: dentro de () => ", "  |  fuera => newline
            if (c == ',') {
                i++;
                while (i < len && Character.isWhitespace(ch[i])) i++;
                if (parenDepth > 0) {
                    trimTrailingChar(out, ' ');
                    out.append(", ");
                } else if (i < len && ch[i] != '}') {
                    pendingNewline = true;
                }
                continue;
            }

            // Dos puntos :
            if (c == ':') {
                trimTrailingChar(out, ' ');
                out.append(": ");
                i++;
                while (i < len && Character.isWhitespace(ch[i])) i++;
                continue;
            }

            // Non-null !
            if (c == '!') {
                trimTrailingChar(out, ' ');
                out.append('!');
                i++;
                continue;
            }

            // Directiva @
            if (c == '@') {
                if (pendingNewline) { flushNewline(out, indent); pendingNewline = false; }
                else ensureNoTrailingSpaceAndSingleSpace(out);
                out.append('@');
                i++;
                continue;
            }

            // Signo de igual = (valores por defecto)
            if (c == '=') {
                trimTrailingChar(out, ' ');
                out.append(" = ");
                i++;
                while (i < len && Character.isWhitespace(ch[i])) i++;
                continue;
            }

            // Cualquier otro carácter (parte de un token: letra, dígito, $, _, etc.)
            if (pendingNewline) {
                flushNewline(out, indent);
                pendingNewline = false;
            }
            out.append(c);
            i++;
        }

        return out.toString().trim();
    }

    private static void flushNewline(StringBuilder out, int indent) {
        trimTrailingChar(out, ' ');
        out.append('\n');
        appendIndent(out, indent);
    }

    private static void ensureNoTrailingSpaceAndSingleSpace(StringBuilder out) {
        if (out.length() > 0) {
            char last = out.charAt(out.length() - 1);
            if (last != ' ' && last != '\n') out.append(' ');
        }
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent * 2; i++) sb.append(' ');
    }

    private static void trimTrailingChar(StringBuilder sb, char target) {
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == target) {
            sb.deleteCharAt(sb.length() - 1);
        }
    }

    private static void trimTrailingWhitespace(StringBuilder sb) {
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
            sb.deleteCharAt(sb.length() - 1);
        }
    }
}
