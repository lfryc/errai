package org.jboss.errai.workspaces.client;

import com.allen_sauer.gwt.dnd.client.PickupDragController;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import static com.google.gwt.core.client.GWT.create;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import static com.google.gwt.user.client.Window.enableScrolling;
import com.google.gwt.user.client.ui.*;
import org.jboss.errai.bus.client.*;
import org.jboss.errai.bus.client.json.JSONUtilCli;
import org.jboss.errai.bus.client.protocols.MessageParts;
import org.jboss.errai.bus.client.protocols.SecurityCommands;
import org.jboss.errai.bus.client.protocols.SecurityParts;
import org.jboss.errai.bus.client.security.SecurityService;
import org.jboss.errai.common.client.framework.AcceptsCallback;
import org.jboss.errai.common.client.framework.WSComponent;
import org.jboss.errai.widgets.client.WSModalDialog;
import org.jboss.errai.widgets.client.WSWindowPanel;
import org.jboss.errai.workspaces.client.framework.*;
import org.jboss.errai.workspaces.client.layout.WorkspaceLayout;
import org.jboss.errai.workspaces.client.widgets.WSLoginPanel;

import java.util.*;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class Workspace implements EntryPoint {

    public static PickupDragController dragController;
    private static WorkspaceLayout workspaceLayout;
    private static SecurityService securityService = new SecurityService();
    private static WSComponent loginComponent = new WSLoginPanel();

    private static WSWindowPanel loginWindowPanel;
    private static Window.ClosingHandler loginWindowClosingHandler;

    static {
        loginWindowClosingHandler = new Window.ClosingHandler() {
            public void onWindowClosing(Window.ClosingEvent event) {
                ErraiClient.getBus().send("ServerEchoService", new CommandMessage());
            }
        };
    }

    private static List<ToolSet> toBeLoaded = new ArrayList<ToolSet>();
    private static Map<String, List<ToolProvider>> toBeLoadedGroups = new HashMap<String, List<ToolProvider>>();
    private static List<String> preferredGroupOrdering = new ArrayList<String>();
    private static Set<String> sessionRoles = new HashSet<String>();

    private Workspace() {
    }

    /**
     * This is the entry point method.
     */
    public void onModuleLoad() {
        init("rootPanel");
    }

    private void init(final String rootId) {
        if (workspaceLayout != null) {
            return;
        }

        final MessageBus bus = ErraiClient.getBus();

        /**
         * Configure the local client message bus to send RemoteSubscribe signals to the remote bus when
         * new subscriptions are created locally.
         */


        /**
         *  Declare the standard LoginClient here.
         */
        bus.subscribe("ClientErrorService", new MessageCallback() {
            public void callback(CommandMessage message) {
                String errorMessage = message.get(String.class, MessageParts.ErrorMessage);

                WSModalDialog errorDialog = new WSModalDialog();
                errorDialog.ask(errorMessage, new AcceptsCallback() {
                    public void callback(Object message, Object data) {
                    }
                });
                errorDialog.showModal();
            }
        });


        bus.subscribe("LoginClient", new MessageCallback() {
            private CommandMessage deferredMessage;

            public void callback(CommandMessage message) {
                try {

                    switch (SecurityCommands.valueOf(message.getCommandType())) {
                        case SecurityChallenge:
                            deferredMessage = new CommandMessage();
                            deferredMessage.setParts(JSONUtilCli.decodeMap(message.get(String.class, SecurityParts.RejectedMessage)));

                            workspaceLayout.getUserInfoPanel().clear();

                            showLoginPanel();
                            break;

                        case FailedAuth:
                            closeLoginPanel();

                            WSModalDialog failed = new WSModalDialog();
                            failed.ask("Authentication Failure. Please Try Again", new AcceptsCallback() {
                                public void callback(Object message, Object data) {
                                    if ("WindowClosed".equals(message)) showLoginPanel();
                                }
                            });
                            failed.showModal();
                            break;

                        case SuccessfulAuth:
                            closeLoginPanel();


                            final WSWindowPanel welcome = new WSWindowPanel();
                            welcome.setWidth("250px");
                            VerticalPanel vp = new VerticalPanel();
                            vp.setWidth("100%");

                            Label label = new Label("Welcome " + message.get(String.class, SecurityParts.Name)
                                    + ", you are now logged in -- "
                                    + (message.hasPart(MessageParts.MessageText) ?
                                    message.get(String.class, MessageParts.MessageText) : ""));

                            label.getElement().getStyle().setProperty("margin", "20px");

                            vp.add(label);
                            vp.setCellVerticalAlignment(label, HasAlignment.ALIGN_MIDDLE);
                            vp.setCellHeight(label, "50px");

                            Button okButton = new Button("OK");
                            okButton.getElement().getStyle().setProperty("margin", "20px");

                            vp.add(okButton);
                            vp.setCellHorizontalAlignment(okButton, HasAlignment.ALIGN_CENTER);

                            okButton.addClickHandler(new ClickHandler() {
                                public void onClick(ClickEvent event) {
                                    welcome.hide();
                                }
                            });

                            welcome.add(vp);
                            welcome.show();
                            welcome.center();

                            okButton.setFocus(true);

                            bus.send(deferredMessage);

                            break;

                        default:
                            // I don't know this command. :(
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        /**
         * Initialize the workspace UI.
         */


        initWorkspace(rootId);

        try {
            /**
             * Specifiy a callback interface to execute the _initAfterWSLoad() tasks when we know the bus
             * is fully up and running.
             */
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Initialize the actual Workspace UI.
     *
     * @param rootId -
     */
    private void initWorkspace(String rootId) {
        /**
         * Instantiate layout.
         */
        workspaceLayout = new WorkspaceLayout(rootId);

        enableScrolling(false);

        if (rootId != null) {
            RootPanel.get(rootId).add(workspaceLayout);
        }
        else {
            Window.alert("No root ID specified!");
            return;
        }

        /**
         * Create the main drag and drop controller for the UI.
         */
        dragController = new PickupDragController(RootPanel.get(), true);


        final ClientMessageBus bus = (ClientMessageBus) ErraiClient.getBus();

        bus.addPostInitTask(new Runnable() {
            public void run() {
                CommandMessage.create()
                        .toSubject("ClientNegotiationService")
                        .set(MessageParts.ReplyTo, "ClientConfiguratorService")
                        .sendNowWith(bus);
            }
        });

        /**
         * This service is used for setting up and restoring the session.
         */
        bus.subscribe("ClientConfiguratorService",
                new MessageCallback() {
                    public void callback(CommandMessage message) {
                        if (message.hasPart(SecurityParts.Roles)) {
                            String[] roleStrs = message.get(String.class, SecurityParts.Roles).split(",");
                            for (String s : roleStrs) {
                                sessionRoles.add(s.trim());
                            }
                        }

                        if (message.hasPart(SecurityParts.Name)) {
                            HorizontalPanel userInfo = new HorizontalPanel();
                            Label userName = new Label(message.get(String.class, SecurityParts.Name));
                            userName.getElement().getStyle().setProperty("fontWeight", "bold");

                            userInfo.add(userName);
                            Button logout = new Button("Logout");
                            logout.setStyleName("logoutButton");
                            userInfo.add(logout);
                            logout.addClickHandler(new ClickHandler() {
                                public void onClick(ClickEvent event) {
                                    CommandMessage.create(SecurityCommands.EndSession)
                                            .toSubject("AuthorizationService")
                                            .sendNowWith(bus);
                                }
                            });

                            workspaceLayout.getUserInfoPanel().add(userInfo);
                        }

                        renderToolPallete();
                    }
                });


    }

    public static SecurityService getSecurityService() {
        return securityService;
    }

    public static WSComponent getLoginComponent() {
        return loginComponent;
    }

    public static void setLoginComponent(WSComponent loginComponent) {
        Workspace.loginComponent = loginComponent;
    }

    public static void addToolSet(ToolSet toolSet) {
        toBeLoaded.add(toolSet);
    }

    private static int toolCounter = 0;

    public static void addTool(String group, String name, String icon, boolean multipleAllowed, int priority, WSComponent component) {
        if (!toBeLoadedGroups.containsKey(group)) toBeLoadedGroups.put(group, new ArrayList<ToolProvider>());

        final String toolId = name.replaceAll(" ", "_") + "." + toolCounter++;
        if (icon == null || "".equals(icon)) {
            icon = "/images/ui/icons/application.png";
        }

        final Tool toolImpl = new ToolImpl(name, toolId, multipleAllowed, new Image(GWT.getModuleBaseURL() + icon), component);
        ToolProvider provider = new ToolProvider() {
            public Tool getTool() {
                return toolImpl;
            }
        };

        toBeLoadedGroups.get(group).add(provider);
    }

    public static void addTool(String group, String name, String icon, boolean multipleAllowed, int prioerty, WSComponent component, final String[] renderIfRoles) {
        if (!toBeLoadedGroups.containsKey(group)) toBeLoadedGroups.put(group, new ArrayList<ToolProvider>());

        final String toolId = name.replaceAll(" ", "_") + "." + toolCounter++;
        if (icon == null || "".equals(icon)) {
            icon = "/images/ui/icons/application.png";
        }

        final Set<String> roles = new HashSet<String>();

        for (String role : renderIfRoles) {
            roles.add(role.trim());
        }


        final Tool toolImpl = new ToolImpl(name, toolId, multipleAllowed, new Image(GWT.getModuleBaseURL() + icon), component);
        ToolProvider provider = new ToolProvider() {
            public Tool getTool() {
                if (sessionRoles.containsAll(roles)) {
                    return toolImpl;
                }
                else {
                    return null;
                }
            }
        };

        toBeLoadedGroups.get(group).add(provider);
    }

    public void renderToolPallete() {
        ModuleLoaderBootstrap mlb = create(ModuleLoaderBootstrap.class);
        mlb.initAll(workspaceLayout);

        Set<String> loaded = new HashSet<String>();
        if (!preferredGroupOrdering.isEmpty()) {
            for (final String group : preferredGroupOrdering) {
                if (loaded.contains(group)) continue;

                for (ToolSet ts : toBeLoaded) {
                    if (ts.getToolSetName().equals(group)) {
                        loaded.add(group);
                        WorkspaceLayout.addToolSet(ts);
                    }
                }

                if (loaded.contains(group)) continue;

                if (toBeLoadedGroups.containsKey(group)) {
                    loaded.add(group);

                    final List<Tool> toBeRendered = new ArrayList<Tool>();
                    for (ToolProvider provider : toBeLoadedGroups.get(group)) {
                        Tool t = provider.getTool();
                        if (t != null) {
                            toBeRendered.add(t);
                        }
                    }

                    if (!toBeRendered.isEmpty()) {
                        ToolSet ts = new ToolSet() {
                            public Tool[] getAllProvidedTools() {
                                Tool[] toolArray = new Tool[toBeRendered.size()];
                                toBeRendered.toArray(toolArray);
                                return toolArray;
                            }

                            public String getToolSetName() {
                                return group;
                            }

                            public Widget getWidget() {
                                return null;
                            }
                        };

                        WorkspaceLayout.addToolSet(ts);
                    }
                }
            }
        }

        for (ToolSet ts : toBeLoaded) {
            if (loaded.contains(ts.getToolSetName())) continue;
            WorkspaceLayout.addToolSet(ts);
        }

        for (final String group : toBeLoadedGroups.keySet()) {
            if (loaded.contains(group)) continue;

            final List<Tool> toBeRendered = new ArrayList<Tool>();
            for (ToolProvider provider : toBeLoadedGroups.get(group)) {
                Tool t = provider.getTool();
                if (t != null) {
                    toBeRendered.add(t);
                }
            }

            if (!toBeRendered.isEmpty()) {

                ToolSet ts = new ToolSet() {
                    public Tool[] getAllProvidedTools() {
                        Tool[] toolArray = new Tool[toBeRendered.size()];
                        toBeRendered.toArray(toolArray);
                        return toolArray;
                    }

                    public String getToolSetName() {
                        return group;
                    }

                    public Widget getWidget() {
                        return null;
                    }
                };

                WorkspaceLayout.addToolSet(ts);
            }
        }

        toBeLoadedGroups.clear();
        toBeLoaded.clear();
    }


    public static void setPreferredGroupOrdering(String[] groups) {
        preferredGroupOrdering.addAll(Arrays.asList(groups));
    }

    private static void closeLoginPanel() {
        if (loginWindowPanel != null) {
            loginWindowPanel.removeClosingHandler(loginWindowClosingHandler);
            loginWindowPanel.hide();
            RootPanel.get().remove(loginWindowPanel);
            loginWindowPanel = null;
        }
    }

    private static void newWindowPanel() {
        closeLoginPanel();

        loginWindowPanel = new WSWindowPanel();
        loginWindowPanel.addClosingHandler(loginWindowClosingHandler);
    }

    public static void showLoginPanel() {
        newWindowPanel();
        loginWindowPanel.setTitle("Security Challenge");
        loginWindowPanel.add(loginComponent.getWidget());
        loginWindowPanel.showModal();
        loginWindowPanel.center();
    }


    private native static void _initAfterWSLoad() /*-{
        try {
            $wnd.initAfterWSLoad();
        }
        catch (e) {
        }
    }-*/;
}