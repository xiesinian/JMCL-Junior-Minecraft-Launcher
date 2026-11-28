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
package org.jackhuang.hmcl.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.Metadata;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Turns a natural-language request into a launcher action.
///
/// Parsing is performed locally by {@link #parse(String)} so the assistant always works offline.
/// When an OpenAI-compatible endpoint is configured through {@link Config}, {@link #parseWithAi(String)}
/// asks the model first and silently falls back to the local parser on any failure.
@NotNullByDefault
public final class AIAssistant {

    /// What the user wants the launcher to download.
    public enum Kind {
        /// Install a Minecraft game version.
        GAME,
        /// Search and install a mod.
        MOD
    }

    /// A parsed user request.
    ///
    /// @param kind        the requested action
    /// @param query       the cleaned search keyword, empty when nothing remains
    /// @param gameVersion the Minecraft version mentioned by the user, or `null`
    /// @param modLoader   the mod loader mentioned by the user, or `null`
    public record Intent(Kind kind, String query, @Nullable String gameVersion, @Nullable String modLoader) {
    }

    /// Persisted configuration of the remote AI model.
    ///
    /// @param endpoint the OpenAI-compatible chat-completions endpoint
    /// @param apiKey   the bearer token used to authenticate, may be empty
    /// @param model    the model name
    public record Config(String endpoint, String apiKey, String model) {

        /// The configuration file used when no environment variable overrides it.
        public static final Path CONFIG_FILE = Metadata.HMCL_LOCAL_HOME.resolve("ai.json");

        /// Reads the stored configuration, falling back to environment variables and defaults.
        ///
        /// @return the effective configuration
        public static Config load() {
            String endpoint = System.getenv("JMCL_AI_ENDPOINT");
            String apiKey = System.getenv("JMCL_AI_KEY");
            String model = System.getenv("JMCL_AI_MODEL");

            if (Files.isRegularFile(CONFIG_FILE)) {
                try {
                    JsonObject json = JsonParser.parseString(Files.readString(CONFIG_FILE, StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    if (endpoint == null && json.has("endpoint"))
                        endpoint = json.get("endpoint").getAsString();
                    if (apiKey == null && json.has("apiKey"))
                        apiKey = json.get("apiKey").getAsString();
                    if (model == null && json.has("model"))
                        model = json.get("model").getAsString();
                } catch (IOException | RuntimeException e) {
                    LOG.warning("Failed to read AI configuration", e);
                }
            }

            return new Config(
                    endpoint != null && !endpoint.isBlank() ? endpoint : "https://api.openai.com/v1/chat/completions",
                    apiKey != null ? apiKey : "",
                    model != null && !model.isBlank() ? model : "gpt-4o-mini");
        }

        /// Returns whether the configuration is usable for remote requests.
        ///
        /// @return `true` when an API key is configured
        public boolean usable() {
            return !apiKey.isBlank();
        }

        /// Persists this configuration to {@link #CONFIG_FILE}.
        ///
        /// @throws IOException when the file cannot be written
        public void save() throws IOException {
            JsonObject json = new JsonObject();
            json.addProperty("endpoint", endpoint);
            json.addProperty("apiKey", apiKey);
            json.addProperty("model", model);
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, json.toString(), StandardCharsets.UTF_8);
        }
    }

    /// Matches a Minecraft version such as `1.20` or `1.20.1`.
    private static final Pattern GAME_VERSION = Pattern.compile("\\b(1\\.\\d{1,2}(?:\\.\\d{1,2})?)\\b");

    /// Keywords that indicate the user is talking about content rather than the game itself.
    private static final String[] MOD_KEYWORDS = {
            "模组", "mod", "插件", "光影", "材质", "整合包", "jei", "rei", "sodium", "iris",
            "optifine", "fabric api", "小地图"
    };

    /// Words removed from the keyword query before searching.
    private static final String[] NOISE_WORDS = {
            "帮我", "请", "麻烦", "我想", "我要", "想要", "下载", "安装", "装个", "装一个", "一个",
            "最新的", "最新", "版本", "游戏", "模组", "mod", "客户端", "服务端", "吧", "谢谢",
            "可以", "能", "给我", "找个", "找", "搜索", "搜", "一下", "的"
    };

