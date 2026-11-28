/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.*;
import javafx.animation.PauseTransition;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.EasterEggs;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.Nullable;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.InputStream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class AboutPage extends SpinnerPane {

    private final WeakListenerHolder holder = new WeakListenerHolder();

    public AboutPage() {
        VBox content = new VBox();
        content.getStyleClass().add("spinner-pane-content");
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setContent(scrollPane);

        ComponentList about = new ComponentList();
        {
            var launcher = new LineButton();
            launcher.setLargeTitle(true);
            launcher.setTrailingIcon(SVG.OPEN_IN_NEW);
            launcher.setTitle(Metadata.FULL_NAME);
            launcher.setSubtitle(Metadata.VERSION);

            var icon = new ImageContainer(32, 32);
            icon.setImage(FXUtils.newBuiltinImage("/assets/img/icon.png"));

            // easter egg: click the icon five times to open the hidden snake game
            int[] iconClicks = {0};
            icon.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    iconClicks[0]++;
                    if (iconClicks[0] >= 5) {
                        iconClicks[0] = 0;
                        EasterEggs.showSnakeGame();
                    }
                }
                event.consume();
            });
            launcher.setLeading(icon);

            // easter egg: click the version text seven times to show a random Minecraft trivia
            int[] versionClicks = {0};
            PauseTransition singleClick = new PauseTransition(Duration.millis(300));
            singleClick.setOnFinished(event -> {
                if (versionClicks[0] == 1)
                    FXUtils.openLink(Metadata.PUBLISH_URL);
                versionClicks[0] = 0;
            });
            launcher.setOnAction(event -> {
                versionClicks[0]++;
                if (versionClicks[0] >= 7) {
                    singleClick.stop();
                    versionClicks[0] = 0;
                    EasterEggs.showVersionTrivia();
                } else {
                    singleClick.playFromStart();
                }
            });

            var author = LineButton.createExternalLinkButton("https://space.bilibili.com/1445341");
            author.setLargeTitle(true);
            author.setLeading(FXUtils.newBuiltinImage("/assets/img/yellow_fish.png"));
            author.setTitle("huanghongxun");
            author.setSubtitle(i18n("about.author.statement"));

            about.getContent().setAll(launcher, author);
        }

        ComponentList thanks = loadIconedTwoLineList("/assets/about/thanks.json", "jmcl");
        ComponentList upstreamThanks = loadIconedTwoLineList("/assets/about/thanks.json", "hmcl");

        ComponentList deps = loadIconedTwoLineList("/assets/about/deps.json");

        ComponentList legal = new ComponentList();
        {
            var copyright = new LineButton();
            copyright.setLargeTitle(true);
            copyright.setTitle(i18n("about.copyright"));
            copyright.setSubtitle(i18n("about.copyright.statement"));

            var claim = LineButton.createExternalLinkButton(Metadata.EULA_URL);
            claim.setLargeTitle(true);
            claim.setTitle(i18n("about.claim"));
            claim.setSubtitle(i18n("about.claim.statement"));

            var forkNotice = new LineButton();
            forkNotice.setLargeTitle(true);
            forkNotice.setTitle(i18n("about.fork_notice"));
            forkNotice.setSubtitle(i18n("about.fork_notice.statement"));

            var upstream = LineButton.createExternalLinkButton(Metadata.UPSTREAM_URL);
            upstream.setLargeTitle(true);
            upstream.setTitle(i18n("about.upstream"));
            upstream.setSubtitle(i18n("about.upstream.statement"));

            var license = LineButton.createExternalLinkButton(Metadata.LICENSE_URL);
            license.setLargeTitle(true);
            license.setTitle(i18n("about.license"));
            license.setSubtitle(i18n("about.license.statement"));

            var openSource = LineButton.createExternalLinkButton(Metadata.JMCL_REPO_URL);
            openSource.setLargeTitle(true);
            openSource.setTitle(i18n("about.open_source"));
            openSource.setSubtitle(i18n("about.open_source.statement"));

            legal.getContent().setAll(copyright, claim, forkNotice, license, upstream, openSource);
        }

        content.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("about")),
                about,
                ComponentList.createComponentListTitle(i18n("about.thanks_to")),
                createDeveloperColumns(thanks, upstreamThanks),
                ComponentList.createComponentListTitle(i18n("about.dependency")),
                deps,
                ComponentList.createComponentListTitle(i18n("about.legal")),
                legal
        );
    }

    /// Lays out the JMCL developers and the upstream HMCL developers in two columns.
    private static HBox createDeveloperColumns(ComponentList jmcl, ComponentList hmcl) {
        VBox left = new VBox(10,
                ComponentList.createComponentListTitle(i18n("about.developers.jmcl")),
                jmcl);
        VBox right = new VBox(10,
                ComponentList.createComponentListTitle(i18n("about.developers.hmcl")),
                hmcl);

        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        left.setMaxWidth(Double.MAX_VALUE);
        right.setMaxWidth(Double.MAX_VALUE);

        HBox columns = new HBox(10, left, right);
        columns.setPrefWidth(Double.MAX_VALUE);
        return columns;
    }

    private static Image loadImage(String url) {
        return url.startsWith("/")
                ? FXUtils.newBuiltinImage(url)
                : new Image(url);
    }

    private ComponentList loadIconedTwoLineList(String path) {
        return loadIconedTwoLineList(path, null);
    }

    /// Loads a contributor list, optionally keeping only entries of the given group.
    ///
    /// Entries without a `group` field are treated as upstream HMCL contributors, so passing
    /// `"hmcl"` also returns them. Passing `null` keeps every entry.
    private ComponentList loadIconedTwoLineList(String path, @Nullable String group) {
        ComponentList componentList = new ComponentList();

        InputStream input = FXUtils.class.getResourceAsStream(path);
        if (input == null) {
            LOG.warning("Resources not found: " + path);
            return componentList;
        }

        try {
            JsonArray array = JsonUtils.fromJsonFully(input, JsonArray.class);

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();

                String entryGroup = obj.has("group") && obj.get("group").isJsonPrimitive()
                        ? obj.get("group").getAsString()
                        : "hmcl";
                if (group != null && !group.equals(entryGroup)) {
                    continue;
                }

                var button = new LineButton();
                button.setLargeTitle(true);

                if (obj.get("externalLink") instanceof JsonPrimitive externalLink) {
                    button.setTrailingIcon(SVG.OPEN_IN_NEW);

                    String link = externalLink.getAsString();
                    button.setOnAction(event -> FXUtils.openLink(link));
                }

                if (obj.has("image")) {
                    JsonElement image = obj.get("image");
                    if (image.isJsonPrimitive()) {
                        var imageView = new ImageContainer(32, 32);
                        imageView.setImage(loadImage(image.getAsString()));
                        imageView.setMouseTransparent(true);


                        button.setLeading(imageView);
                    } else if (image.isJsonObject()) {
                        holder.add(FXUtils.onWeakChangeAndOperate(Themes.darkModeProperty(), darkMode -> button.setLeading(darkMode
                                ? loadImage(image.getAsJsonObject().get("dark").getAsString())
                                : loadImage(image.getAsJsonObject().get("light").getAsString())
                        )));
                    }
                }

                if (obj.get("title") instanceof JsonPrimitive title)
                    button.setTitle(title.getAsString());
                else if (obj.get("titleLocalized") instanceof JsonPrimitive titleLocalized)
                    button.setTitle(i18n(titleLocalized.getAsString()));

                if (obj.get("subtitle") instanceof JsonPrimitive subtitle)
                    button.setSubtitle(subtitle.getAsString());
                else if (obj.get("subtitleLocalized") instanceof JsonPrimitive subtitleLocalized)
                    button.setSubtitle(i18n(subtitleLocalized.getAsString()));

                componentList.getContent().add(button);
            }
        } catch (IOException | JsonParseException e) {
            LOG.warning("Failed to load list: " + path, e);
        }

        return componentList;
    }
}
