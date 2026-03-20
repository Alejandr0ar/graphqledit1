package org.zaproxy.addon.graphqlEdit;

import org.parosproxy.paros.extension.ExtensionAdaptor;
import org.parosproxy.paros.extension.ExtensionHook;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.zap.extension.httppanel.Message;
import org.zaproxy.zap.extension.httppanel.component.all.request.RequestAllComponent;
import org.zaproxy.zap.extension.httppanel.component.split.request.RequestSplitComponent;
import org.zaproxy.zap.extension.httppanel.view.HttpPanelDefaultViewSelector;
import org.zaproxy.zap.extension.httppanel.view.HttpPanelView;
import org.zaproxy.zap.view.HttpPanelManager;
import org.zaproxy.zap.view.HttpPanelManager.HttpPanelDefaultViewSelectorFactory;
import org.zaproxy.zap.view.HttpPanelManager.HttpPanelViewFactory;

public class ExtensionGraphqlEdit extends ExtensionAdaptor {

    public static final String NAME = "ExtensionHttpPanelJsonView";

    public ExtensionGraphqlEdit() {
        super(NAME);
    }

    @Override
    public void hook(ExtensionHook extensionHook) {
        super.hook(extensionHook);

        if (hasView()) {
            HttpPanelManager panelManager = HttpPanelManager.getInstance();
            panelManager.addRequestViewFactory(RequestSplitComponent.NAME, new RequestGraphqlViewFactory());
            panelManager.addRequestViewFactory(RequestAllComponent.NAME, new RequestGraphqlViewFactory());

            panelManager.addRequestDefaultViewSelectorFactory(
                    RequestSplitComponent.NAME, new GraphqlDefaultViewSelectorFactory());
            panelManager.addRequestDefaultViewSelectorFactory(
                    RequestAllComponent.NAME, new GraphqlDefaultViewSelectorFactory());

            extensionHook.getHookMenu().addPopupMenuItem(new GenerateIntrospectionMenuItem());
        }
    }

    @Override
    public boolean canUnload() {
        return true;
    }

    @Override
    public void unload() {
        if (hasView()) {
            HttpPanelManager panelManager = HttpPanelManager.getInstance();
            panelManager.removeRequestViewFactory(RequestSplitComponent.NAME, RequestGraphqlViewFactory.NAME);
            panelManager.removeRequestViews(
                    RequestSplitComponent.NAME,
                    HttpPanelGraphqlEditView.NAME,
                    RequestSplitComponent.ViewComponent.BODY);
            panelManager.removeRequestViewFactory(RequestAllComponent.NAME, RequestGraphqlViewFactory.NAME);
            panelManager.removeRequestViews(RequestAllComponent.NAME, HttpPanelGraphqlEditView.NAME, null);

            panelManager.removeRequestDefaultViewSelectorFactory(
                    RequestSplitComponent.NAME, GraphqlDefaultViewSelectorFactory.NAME);
            panelManager.removeRequestDefaultViewSelectors(
                    RequestSplitComponent.NAME,
                    GraphqlDefaultViewSelector.NAME,
                    RequestSplitComponent.ViewComponent.BODY);
            panelManager.removeRequestDefaultViewSelectorFactory(
                    RequestAllComponent.NAME, GraphqlDefaultViewSelectorFactory.NAME);
            panelManager.removeRequestDefaultViewSelectors(
                    RequestAllComponent.NAME, GraphqlDefaultViewSelector.NAME, null);
        }
        super.unload();
    }

    private static final class RequestGraphqlViewFactory implements HttpPanelViewFactory {
        public static final String NAME = "RequestGraphqlViewFactory";

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public HttpPanelView getNewView() {
            return new HttpPanelGraphqlEditView(new GraphqlRequestViewModel());
        }

        @Override
        public Object getOptions() {
            return RequestSplitComponent.ViewComponent.BODY;
        }
    }

    private static final class GraphqlDefaultViewSelector implements HttpPanelDefaultViewSelector {

        public static final String NAME = "GraphqlDefaultViewSelector";

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public boolean matchToDefaultView(Message message) {
            if (!(message instanceof HttpMessage)) {
                return false;
            }
            return HttpPanelGraphqlEditView.isGraphqlMessage((HttpMessage) message);
        }

        @Override
        public String getViewName() {
            return HttpPanelGraphqlEditView.NAME;
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }

    private static final class GraphqlDefaultViewSelectorFactory
            implements HttpPanelDefaultViewSelectorFactory {

        public static final String NAME = "GraphqlDefaultViewSelectorFactory";

        private static final HttpPanelDefaultViewSelector SELECTOR = new GraphqlDefaultViewSelector();

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public HttpPanelDefaultViewSelector getNewDefaultViewSelector() {
            return SELECTOR;
        }

        @Override
        public Object getOptions() {
            return RequestSplitComponent.ViewComponent.BODY;
        }
    }
}
