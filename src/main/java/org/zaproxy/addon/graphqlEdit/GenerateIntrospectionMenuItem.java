package org.zaproxy.addon.graphqlEdit;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import org.parosproxy.paros.extension.ExtensionPopupMenuItem;

/**
 * Item de menú contextual de ZAP que aparece al hacer clic derecho sobre el área de query GraphQL.
 * Reemplaza el contenido del editor con el query de introspección estándar.
 */
public class GenerateIntrospectionMenuItem extends ExtensionPopupMenuItem {

    private static final long serialVersionUID = 1L;

    public GenerateIntrospectionMenuItem() {
        super("Generar Introspección");
        addActionListener(e -> {
            Component focused =
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focused instanceof HttpPanelGraphqlQueryArea) {
                HttpPanelGraphqlQueryArea area = (HttpPanelGraphqlQueryArea) focused;
                area.setText(HttpPanelGraphqlEditView.INTROSPECTION_QUERY);
                Runnable callback = area.getOnIntrospectionApplied();
                if (callback != null) callback.run();
            }
        });
    }

    @Override
    public boolean isEnableForComponent(Component invoker) {
        return invoker instanceof HttpPanelGraphqlQueryArea;
    }
}
