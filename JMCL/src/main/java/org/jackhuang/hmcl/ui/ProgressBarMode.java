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
package org.jackhuang.hmcl.ui;

import org.jackhuang.hmcl.setting.SettingsManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/// Controls the simplified progress bar display used while downloading or launching games.
///
/// The mode can be enabled permanently in the settings, or overridden for a single command-line
/// invocation through `--progress` and `--no-progress`.
@NotNullByDefault
public final class ProgressBarMode {

    /// The Minecraft bugs and quirks shown randomly while the progress bar is displayed.
    private static final @Unmodifiable List<String> MINECRAFT_BUGS = List.of(
            "你知道吗？船造在冰上会获得极高速度，玩家称之为「冰船」。",
            "你知道吗？1.9 之前没有攻击冷却，连点鼠标就能打出成倍伤害。",
            "你知道吗？基岩版的红石线可以向上爬一格，而 Java 版不行。",
            "你知道吗？旧版本里船只碰到掉落物会被弹飞，甚至能把玩家甩出地图。",
            "你知道吗？早期版本用打火石点燃 TNT 有概率让游戏直接崩溃。",
            "你知道吗？区块错误会让存档里的方块显示成乱码，重新进入世界即可恢复。",
            "你知道吗？旧版本的水会顺着方块更新的间隙漏进玻璃鱼缸。",
            "你知道吗？1.14 村庄更新后，村民经常卡在门和床之间来回踱步。",
            "你知道吗？基岩版与 Java 版的红石行为长期不一致，很多机器要分别设计。",
            "你知道吗？「零刻农场」利用刻的先后顺序，绕过了作物生长的时间限制。",
            "你知道吗？闪电苦力怕爆炸时会把附近的生物头颅炸下来。",
            "你知道吗？旧版本的矿车叠在一起会互相挤压，把玩家卡进方块里。"
    );

    /// A command-line override for [#isEnabled()], or `null` when the settings value should be used.
    private static @Nullable Boolean override;

    private ProgressBarMode() {
    }

    /// Overrides the progress bar mode for the current process.
    ///
    /// @param enabled `true` to force the mode on, `false` to force it off, `null` to follow the settings
    public static void setOverride(@Nullable Boolean enabled) {
        override = enabled;
    }

    /// Returns whether the simplified progress bar mode is currently active.
    public static boolean isEnabled() {
        if (override != null) {
            return override;
        }
        return SettingsManager.settings().progressBarModeProperty().get();
    }

    /// Returns a random Minecraft bug description.
    public static String randomBug() {
        return MINECRAFT_BUGS.get(ThreadLocalRandom.current().nextInt(MINECRAFT_BUGS.size()));
    }
}
