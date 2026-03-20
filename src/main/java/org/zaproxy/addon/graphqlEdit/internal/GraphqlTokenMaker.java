package org.zaproxy.addon.graphqlEdit.internal;

import javax.swing.text.Segment;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

/**
 * TokenMaker para resaltado de sintaxis GraphQL en RSyntaxTextArea.
 * Soporta: keywords, tipos (PascalCase), variables ($), strings, comentarios (#), números.
 */
public class GraphqlTokenMaker extends AbstractTokenMaker {

    public static final String SYNTAX_STYLE_GRAPHQL = "text/graphql";

    @Override
    public TokenMap getWordsToHighlight() {
        TokenMap map = new TokenMap();
        int kw = Token.RESERVED_WORD;
        map.put("query", kw);
        map.put("mutation", kw);
        map.put("subscription", kw);
        map.put("fragment", kw);
        map.put("on", kw);
        map.put("type", kw);
        map.put("input", kw);
        map.put("enum", kw);
        map.put("interface", kw);
        map.put("union", kw);
        map.put("scalar", kw);
        map.put("directive", kw);
        map.put("schema", kw);
        map.put("extend", kw);
        map.put("implements", kw);
        map.put("repeatable", kw);
        map.put("true", Token.LITERAL_BOOLEAN);
        map.put("false", Token.LITERAL_BOOLEAN);
        map.put("null", Token.RESERVED_WORD_2);
        return map;
    }

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();

        char[] array = text.array;
        int offset = text.offset;
        int end = offset + text.count;
        int newStartOffset = startOffset - offset;

        int currentTokenStart = offset;
        int currentTokenType = initialTokenType;

        for (int i = offset; i < end; i++) {
            char c = array[i];

            switch (currentTokenType) {

                case Token.NULL:
                    currentTokenStart = i;
                    if (c == '#') {
                        currentTokenType = Token.COMMENT_EOL;
                    } else if (c == '"') {
                        currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE;
                    } else if (Character.isLetter(c) || c == '_') {
                        currentTokenType = Token.IDENTIFIER;
                    } else if (Character.isDigit(c) || (c == '-' && i + 1 < end && Character.isDigit(array[i + 1]))) {
                        currentTokenType = Token.LITERAL_NUMBER_DECIMAL_INT;
                    } else if (c == '$') {
                        currentTokenType = Token.VARIABLE;
                    } else if (c == ' ' || c == '\t') {
                        currentTokenType = Token.WHITESPACE;
                    } else {
                        addToken(text, i, i, Token.SEPARATOR, newStartOffset + i);
                    }
                    break;

                case Token.WHITESPACE:
                    if (c != ' ' && c != '\t') {
                        addToken(text, currentTokenStart, i - 1, Token.WHITESPACE, newStartOffset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType = Token.NULL;
                        i--;
                    }
                    break;

                case Token.IDENTIFIER:
                    if (!Character.isLetterOrDigit(c) && c != '_') {
                        int ttype = wordsToHighlight.get(text, currentTokenStart, i - 1);
                        if (ttype == -1) {
                            ttype = Character.isUpperCase(array[currentTokenStart])
                                    ? Token.DATA_TYPE
                                    : Token.IDENTIFIER;
                        }
                        addToken(text, currentTokenStart, i - 1, ttype, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        currentTokenStart = i;
                        i--;
                    }
                    break;

                case Token.VARIABLE:
                    if (!Character.isLetterOrDigit(c) && c != '_') {
                        addToken(text, currentTokenStart, i - 1, Token.VARIABLE, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        currentTokenStart = i;
                        i--;
                    }
                    break;

                case Token.LITERAL_NUMBER_DECIMAL_INT:
                    if (!Character.isDigit(c) && c != '.' && c != 'e' && c != 'E') {
                        addToken(text, currentTokenStart, i - 1, Token.LITERAL_NUMBER_DECIMAL_INT, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                        currentTokenStart = i;
                        i--;
                    }
                    break;

                case Token.LITERAL_STRING_DOUBLE_QUOTE:
                    if (c == '"') {
                        addToken(text, currentTokenStart, i, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart);
                        currentTokenType = Token.NULL;
                    } else if (c == '\\') {
                        i++; // skip escaped char
                    }
                    break;

                case Token.COMMENT_EOL:
                    // continues to end of line, handled at the switch below
                    break;

                default:
                    break;
            }
        }

        // End of segment
        switch (currentTokenType) {
            case Token.NULL:
                addNullToken();
                break;
            case Token.IDENTIFIER:
                int ttype = wordsToHighlight.get(text, currentTokenStart, end - 1);
                if (ttype == -1) {
                    ttype = Character.isUpperCase(array[currentTokenStart])
                            ? Token.DATA_TYPE
                            : Token.IDENTIFIER;
                }
                addToken(text, currentTokenStart, end - 1, ttype, newStartOffset + currentTokenStart);
                addNullToken();
                break;
            case Token.COMMENT_EOL:
                addToken(text, currentTokenStart, end - 1, Token.COMMENT_EOL, newStartOffset + currentTokenStart);
                addNullToken();
                break;
            default:
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart);
                addNullToken();
                break;
        }

        return firstToken;
    }
}
