/*
 * JMCL Launcher
 * Copyright (C) 2026 xiesinian and liangchen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.main;

import javafx.beans.property.ReadOnlyObjectWrapper;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ai.AIAssistant;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The AI assistant page: the user describes what they want in natural language and the page
/// installs the matching Minecraft version or downloads the matching mod.
@NotNullByDefault
public final class AiAssistantPage extends DecoratorAnimatedPage implements DecoratorPage {

    /// The page state shown in the title bar.
    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("ai.title"), -1));

    /// Container holding every chat bubble.
    private final VBox messageBox = new VBox(10);

    /// The natural-language input field.
    private final TextField inputField = new TextField();

    /// Hint shown below the chat, used for progress and errors.
    private final Label statusLabel = new Label();

    /// Creates the assistant page and its initial welcome message.
    public AiAssistantPage() {
        messageBox.setPadding(new Insets(20, 24, 20, 24));

        ScrollPane scrollPane = new ScrollPane(messageBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        inputField.setPromptText(i18n("ai.placeholder"));
        inputField.setOnAction(e -> submit());

        var sendButton = FXUtils.newRaisedButton(i18n("ai.send"));
        sendButton.setOnAction(e -> submit());

        var settingsButton = new javafx.scene.control.Button(i18n("ai.settings"));
        settingsButton.setOnAction(e -> showSettings());

        HBox inputBar = new HBox(8, inputField, sendButton, settingsButton);
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setPadding(new Insets(12, 16, 16, 16));
        HBox.setHgrow(inputField, Priority.ALWAYS);

        statusLabel.setPadding(new Insets(0, 18, 4, 18));

        VBox container = new VBox(scrollPane, statusLabel, inputBar);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        setCenter(container);

        appendBubble(i18n("ai.welcome"), null);
    }

    /// Handles the send action: parses the request and performs the matching action.
    private void submit() {
        String text = inputField.getText();
        if (text == null || text.isBlank())
            return;

        inputField.clear();
        appendBubble(text, null);
        statusLabel.setText(i18n("ai.thinking"));

        Schedulers.io().execute(() -> {
            AIAssistant.Intent intent = AIAssistant.parseWithAi(text);
            runInFX(() -> {
                statusLabel.setText("");
                handleIntent(intent);
            });
        });
    }

    /// Performs the parsed action.
    ///
    /// @param intent the parsed user request
    private void handleIntent(AIAssistant.Intent intent) {
        String kindText = intent.kind() == AIAssistant.Kind.MOD
                ? i18n("ai.intent.mod")
                : i18n("ai.intent.game");
        appendBubble(kindText + (intent.gameVersion() != null ? " · " + intent.gameVersion() : ""), null);

        if (intent.kind() == AIAssistant.Kind.GAME) {
            showGameAction(intent);
        } else {
            searchMods(intent);
        }
    }

    /// Shows the install button for a game version, or opens the download page when no version was found.
    ///
    /// @param intent the parsed request
    private void showGameAction(AIAssistant.Intent intent) {
        @Nullable String version = intent.gameVersion();
        if (version == null) {
            var openButton = FXUtils.newRaisedButton(i18n("ai.action.open_download"));
            openButton.setOnAction(e -> {
                Controllers.getDownloadPage().showGameDownloads();
                Controllers.navigate(Controllers.getDownloadPage());
            });
            appendBubble(i18n("ai.result.no_version"), openButton);
            return;
        }

        var installButton = FXUtils.newRaisedButton(i18n("ai.action.install_game", version));
        installButton.setOnAction(e -> installGame(version));
        appendBubble(i18n("ai.result.game", version), installButton);
    }

    /// Installs a vanilla Minecraft version with the existing dependency manager.
    ///
    /// @param version the Minecraft version id
    private void installGame(String version) {
        DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();
        VersionList<?> versionList = downloadProvider.getVersionListById("game");
        var repository = GameDirectoryManager.getSelectedRepository();

        Task<?> task = versionList.refreshAsync("")
                .thenSupplyAsync(() -> versionList.getVersions("").stream()
                        .filter(it -> it.getGameVersion().equals(version) || it.getSelfVersion().equals(version))
                        .findFirst()
                        .orElseThrow(() -> new java.io.IOException("Version not found: " + version)))
                .thenComposeAsync(remoteVersion -> {
                    String gameVersion = remoteVersion.getGameVersion();
                    return repository.getDependency()
                            .gameBuilder()
                            .name(gameVersion)
                            .gameVersion(gameVersion)
                            .buildAsync();
                })
                .whenComplete(any -> runInFX(repository::refreshVersions));

        Controllers.taskDialog(task, i18n("ai.installing_game", version), TaskCancellationAction.NORMAL);
    }

    /// Searches Modrinth for the requested mod and lists the results.
    ///
    /// @param intent the parsed request
    private void searchMods(AIAssistant.Intent intent) {
        statusLabel.setText(i18n("ai.searching"));

        Schedulers.io().execute(() -> {
            try {
                DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();
                RemoteAddonRepository repository = ModrinthRemoteAddonRepository.MODS;
                RemoteAddonRepository.SearchResult result = repository.search(
                        downloadProvider,
                        intent.gameVersion() != null ? intent.gameVersion() : "",
                        null,
                        0,
                        8,
                        intent.query(),
                        RemoteAddonRepository.SortType.POPULARITY,
                        RemoteAddonRepository.SortOrder.DESC);

                List<RemoteAddon> addons = result.getResults().limit(6).toList();
                runInFX(() -> {
                    statusLabel.setText("");
                    if (addons.isEmpty()) {
                        appendBubble(i18n("ai.result.none"), null);
                        return;
                    }
                    appendBubble(i18n("ai.result.found", addons.size()), null);
                    for (RemoteAddon addon : addons)
                        appendModCard(addon, intent.gameVersion());
                });
            } catch (Exception e) {
                LOG.warning("Failed to search mods", e);
                runInFX(() -> {
                    statusLabel.setText("");
                    appendBubble(i18n("ai.error", String.valueOf(e.getMessage())), null);
                });
            }
        });
    }

    /// Appends one mod search result with an install button.
    ///
    /// @param addon       the mod found on the remote repository
    /// @param gameVersion the requested game version, or `null`
    private void appendModCard(RemoteAddon addon, @Nullable String gameVersion) {
        var card = new VBox(4);
        card.setPadding(new Insets(10, 12, 10, 12));
        card.setMaxWidth(620);

        Label title = new Label(addon.title());
        title.setStyle("-fx-font-weight: bold;");
        Label description = new Label(addon.description());
        description.setWrapText(true);
        description.setMaxWidth(560);

        var installButton = FXUtils.newRaisedButton(i18n("ai.action.install_mod"));
        installButton.setOnAction(e -> installMod(addon, gameVersion));

        card.getChildren().setAll(title, description, installButton);
        messageBox.getChildren().add(card);
    }

    /// Downloads and installs one mod into the selected game instance.
    ///
    /// @param addon       the mod to install
    /// @param gameVersion the requested game version, or `null`
    private void installMod(RemoteAddon addon, @Nullable String gameVersion) {
        statusLabel.setText(i18n("ai.searching"));

        Schedulers.io().execute(() -> {
            try {
                DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();
                List<RemoteAddon.Version> versions = addon.data()
                        .loadVersions(ModrinthRemoteAddonRepository.MODS, downloadProvider)
                        .toList();

                @Nullable String targetVersion = gameVersion != null
                        ? gameVersion
                        : GameDirectoryManager.getSelectedRepository().getSelectedInstance();

                @Nullable RemoteAddon.Version chosen = null;
                for (RemoteAddon.Version candidate : versions) {
                    if (targetVersion != null && candidate.gameVersions().contains(targetVersion)) {
                        chosen = candidate;
                        break;
                    }
                }
                if (chosen == null && !versions.isEmpty())
                    chosen = versions.get(0);

                if (chosen == null) {
                    runInFX(() -> {
                        statusLabel.setText("");
                        appendBubble(i18n("ai.result.none"), null);
                    });
                    return;
                }

                RemoteAddon.Version file = chosen;
                runInFX(() -> {
                    statusLabel.setText("");
                    DownloadPage.download(downloadProvider, GameDirectoryManager.getSelectedRepository(),
                            null, file, "mods");
                });
            } catch (Exception e) {
                LOG.warning("Failed to install mod", e);
                runInFX(() -> {
                    statusLabel.setText("");
                    appendBubble(i18n("ai.error", String.valueOf(e.getMessage())), null);
                });
            }
        });
    }

    /// Appends a chat bubble, optionally with an action button below it.
    ///
    /// @param text   the bubble text
    /// @param action the button placed under the text, or `null`
    private void appendBubble(String text, @Nullable javafx.scene.Node action) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(620);

        VBox bubble = new VBox(8);
        bubble.setPadding(new Insets(10, 12, 10, 12));
        bubble.setMaxWidth(640);
        bubble.getChildren().add(label);
        if (action != null)
            bubble.getChildren().add(action);

        messageBox.getChildren().add(bubble);
    }

    /// Shows the AI endpoint settings dialog.
    private void showSettings() {
        AIAssistant.Config config = AIAssistant.Config.load();

        var endpointField = new TextField(config.endpoint());
        var keyField = new TextField(config.apiKey());
        var modelField = new TextField(config.model());

        VBox content = new VBox(8,
                new Label(i18n("ai.config.endpoint")), endpointField,
                new Label(i18n("ai.config.key")), keyField,
                new Label(i18n("ai.config.model")), modelField);
        content.setPadding(new Insets(12));

        // MessageDialogPane only accepts plain text, so build the form dialog on JFXDialogLayout.
        JFXDialogLayout layout = new JFXDialogLayout();
        layout.setHeading(new HBox(new Label(i18n("ai.config.title"))));
        layout.setBody(content);

        JFXButton saveButton = new JFXButton(i18n("button.ok"));
        saveButton.getStyleClass().add("dialog-accept");
        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        layout.setActions(saveButton, cancelButton);

        cancelButton.setOnAction(e -> layout.fireEvent(new DialogCloseEvent()));
        saveButton.setOnAction(e -> {
            try {
                new AIAssistant.Config(
                        endpointField.getText().trim(),
                        keyField.getText().trim(),
                        modelField.getText().trim()).save();
                layout.fireEvent(new DialogCloseEvent());
                appendBubble(i18n("ai.config.saved"), null);
            } catch (Exception ex) {
                LOG.warning("Failed to save AI configuration", ex);
                appendBubble(i18n("ai.error", String.valueOf(ex.getMessage())), null);
            }
        });

        Controllers.dialog(layout);
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }
}
