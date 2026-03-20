package org.zaproxy.addon.graphqlEdit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.apache.commons.configuration.FileConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.view.View;
import org.zaproxy.addon.graphqlEdit.internal.GraphqlEditFormatter;
import org.zaproxy.zap.extension.httppanel.Message;
import org.zaproxy.zap.extension.httppanel.view.AbstractStringHttpPanelViewModel;
import org.zaproxy.zap.extension.httppanel.view.HttpPanelView;
import org.zaproxy.zap.extension.httppanel.view.HttpPanelViewModel;
import org.zaproxy.zap.extension.httppanel.view.HttpPanelViewModelEvent;
import org.zaproxy.zap.extension.httppanel.view.HttpPanelViewModelListener;
import org.zaproxy.zap.extension.httppanel.view.impl.models.http.request.RequestBodyStringHttpPanelViewModel;

public class HttpPanelGraphqlEditView implements HttpPanelView, HttpPanelViewModelListener {

    public static final String NAME = "HttpPanelGraphqlEditView";
    private static final String CAPTION_NAME = "GraphQL Edit";
    private static final Logger log = LogManager.getLogger(HttpPanelGraphqlEditView.class);

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    // Detección de introspección
    private static final Pattern INTROSPECTION_PATTERN =
            Pattern.compile("__schema|__type|IntrospectionQuery", Pattern.CASE_INSENSITIVE);

    // Detección por path de URL
    private static final Pattern GRAPHQL_PATH_PATTERN =
            Pattern.compile("/graphql|/gql|/api/graphql|/v\\d+/graphql|/query",
                    Pattern.CASE_INSENSITIVE);