    /// Parses a request without any network access.
    ///
    /// @param input the raw user text
    /// @return the parsed intent, never `null`
    public static Intent parse(String input) {
        String text = input == null ? "" : input.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        String gameVersion = null;
        Matcher matcher = GAME_VERSION.matcher(lower);
        if (matcher.find())
            gameVersion = matcher.group(1);

        String modLoader = null;
        for (String loader : new String[]{"neoforge", "fabric", "forge", "quilt"}) {
            if (lower.contains(loader)) {
                modLoader = loader;
                break;
            }
        }

        Kind kind = Kind.GAME;
        for (String keyword : MOD_KEYWORDS) {
            if (lower.contains(keyword)) {
                kind = Kind.MOD;
                break;
            }
        }

        return new Intent(kind, cleanQuery(lower, gameVersion, modLoader), gameVersion, modLoader);
    }

    /// Parses a request, asking the configured model first and falling back to {@link #parse(String)}.
    ///
    /// @param input the raw user text
    /// @return the parsed intent, never `null`
    public static Intent parseWithAi(String input) {
        Config config = Config.load();
        if (!config.usable())
            return parse(input);

        try {
            Intent intent = requestModel(config, input);
            return intent != null ? intent : parse(input);
        } catch (Exception e) {
            LOG.warning("AI request failed, falling back to the local parser", e);
            return parse(input);
        }
    }

    /// Returns whether the remote model is configured.
    ///
    /// @return `true` when requests will be sent to a model
    public static boolean isAiEnabled() {
        return Config.load().usable();
    }

    /// Sends the request to an OpenAI-compatible endpoint and parses the returned JSON answer.
    ///
    /// @param config the effective configuration
    /// @param input  the raw user text
    /// @return the parsed intent, or `null` when the model reply cannot be understood
    /// @throws IOException          when the request fails
    /// @throws InterruptedException when the calling thread is interrupted
    private static @Nullable Intent requestModel(Config config, String input)
            throws IOException, InterruptedException {
        String prompt = """
                You convert Minecraft launcher requests into JSON.
                Reply with JSON only, no markdown, using this schema:
                {"action":"game"|"mod","query":"<search keywords>","version":"<like 1.20.1 or empty>","loader":"fabric|forge|neoforge|quilt|empty"}
                "game" means the user wants to install a Minecraft version.
                "mod" means the user wants to download a mod.
                User request: """ + input;

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("temperature", 0);
        var messages = new com.google.gson.JsonArray();
        messages.add(message);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.endpoint()))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2)
            throw new IOException("AI endpoint returned HTTP " + response.statusCode());

        String content = JsonParser.parseString(response.body())
                .getAsJsonObject()
                .getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start)
            return null;

        JsonObject answer = JsonParser.parseString(content.substring(start, end + 1)).getAsJsonObject();
        Kind kind = "mod".equalsIgnoreCase(answer.has("action") ? answer.get("action").getAsString() : "")
                ? Kind.MOD
                : Kind.GAME;

        String query = answer.has("query") ? answer.get("query").getAsString().trim() : "";
        String version = answer.has("version") ? answer.get("version").getAsString().trim() : "";
        String loader = answer.has("loader") ? answer.get("loader").getAsString().trim().toLowerCase(Locale.ROOT) : "";

        return new Intent(kind, query, version.isEmpty() ? null : version, loader.isEmpty() ? null : loader);
    }

    /// Removes filler words, the detected version and loader from the text, leaving search keywords.
    ///
    /// @param text        the lower-cased user text
    /// @param gameVersion the version detected in the text, or `null`
    /// @param modLoader   the loader detected in the text, or `null`
    /// @return the cleaned keyword string
    private static String cleanQuery(String text, @Nullable String gameVersion, @Nullable String modLoader) {
        String result = text;
        if (gameVersion != null)
            result = result.replace(gameVersion, " ");
        if (modLoader != null)
            result = result.replace(modLoader, " ");

        for (String word : NOISE_WORDS)
            result = result.replace(word, " ");

        result = result.replaceAll("[\\p{Punct}，。、！？：；“”‘’（）【】]", " ");
        return result.replaceAll("\\s+", " ").trim();
    }

    /// Prevents instantiation.
    private AIAssistant() {
    }
}
