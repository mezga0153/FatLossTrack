package com.fatlosstrack.data.remote.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

interface BackendApi {
    suspend fun chat(token: String, context: JsonObject, prompt: String): AiResponse
    suspend fun mealPhoto(token: String, context: JsonObject, imageBase64: String): AiResponse
    suspend fun menuScan(token: String, context: JsonObject, imageBase64: String): AiResponse
    suspend fun fridgeScan(token: String, context: JsonObject, imageBase64: String): AiResponse
    suspend fun insights(token: String, context: JsonObject): AiResponse
}

@Serializable
data class AiResponse(val message: String)

@Serializable
data class ChatRequest(val context: JsonObject, val prompt: String)

@Serializable
data class VisionRequest(val context: JsonObject, val image_base64: String)

@Serializable
data class InsightsRequest(val context: JsonObject)

class BackendApiImpl(
    private val client: HttpClient,
    private val baseUrl: String,
) : BackendApi {

    private fun HttpRequestBuilder.authHeader(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
    }

    override suspend fun chat(token: String, context: JsonObject, prompt: String): AiResponse {
        return client.post("$baseUrl/api/ai/chat") {
            authHeader(token)
            setBody(ChatRequest(context, prompt))
        }.body()
    }

    override suspend fun mealPhoto(token: String, context: JsonObject, imageBase64: String): AiResponse {
        return client.post("$baseUrl/api/ai/meal-photo") {
            authHeader(token)
            setBody(VisionRequest(context, imageBase64))
        }.body()
    }

    override suspend fun menuScan(token: String, context: JsonObject, imageBase64: String): AiResponse {
        return client.post("$baseUrl/api/ai/menu-scan") {
            authHeader(token)
            setBody(VisionRequest(context, imageBase64))
        }.body()
    }

    override suspend fun fridgeScan(token: String, context: JsonObject, imageBase64: String): AiResponse {
        return client.post("$baseUrl/api/ai/fridge-scan") {
            authHeader(token)
            setBody(VisionRequest(context, imageBase64))
        }.body()
    }

    override suspend fun insights(token: String, context: JsonObject): AiResponse {
        return client.post("$baseUrl/api/ai/insights") {
            authHeader(token)
            setBody(InsightsRequest(context))
        }.body()
    }
}
