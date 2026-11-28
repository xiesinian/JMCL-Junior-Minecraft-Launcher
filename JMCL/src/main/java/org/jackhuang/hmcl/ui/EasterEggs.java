/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.setting.LauncherState;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.decorator.Decorator;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayDeque;
import java.util.Random;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 收集了若干与启动器玩法无关、只为了有趣的小彩蛋。
///
/// 所有方法都必须在 JavaFX 应用线程上调用。
///
/// @author HMCL contributors
@NotNullByDefault
public final class EasterEggs {

    /// Konami 秘籍的按键序列。
    private static final KeyCode[] KONAMI_CODE = {
            KeyCode.UP, KeyCode.UP, KeyCode.DOWN, KeyCode.DOWN,
            KeyCode.LEFT, KeyCode.RIGHT, KeyCode.LEFT, KeyCode.RIGHT,
            KeyCode.B, KeyCode.A
    };

    /// 苦力怕脸部图案，`1` 表示黑色像素。
    private static final String @Unmodifiable [] CREEPER_FACE = {
            "00000000",
            "01100110",
            "01100110",
            "00011000",
            "00111100",
            "01100110",
            "01100110",
            "00000000"
    };

    /// 触发里程碑彩蛋的启动次数。
    private static final int[] LAUNCH_MILESTONES = {10, 23, 100, 365, 1000, 2333};

    /// 随机数生成器，用于决定彩蛋动画的随机位置与时长。
    private static final Random RANDOM = new Random();

    /// 贪吃蛇格子边长（像素）。
    private static final int CELL_SIZE = 20;

    /// 贪吃蛇横向格子数。
    private static final int SNAKE_COLUMNS = 24;

    /// 贪吃蛇纵向格子数。
    private static final int SNAKE_ROWS = 16;

    private EasterEggs() {
    }

    // ==== Konami 秘籍 ====