    // Query estándar de introspección para descubrir el esquema completo
    static final String INTROSPECTION_QUERY =
            "query IntrospectionQuery {\n"
            + "  __schema {\n"
            + "    queryType { name }\n"
            + "    mutationType { name }\n"
            + "    subscriptionType { name }\n"
            + "    types {\n"
            + "      ...FullType\n"
            + "    }\n"
            + "    directives {\n"
            + "      name\n"
            + "      description\n"
            + "      locations\n"
            + "      args {\n"
            + "        ...InputValue\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "fragment FullType on __Type {\n"
            + "  kind\n"
            + "  name\n"
            + "  description\n"
            + "  fields(includeDeprecated: true) {\n"
            + "    name\n"
            + "    description\n"
            + "    args {\n"
            + "      ...InputValue\n"
            + "    }\n"
            + "    type {\n"
            + "      ...TypeRef\n"
            + "    }\n"
            + "    isDeprecated\n"
            + "    deprecationReason\n"
            + "  }\n"
            + "  inputFields {\n"
            + "    ...InputValue\n"
            + "  }\n"
            + "  interfaces {\n"
            + "    ...TypeRef\n"
            + "  }\n"
            + "  enumValues(includeDeprecated: true) {\n"
            + "    name\n"
            + "    description\n"
            + "    isDeprecated\n"
            + "    deprecationReason\n"
            + "  }\n"
            + "  possibleTypes {\n"
            + "    ...TypeRef\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "fragment InputValue on __InputValue {\n"
            + "  name\n"
            + "  description\n"
            + "  type { ...TypeRef }\n"
            + "  defaultValue\n"
            + "}\n"
            + "\n"
            + "fragment TypeRef on __Type {\n"
            + "  kind\n"
            + "  name\n"
            + "  ofType {\n"
            + "    kind\n"
            + "    name\n"
            + "    ofType {\n"
            + "      kind\n"
            + "      name\n"
            + "      ofType {\n"
            + "        kind\n"
            + "        name\n"
            + "        ofType {\n"
            + "          kind\n"
            + "          name\n"
            + "          ofType {\n"
            + "            kind\n"
            + "            name\n"
            + "            ofType {\n"
            + "              kind\n"
            + "              name\n"
            + "              ofType {\n"
            + "                kind\n"
            + "                name\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

    private HttpPanelGraphqlQueryArea graphqlEditArea;
    private HttpPanelGraphqlEditArea variablesEditArea;
    private JTextField operationNameField;
    private boolean operationNameModified = false;

    private JLabel introspectionLabel;
    private JLabel batchLabel;

    private final JPanel mainPanel;
    private final AbstractStringHttpPanelViewModel model;

    private JsonObject originalJsonBody;
    private boolean isBatchMode = false;

    public HttpPanelGraphqlEditView(AbstractStringHttpPanelViewModel model) {
        this.model = model;
        this.originalJsonBody = new JsonObject();
        mainPanel = new JPanel(new BorderLayout());

        // ── Barra superior: Operation Name + indicadores ──────────────────────
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));

        topBar.add(new JLabel("Operation:"));
        operationNameField = new JTextField(20);
        operationNameField.setToolTipText("operationName (opcional)");
        operationNameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { operationNameModified = true; }
            @Override public void removeUpdate(DocumentEvent e) { operationNameModified = true; }
            @Override public void changedUpdate(DocumentEvent e) { operationNameModified = true; }
        });
        topBar.add(operationNameField);

        introspectionLabel = new JLabel("[ Introspection ]");
        introspectionLabel.setForeground(new Color(180, 100, 0));
        introspectionLabel.setVisible(false);
        topBar.add(introspectionLabel);

        batchLabel = new JLabel("[ Batch ]");
        batchLabel.setForeground(new Color(0, 100, 180));
        batchLabel.setVisible(false);
        topBar.add(batchLabel);

        mainPanel.add(topBar, BorderLayout.NORTH);

        // ── Panel Query con etiqueta ──────────────────────────────────────────
        graphqlEditArea = new HttpPanelGraphqlQueryArea();
        RTextScrollPane queryScrollPane = new RTextScrollPane(graphqlEditArea);
        queryScrollPane.setLineNumbersEnabled(true);
        assignPopupMenu(graphqlEditArea);
        graphqlEditArea.setOnIntrospectionApplied(
                () -> operationNameField.setText("IntrospectionQuery"));

        JPanel queryPanel = new JPanel(new BorderLayout());
        queryPanel.add(new JLabel(" Query"), BorderLayout.NORTH);
        queryPanel.add(queryScrollPane, BorderLayout.CENTER);

        // ── Panel Variables con etiqueta ──────────────────────────────────────
        variablesEditArea = new HttpPanelGraphqlEditArea();
        RTextScrollPane variablesScrollPane = new RTextScrollPane(variablesEditArea);
        variablesScrollPane.setLineNumbersEnabled(true);
        assignPopupMenu(variablesEditArea);

        JPanel variablesPanel = new JPanel(new BorderLayout());
        variablesPanel.add(new JLabel(" Variables (JSON)"), BorderLayout.NORTH);
        variablesPanel.add(variablesScrollPane, BorderLayout.CENTER);

        JSplitPane splitPane =
                new JSplitPane(JSplitPane.VERTICAL_SPLIT, queryPanel, variablesPanel);
        splitPane.setResizeWeight(0.6);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        model.addHttpPanelViewModelListener(this);
    }

    @Override
    public void save() {
        if (isBatchMode) {
            // En modo batch el editor muestra el array JSON completo
            this.model.setData(graphqlEditArea.getText());
            return;
        }

        JsonObject jsonToSave = originalJsonBody.deepCopy();
        try {
            jsonToSave.addProperty("query", graphqlEditArea.getText());

            String opName = operationNameField.getText().trim();
            if (!opName.isEmpty()) {
                jsonToSave.addProperty("operationName", opName);
            } else {
                jsonToSave.remove("operationName");
            }

            String variablesText = variablesEditArea.getText();
            if (variablesText != null && !variablesText.trim().isEmpty()) {
                JsonElement variablesJson = JsonParser.parseString(variablesText);
                jsonToSave.add("variables", variablesJson);
            } else {
                jsonToSave.remove("variables");
            }

            this.model.setData(GSON.toJson(jsonToSave));

        } catch (JsonSyntaxException e) {
            log.warn("No se pudieron guardar los cambios. Variables no es JSON válido.", e);
        }
    }

    @Override
    public void dataChanged(HttpPanelViewModelEvent e) {
        String body = ((AbstractStringHttpPanelViewModel) e.getSource()).getData();

        isBatchMode = false;
        introspectionLabel.setVisible(false);
        batchLabel.setVisible(false);
        operationNameModified = false;

        try {
            JsonElement parsed = JsonParser.parseString(body);

            // ── Batch query: array JSON ────────────────────────────────────────
            if (parsed.isJsonArray()) {
                JsonArray array = parsed.getAsJsonArray();
                isBatchMode = true;
                batchLabel.setText("[ Batch: " + array.size() + " ops ]");
                batchLabel.setVisible(true);
                graphqlEditArea.setText(GraphqlEditFormatter.toFormattedJson(body));
                variablesEditArea.setText("");
                operationNameField.setText("");
                return;
            }

            // ── Petición GraphQL normal ────────────────────────────────────────
            this.originalJsonBody = parsed.getAsJsonObject();

            String query = "";
            if (originalJsonBody.has("query") && originalJsonBody.get("query").isJsonPrimitive()) {
                query = originalJsonBody.get("query").getAsString();
            }

            String opName = "";
            if (originalJsonBody.has("operationName")
                    && originalJsonBody.get("operationName").isJsonPrimitive()) {
                opName = originalJsonBody.get("operationName").getAsString();
            }

            JsonElement variables = originalJsonBody.get("variables");

            graphqlEditArea.setText(GraphqlEditFormatter.formatQuery(query));
            operationNameField.setText(opName);

            if (variables != null && (variables.isJsonObject() || variables.isJsonArray())) {
                variablesEditArea.setText(GraphqlEditFormatter.toFormattedJson(variables.toString()));
            } else {
                variablesEditArea.setText("");
            }

            // ── Detección de introspección ─────────────────────────────────────
            if (INTROSPECTION_PATTERN.matcher(query).find()) {
                introspectionLabel.setVisible(true);
            }

        } catch (JsonSyntaxException | IllegalStateException ex) {
            this.originalJsonBody = new JsonObject();
            graphqlEditArea.setText(GraphqlEditFormatter.formatQuery(body));
            variablesEditArea.setText("");
            operationNameField.setText("");
        }

        if (!isEditable()) {
            graphqlEditArea.discardAllEdits();
            variablesEditArea.discardAllEdits();
        }
        graphqlEditArea.setCaretPosition(0);
        variablesEditArea.setCaretPosition(0);
    }

    @Override
    public void setSelected(boolean selected) {
        if (selected) {
            graphqlEditArea.requestFocusInWindow();
        }
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getCaptionName() { return CAPTION_NAME; }

    private void assignPopupMenu(Component component) {
        component.addMouseListener(
                new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) { showPopupMenu(e); }
                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) { showPopupMenu(e); }
                    private void showPopupMenu(java.awt.event.MouseEvent e) {
                        if (e.isPopupTrigger()) {
                            if (!component.isFocusOwner()) {
                                component.requestFocusInWindow();
                            }
                            View.getSingleton().getPopupMenu().show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                });
    }

    @Override
    public String getTargetViewName() { return ""; }

    @Override
    public int getPosition() { return 0; }

    @Override
    public boolean isEnabled(Message message) {
        if (!(message instanceof HttpMessage)) return false;
        if (!(this.model instanceof RequestBodyStringHttpPanelViewModel)) return false;
        return isGraphqlMessage((HttpMessage) message);
    }

    public static boolean isGraphqlMessage(HttpMessage httpMessage) {
        // GET con parámetro query en la URL
        if (GraphqlRequestViewModel.isGetWithGraphql(httpMessage)) return true;

        // Detección por path de la URL
        try {
            String path = httpMessage.getRequestHeader().getURI().getPath();
            if (path != null && GRAPHQL_PATH_PATTERN.matcher(path).find()) return true;
        } catch (Exception ex) {
            // ignorar
        }

        // POST: Content-Type y body
        String contentType = httpMessage.getRequestHeader().getHeader(HttpHeader.CONTENT_TYPE);
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("application/graphql")) return true;
            if (ct.contains("application/json")) {
                String body = httpMessage.getRequestBody().toString();
                String trimmed = body.trim();
                // Batch: array JSON con operaciones GraphQL
                if (trimmed.startsWith("[")) {
                    return trimmed.contains("\"query\"") || trimmed.contains("\"mutation\"");
                }
                return body.contains("\"query\"") || body.contains("\"mutation\"");
            }
        }
        return false;
    }

    @Override
    public boolean hasChanged() {
        return graphqlEditArea.isTextModified()
                || variablesEditArea.isTextModified()
                || operationNameModified;
    }

    @Override
    public JComponent getPane() { return mainPanel; }

    @Override
    public boolean isEditable() { return graphqlEditArea.isEditable(); }

    @Override
    public void setEditable(boolean editable) {
        graphqlEditArea.setEditable(editable);
        variablesEditArea.setEditable(editable);
        operationNameField.setEditable(editable);
    }

    @Override
    public HttpPanelViewModel getModel() { return model; }

    @Override
    public void setParentConfigurationKey(String configurationKey) {}

    @Override
    public void loadConfiguration(FileConfiguration fileConfiguration) {}

    @Override
    public void saveConfiguration(FileConfiguration fileConfiguration) {}
}
