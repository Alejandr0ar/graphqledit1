package org.zaproxy.addon.graphqlEdit;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.zaproxy.zap.extension.httppanel.view.syntaxhighlight.HttpPanelSyntaxHighlightTextArea;
import org.zaproxy.zap.extension.search.SearchMatch;

/**
 * Área de texto especializada para la edición de cuerpos GraphQL, con resaltado de sintaxis y
 * la funcionalidad esperada por el framework de ZAP para detectar cambios.
 */
public class HttpPanelGraphqlEditArea extends HttpPanelSyntaxHighlightTextArea {

    private static final long serialVersionUID = 1L;

    // Flag para rastrear si el contenido ha sido modificado por el usuario.
    private boolean textModified = false;

    public HttpPanelGraphqlEditArea() {
        this.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON_WITH_COMMENTS);
        this.setCodeFoldingEnabled(true);

        // Añadir un listener al documento para detectar cualquier cambio y marcar el estado como modificado.
        this.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                textModified = true;
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                textModified = true;
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                textModified = true;
            }
        });
    }

    @Override
    public void search(Pattern p, List<SearchMatch> matches) {
        if (p == null) {
            return;
        }
        Matcher matcher = p.matcher(this.getText());
        while (matcher.find()) {
            matches.add(
                    new SearchMatch(
                            SearchMatch.Location.REQUEST_BODY, matcher.start(), matcher.end()));
        }
    }

    @Override
    public void highlight(SearchMatch sm) {
        if (sm == null || sm.getStart() < 0 || sm.getEnd() < 0) {
            return;
        }
        this.select(sm.getStart(), sm.getEnd());
        this.requestFocusInWindow();
    }

    /**
     * Sobrescribe el método setText para reiniciar el flag de modificación cuando
     * el contenido se carga mediante programación (ej. al seleccionar un nuevo mensaje).
     */
    @Override
    public void setText(String content) {
        super.setText(content);
        // Después de establecer el texto, se considera "no modificado" hasta que el usuario lo edite.
        this.textModified = false;
    }

    /**
     * Implementa el método `isTextModified()` que el framework de ZAP necesita.
     * @return true si el usuario ha cambiado el texto, false en caso contrario.
     */
    public boolean isTextModified() {
        return this.textModified;
    }

    /**
     * Sobrescribe `discardAllEdits()` para reiniciar el gestor de "deshacer" (undo) y
     * nuestro flag de modificación personalizado.
     */
    @Override
    public void discardAllEdits() {
        super.discardAllEdits();
        this.textModified = false;
    }

    @Override
    protected CustomTokenMakerFactory getTokenMakerFactory() {
        return null;
    }
}