    /// 在指定窗口上监听 Konami 秘籍，成功后播放苦力怕雨。
    ///
    /// 监听器挂在窗口而不是内容节点上，这样按键事件被重定向到当前页面时不会重复计数。
    ///
    /// @param decorator 接收按键事件的窗口装饰器
    public static void installKonamiListener(Decorator decorator) {
        int[] progress = {0};
        decorator.getPrimaryStage().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            KeyCode code = event.getCode();
            if (code == KONAMI_CODE[progress[0]]) {
                progress[0]++;
                if (progress[0] == KONAMI_CODE.length) {
                    progress[0] = 0;
                    playCreeperRain(decorator);
                }
            } else {
                // 允许序列中的第一个按键同时作为新序列的起点。
                progress[0] = code == KONAMI_CODE[0] ? 1 : 0;
            }
        });
    }

    /// 在窗口内播放一次苦力怕雨动画。
    ///
    /// @param decorator 承载动画的窗口装饰器
    public static void playCreeperRain(Decorator decorator) {
        double width = decorator.getWidth() > 0 ? decorator.getWidth() : 900;
        double height = decorator.getHeight() > 0 ? decorator.getHeight() : 600;

        StackPane overlay = new StackPane();
        overlay.setMouseTransparent(true);

        Pane rainPane = new Pane();
        rainPane.setMouseTransparent(true);
        overlay.getChildren().add(rainPane);
        decorator.getContainer().add(overlay);

        Controllers.showToast(i18n("easter_egg.konami"));

        int total = 24;
        int[] remaining = {total};
        for (int i = 0; i < total; i++) {
            double size = 36 + RANDOM.nextInt(30);
            Node creeper = createCreeperFace(size);
            creeper.setLayoutX(RANDOM.nextDouble() * Math.max(1, width - size));

            Duration duration = Duration.seconds(2.2 + RANDOM.nextDouble() * 2.0);
            TranslateTransition fall = new TranslateTransition(duration, creeper);
            fall.setFromY(-size - RANDOM.nextInt(200));
            fall.setToY(height + size);
            fall.setInterpolator(Interpolator.LINEAR);
            fall.setDelay(Duration.millis(RANDOM.nextInt(900)));
            fall.setOnFinished(event -> {
                rainPane.getChildren().remove(creeper);
                if (--remaining[0] <= 0) {
                    decorator.getContainer().remove(overlay);
                }
            });

            RotateTransition spin = new RotateTransition(duration, creeper);
            spin.setFromAngle(RANDOM.nextInt(180) - 90);
            spin.setToAngle(RANDOM.nextInt(720) - 360);

            rainPane.getChildren().add(creeper);
            fall.play();
            spin.play();
        }
    }

    /// 创建一个由像素方块拼成的苦力怕头像。
    ///
    /// @param size 头像边长（像素）
    /// @return 不可由鼠标交互的苦力怕头像节点
    private static Node createCreeperFace(double size) {
        Pane face = new Pane();
        face.setPrefSize(size, size);
        face.setMinSize(size, size);
        face.setMaxSize(size, size);

        Rectangle background = new Rectangle(size, size, Color.web("#6cbf59"));
        face.getChildren().add(background);

        double pixel = size / 8.0;
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                if (CREEPER_FACE[row].charAt(column) == '1') {
                    Rectangle block = new Rectangle(column * pixel, row * pixel, pixel, pixel);
                    block.setFill(Color.web("#151515"));
                    face.getChildren().add(block);
                }
            }
        }
        return face;
    }

    // ==== 窗口跳舞 ====

    /// 让指定节点做一次摇摆动画，模拟窗口在跳舞。
    ///
    /// @param node 参与动画的节点，通常是整个启动器窗口内容
    public static void playWindowDance(Node node) {
        Timeline dance = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(node.rotateProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(node.translateXProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(), 1, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(90),
                        new KeyValue(node.rotateProperty(), 6, Interpolator.EASE_BOTH),
                        new KeyValue(node.translateXProperty(), 14, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleXProperty(), 1.02, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(), 1.02, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(node.rotateProperty(), -6, Interpolator.EASE_BOTH),
                        new KeyValue(node.translateXProperty(), -14, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(270),
                        new KeyValue(node.rotateProperty(), 4, Interpolator.EASE_BOTH),
                        new KeyValue(node.translateXProperty(), 9, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(360),
                        new KeyValue(node.rotateProperty(), -3, Interpolator.EASE_BOTH),
                        new KeyValue(node.translateXProperty(), -6, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(450),
                        new KeyValue(node.rotateProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(node.translateXProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleXProperty(), 1, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(), 1, Interpolator.EASE_BOTH))
        );
        dance.play();
    }

    // ==== 版本号冷知识 ====

    /// 显示一条随机的 Minecraft 冷知识。
    public static void showVersionTrivia() {
        int index = RANDOM.nextInt(8) + 1;
        Controllers.dialog(i18n("easter_egg.trivia." + index), i18n("easter_egg.trivia.title"), MessageType.INFO);
    }

    // ==== 贪吃蛇小游戏 ====

    /// 打开隐藏的贪吃蛇小游戏窗口。
    public static void showSnakeGame() {
        Canvas canvas = new Canvas(SNAKE_COLUMNS * CELL_SIZE, SNAKE_ROWS * CELL_SIZE);
        // Canvas 默认不可获得焦点，必须显式开启，否则收不到键盘事件
        canvas.setFocusTraversable(true);

        Label scoreLabel = new Label();
        scoreLabel.setTextFill(Color.WHITE);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 10, 8, 10));
        Label hint = new Label(i18n("easter_egg.snake.hint"));
        hint.setTextFill(Color.web("#bdbdbd"));
        HBox.setMargin(hint, new Insets(0, 0, 0, 12));
        header.getChildren().addAll(scoreLabel, hint);

        VBox root = new VBox(header, canvas);
        root.setStyle("-fx-background-color: #1b1b1f;");
        root.setFocusTraversable(true);

        SnakeGame game = new SnakeGame(canvas, scoreLabel);

        Stage stage = new Stage();
        stage.initOwner(Controllers.getStage());
        stage.initModality(Modality.NONE);
        stage.setTitle(i18n("easter_egg.snake.title"));
        stage.setResizable(false);

        Scene scene = new Scene(root);
        scene.setOnMousePressed(event -> canvas.requestFocus());

        // 在窗口级别捕获按键，避免焦点不在 Canvas 上时游戏收不到输入
        stage.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                game.stop();
                stage.close();
                return;
            }
            game.onKeyPressed(event.getCode());
            event.consume();
        });

        stage.setScene(scene);
        stage.setOnShown(event -> canvas.requestFocus());
        stage.setOnHidden(event -> game.stop());
        stage.show();

        stage.toFront();
        stage.requestFocus();
        Platform.runLater(canvas::requestFocus);
        game.start();
    }

    /// 贪吃蛇的游戏状态与绘制逻辑。
    private static final class SnakeGame {
        private final Canvas canvas;
        private final Label scoreLabel;
        private final ArrayDeque<int[]> snake = new ArrayDeque<>();
        private final AnimationTimer timer;

        private int directionX = 1;
        private int directionY;
        private int pendingDirectionX = 1;
        private int pendingDirectionY;
        private int[] food;
        private int score;
        private boolean gameOver;
        private boolean started;
        private long lastStepNanos;

        SnakeGame(Canvas canvas, Label scoreLabel) {
            this.canvas = canvas;
            this.scoreLabel = scoreLabel;
            this.timer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (lastStepNanos == 0) {
                        lastStepNanos = now;
                    }
                    if (now - lastStepNanos >= 130_000_000L) {
                        lastStepNanos = now;
                        step();
                    }
                }
            };
            reset();
        }

        void start() {
            timer.start();
        }

        void stop() {
            timer.stop();
        }

        /// 重新开始时清空蛇身并放置新的食物。
        private void reset() {
            snake.clear();
            int startX = SNAKE_COLUMNS / 2;
            int startY = SNAKE_ROWS / 2;
            for (int i = 0; i < 4; i++) {
                snake.addFirst(new int[]{startX - i, startY});
            }
            directionX = 1;
            directionY = 0;
            pendingDirectionX = 1;
            pendingDirectionY = 0;
            score = 0;
            gameOver = false;
            started = false;
            lastStepNanos = 0;
            placeFood();
            updateScore();
            draw();
        }

        /// 处理方向键与 WASD 输入。
        void onKeyPressed(KeyCode code) {
            switch (code) {
                case UP, W -> requestDirection(0, -1);
                case DOWN, S -> requestDirection(0, 1);
                case LEFT, A -> requestDirection(-1, 0);
                case RIGHT, D -> requestDirection(1, 0);
                case ENTER, SPACE -> {
                    if (gameOver) {
                        reset();
                    }
                }
                default -> {
                }
            }
        }

        /// 记录下一次移动方向，禁止一步之内掉头。
        private void requestDirection(int x, int y) {
            if (gameOver) {
                return;
            }
            if (directionX == -x && directionY == -y) {
                return;
            }
            pendingDirectionX = x;
            pendingDirectionY = y;
            started = true;
        }

        /// 推进一帧：移动蛇头，判断吃食物与碰撞。
        private void step() {
            if (gameOver || !started) {
                return;
            }

            directionX = pendingDirectionX;
            directionY = pendingDirectionY;

            int[] head = snake.peekFirst();
            int nextX = head[0] + directionX;
            int nextY = head[1] + directionY;

            boolean hitWall = nextX < 0 || nextX >= SNAKE_COLUMNS || nextY < 0 || nextY >= SNAKE_ROWS;
            boolean hitBody = false;
            for (int[] body : snake) {
                if (body[0] == nextX && body[1] == nextY) {
                    hitBody = true;
                    break;
                }
            }

            if (hitWall || hitBody) {
                gameOver = true;
                draw();
                return;
            }

            snake.addFirst(new int[]{nextX, nextY});
            if (food != null && nextX == food[0] && nextY == food[1]) {
                score++;
                updateScore();
                placeFood();
            } else {
                snake.removeLast();
            }

            draw();
        }

        /// 在空白格子上放置食物。
        private void placeFood() {
            while (true) {
                int x = RANDOM.nextInt(SNAKE_COLUMNS);
                int y = RANDOM.nextInt(SNAKE_ROWS);
                boolean occupied = false;
                for (int[] body : snake) {
                    if (body[0] == x && body[1] == y) {
                        occupied = true;
                        break;
                    }
                }
                if (!occupied) {
                    food = new int[]{x, y};
                    return;
                }
            }
        }

        /// 刷新分数文字。
        private void updateScore() {
            scoreLabel.setText(i18n("easter_egg.snake.score") + " " + score);
        }

        /// 绘制背景、食物、蛇身与结束提示。
        private void draw() {
            GraphicsContext gc = canvas.getGraphicsContext2D();

            gc.setFill(Color.web("#26262c"));
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

            gc.setStroke(Color.web("#32323a"));
            gc.setLineWidth(1);
            for (int x = 0; x <= SNAKE_COLUMNS; x++) {
                gc.strokeLine(x * CELL_SIZE, 0, x * CELL_SIZE, canvas.getHeight());
            }
            for (int y = 0; y <= SNAKE_ROWS; y++) {
                gc.strokeLine(0, y * CELL_SIZE, canvas.getWidth(), y * CELL_SIZE);
            }

            if (food != null) {
                gc.setFill(Color.web("#d9534f"));
                gc.fillRoundRect(food[0] * CELL_SIZE + 2, food[1] * CELL_SIZE + 2,
                        CELL_SIZE - 4, CELL_SIZE - 4, 6, 6);
            }

            boolean isHead = true;
            for (int[] body : snake) {
                gc.setFill(isHead ? Color.web("#8bd66f") : Color.web("#6cbf59"));
                gc.fillRoundRect(body[0] * CELL_SIZE + 1, body[1] * CELL_SIZE + 1,
                        CELL_SIZE - 2, CELL_SIZE - 2, 5, 5);
                isHead = false;
            }

            if (gameOver) {
                gc.setFill(Color.rgb(0, 0, 0, 0.55));
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                gc.setFill(Color.WHITE);
                gc.setFont(javafx.scene.text.Font.font(22));
                gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
                gc.fillText(i18n("easter_egg.snake.game_over"), canvas.getWidth() / 2, canvas.getHeight() / 2 - 8);
                gc.setFont(javafx.scene.text.Font.font(14));
                gc.fillText(i18n("easter_egg.snake.restart"), canvas.getWidth() / 2, canvas.getHeight() / 2 + 22);
            } else if (!started) {
                gc.setFill(Color.WHITE);
                gc.setFont(javafx.scene.text.Font.font(16));
                gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
                gc.fillText(i18n("easter_egg.snake.start"), canvas.getWidth() / 2, canvas.getHeight() / 2 + 6);
            }
        }
    }

    // ==== 启动次数彩蛋 ====

    /// 记录一次启动，并在达到特定次数时播放庆祝彩蛋。
    ///
    /// 应在启动器窗口显示之后调用。
    public static void onLauncherStarted() {
        LauncherState state = SettingsManager.state();
        int count = state.getLaunchCount() + 1;
        state.setLaunchCount(count);

        boolean milestone = false;
        for (int value : LAUNCH_MILESTONES) {
            if (value == count) {
                milestone = true;
                break;
            }
        }
        if (!milestone) {
            return;
        }

        Platform.runLater(() -> {
            Controllers.dialog(i18n("easter_egg.launch." + count), i18n("easter_egg.launch.title"), MessageType.INFO);
            Decorator decorator = Controllers.getDecorator().getDecorator();
            playCreeperRain(decorator);
        });
    }
}
