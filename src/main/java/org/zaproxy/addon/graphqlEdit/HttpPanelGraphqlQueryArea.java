package org.zaproxy.addon.graphqlEdit;

import org.fife.ui.rsyntaxtextarea.TokenMaker;
import org.zaproxy.addon.graphqlEdit.internal.GraphqlTokenMaker;

/**
 * Área de texto para el query GraphQL con resaltado de sintaxis GraphQL real.
 * Usa una CustomTokenMakerFactory propia para evitar el ClassNotFoundException
 * que ocurre cuando RSyntaxTextArea intenta cargar GraphqlTokenMaker con el
 * classloader del sistema (que no conoce las clases del add-on).
 */
public class HttpPanelGraphqlQueryArea extends HttpPanelGraphqlEditArea {

    private static final long serialVersionUID = 1L;

    /** Callback que la vista registra para reaccionar cuando se aplica la introspección. */
    private Runnable onIntrospectionApplied;

    public HttpPanelGraphqlQueryArea() {
        this.setSyntaxEditingStyle(GraphqlTokenMaker.SYNTAX_STYLE_GRAPHQL);
    }

    /**
     * Devuelve una factory que instancia GraphqlTokenMaker directamente,
     * evitando el Class.forName() con classloader incorrecto.
     */
    @Override
    protected CustomTokenMakerFactory getTokenMakerFactory() {
        return new GraphqlTokenMakerFactory();
    }

    private static class GraphqlTokenMakerFactory extends CustomTokenMakerFactory {

        @Override
        protected void initTokenMakerMap() {
            super.initTokenMakerMap();
            // No usamos putMapping aquí para evitar Class.forName con el
            // classloader de RSyntaxTextArea. En su lugar sobreescribimos
            // getTokenMakerImpl para instanciar directamente.
        }

        @Override
        protected TokenMaker getTokenMakerImpl(String key) {
            if (GraphqlTokenMaker.SYNTAX_STYLE_GRAPHQL.equals(key)) {
                return new GraphqlTokenMaker();
            }
            return super.getTokenMakerImpl(key);
        }
    }

    public void setOnIntrospectionApplied(Runnable callback) {
        this.onIntrospectionApplied = callback;
    }

    public Runnable getOnIntrospectionApplied() {
        return onIntrospectionApplied;
    }
}
