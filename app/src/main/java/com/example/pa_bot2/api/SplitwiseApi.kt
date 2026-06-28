package com.example.pa_bot2.api

import com.example.pa_bot2.model.SplitwiseGroup
import com.example.pa_bot2.model.SplitwiseUser
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

@Serializable
data class GetGroupsResponse(
    val groups: List<SplitwiseGroup>
)

@Serializable
data class GetGroupResponse(
    val group: SplitwiseGroup
)

@Serializable
data class GetCurrentUserResponse(
    val user: SplitwiseUser
)

interface SplitwiseApi {
    @GET("get_groups")
    suspend fun getGroups(
        @Header("Authorization") authHeader: String
    ): GetGroupsResponse

    @GET("get_group/{id}")
    suspend fun getGroup(
        @Header("Authorization") authHeader: String,
        @Path("id") groupId: Long
    ): GetGroupResponse

    @GET("get_current_user")
    suspend fun getCurrentUser(
        @Header("Authorization") authHeader: String
    ): GetCurrentUserResponse

    @retrofit2.http.POST("create_expense")
    @retrofit2.http.FormUrlEncoded
    suspend fun createExpense(
        @Header("Authorization") authHeader: String,
        @retrofit2.http.FieldMap params: Map<String, String>
    ): CreateExpenseResponse

    companion object {
        const val BASE_URL = "https://secure.splitwise.com/api/v3.0/"
    }
}

@Serializable
data class CreateExpenseResponse(
    val expenses: List<SplitwiseExpense>? = null,
    val errors: Map<String, List<String>>? = null
)

@Serializable
data class SplitwiseExpense(
    val id: Long,
    val description: String,
    val cost: String
)
