package com.claudeplugin.jmeter.service;

import com.google.gson.*;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Handles all communication with the Anthropic Claude API.
 * Supports both synchronous calls and streaming (for real-time output in the panel).
 */
public class ClaudeApiService {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-sonnet-4-20250514";
    private static final MediaType JSON  = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;
    private String apiKey;

    public ClaudeApiService(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void setApiKey(String key) { this.apiKey = key; }

    // ─── 1. Generate JMX from natural language or HAR ───────────────────────

    public String generateJmxScript(String userRequirement) throws IOException {
        String system = """
            You are an expert Apache JMeter script engineer.
            When given a requirement or HTTP traffic, output a complete, valid JMeter JMX XML test plan.
            Include: ThreadGroup, HTTPSamplerProxy for each request, ResponseAssertion, 
            RegexExtractor/JSONPathExtractor for correlations, CSVDataSet for parameterization,
            ConstantTimer for think time, ResultCollector listener.
            Add XML comments explaining each section. Output ONLY the XML, no preamble.
            """;
        return callClaude(system, userRequirement);
    }

    // ─── 2. Analyse HAR and return structured HTTP calls + suggestions ───────

    public String analyzeHar(String harJson) throws IOException {
        String system = """
            You are a JMeter expert analyzing a HAR (HTTP Archive) file.
            From the HAR JSON, extract all HTTP requests and:
            1. List every unique URL, method, headers, body
            2. Identify dynamic/session values that need CORRELATION 
               (tokens, session IDs, CSRF, ViewState, timestamps, GUIDs)
            3. Identify hardcoded values that should be PARAMETERIZED 
               (usernames, search terms, IDs in URLs)
            4. Suggest JMeter extractors (Regex, JSONPath, CSS/JQuery, Boundary) for each correlation
            5. Then produce a complete JMX test plan covering all discovered requests
            
            Respond in this exact format:
            ---SUMMARY---
            <plain English summary>
            ---CORRELATIONS---
            <list of dynamic values with extractor suggestions>
            ---PARAMETERS---
            <list of values to parameterize with variable names>
            ---JMX---
            <complete JMX XML>
            """;
        String userMsg = "Analyze this HAR file and generate a JMeter test plan:\n" + harJson;
        return callClaude(system, userMsg);
    }

    // ─── 3. Suggest correlations from an existing JMX ────────────────────────

    public String suggestCorrelations(String jmxContent) throws IOException {
        String system = """
            You are a JMeter correlation expert. Analyze this JMX test plan and identify:
            1. Hardcoded dynamic values (session tokens, auth tokens, IDs) that will break on replay
            2. For each: suggest the correct JMeter extractor type (Regex, JSONPath, Boundary, XPath2)
            3. Provide the exact extractor config (regex pattern, JSON path, etc.)
            4. Show where to add the extractor (after which sampler)
            5. Show the ${variable} reference to use in subsequent requests
            Format as a numbered list with clear before/after examples.
            """;
        return callClaude(system, "Find correlations in this JMX:\n" + jmxContent);
    }

    // ─── 4. Suggest parameterization ─────────────────────────────────────────

    public String suggestParameterization(String jmxContent) throws IOException {
        String system = """
            You are a JMeter parameterization expert. Analyze this JMX test plan and:
            1. Identify all hardcoded test data (usernames, passwords, search terms, IDs, URLs)
            2. For each: suggest the variable name and whether to use CSV Data Set Config or User Defined Variables
            3. Provide the exact CSV header row and sample data rows
            4. Show the updated sampler config using ${variable} syntax
            Format clearly with before/after examples for each suggestion.
            """;
        return callClaude(system, "Suggest parameterization for this JMX:\n" + jmxContent);
    }

    // ─── 5. Free chat / Q&A about JMeter ─────────────────────────────────────

    public String askJmeterQuestion(String question, String context) throws IOException {
        String system = """
            You are an expert JMeter consultant. Answer questions about JMeter scripting,
            performance testing, load test design, and test plan optimization.
            Be specific, practical, and provide JMX snippets when helpful.
            Context about the current test plan will be provided if available.
            """;
        String userMsg = context.isEmpty() ? question : "Context (current JMX):\n" + context + "\n\nQuestion: " + question;
        return callClaude(system, userMsg);
    }

    // ─── Public raw call (used by OptimizerTab and ChatTab) ──────────────────

    public String callRaw(String systemPrompt, String userMessage) throws IOException {
        return callClaude(systemPrompt, userMessage);
    }

    // ─── Core API call ────────────────────────────────────────────────────────

    private String callClaude(String systemPrompt, String userMessage) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("max_tokens", 4096);
        body.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", userMessage);
        messages.add(msg);
        body.add("messages", messages);

        Request request = new Request.Builder()
                .url(API_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(RequestBody.create(gson.toJson(body), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "unknown error";
                throw new IOException("Claude API error " + response.code() + ": " + err);
            }
            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            return json.getAsJsonArray("content")
                       .get(0).getAsJsonObject()
                       .get("text").getAsString();
        }
    }
}
