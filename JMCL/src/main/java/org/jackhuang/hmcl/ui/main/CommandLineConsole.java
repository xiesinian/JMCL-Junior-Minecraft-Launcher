/*
 * JMCL - Junior Minecraft Launcher
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

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.cli.CLIMain;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// The built-in console opened from the left sidebar, below the AI assistant.
///
/// The console forwards every entered line to [CLIMain#runInteractiveCommand(String)]
/// and appends the captured output to a read-only text area.
@NotNullByDefault
public final class CommandLineConsole {

    private CommandLineConsole() {
    }

    /// Opens the command line console as a dialog.
    public static void show() {
        Controllers.dialog(createPane());
    }

    /// Builds the console pane containing the output area and the input field.
    ///
    /// @return the console pane
    public static Region createPane() {
        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        output.setPrefRowCount(14);
        output.setStyle("-fx-font-family: monospace;");

        TextField input = new TextField();
        input.setPromptText(i18n("settings.launcher.command_line.hint"));

        JFXButton closeButton = new JFXButton(i18n("button.cancel"));
        closeButton.getStyleClass().add("dialog-cancel");

        HBox actions = new HBox(8, closeButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox pane = new VBox(8, output, input, actions);
        pane.setPadding(new Insets(16));
        pane.setPrefWidth(620);

        closeButton.setOnAction(event -> pane.fireEvent(new DialogCloseEvent()));

        input.setOnAction(event -> {
            String line = input.getText();
            if (line == null || line.isBlank()) {
                return;
            }
            input.clear();

            String trimmed = line.trim();
            if ("exit".equalsIgnoreCase(trimmed) || "quit".equalsIgnoreCase(trimmed)) {
                pane.fireEvent(new DialogCloseEvent());
                return;
            }

            output.appendText("JMCL> " + line + "\n");

            Thread worker = new Thread(() -> {
                String result = CLIMain.runInteractiveCommand(line);
                FXUtils.runInFX(() -> output.appendText(result + "\n"));
            }, "jmcl-command-line");
            worker.setDaemon(true);
            worker.start();
        });

        FXUtils.runInFX(input::requestFocus);
        return pane;
    }
}
