/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.healthconnectsample.data

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources.NotFoundException
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.io.InvalidObjectException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlin.reflect.KClass

// The minimum android level that can use Health Connect
const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.O_MR1

/** Demonstrates reading and writing from Health Connect. */
class HealthConnectManager(private val context: Context) {
    internal val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }



    var availability = mutableStateOf(SDK_UNAVAILABLE)
        private set

    fun checkAvailability() {
        availability.value = HealthConnectClient.getSdkStatus(context)
        Log.i(TAG, "checkAvailability: ${availability.value}")
    }

    init {
        checkAvailability()
    }

    /**
     * Determines whether all the specified permissions are already granted. It is recommended to
     * call [PermissionController.getGrantedPermissions] first in the permissions flow, as if the
     * permissions are already granted then there is no need to request permissions via
     * [PermissionController.createRequestPermissionResultContract].
     */
    suspend fun hasAllPermissions(permissions: Set<String>): Boolean {
        return healthConnectClient.permissionController.getGrantedPermissions()
            .containsAll(permissions)
    }

    fun requestPermissionsActivityContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    suspend fun revokeAllPermissions() {
        healthConnectClient.permissionController.revokeAllPermissions()
    }

    fun openHealthConnectPermissionsScreen(context: Context) {
        try {
            val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            }

            // Check if the intent can be resolved
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            if (resolveInfo != null) {
                context.startActivity(intent)
                Toast.makeText(context, "Opening Health Connect permissions", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Health Connect app not installed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("HealthConnect", "Error opening Health Connect permissions screen", e)
            Toast.makeText(context, "Failed to open Health Connect permissions", Toast.LENGTH_SHORT).show()
        }
    }


    /**
     * Returns the weekly average of [WeightRecord]s.
     */
    suspend fun computeWeeklyAverage(start: Instant, end: Instant): Mass? {
        val request = AggregateRequest(
            metrics = setOf(WeightRecord.WEIGHT_AVG),
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        val response = healthConnectClient.aggregate(request)
        return response[WeightRecord.WEIGHT_AVG]
    }

    /**
     * Deletes a [WeightRecord]s.
     */
    suspend fun deleteWeightInput(uid: String) {
        healthConnectClient.deleteRecords(
            WeightRecord::class,
            recordIdsList = listOf(uid),
            clientRecordIdsList = emptyList()
        )
    }

    /**
     * Obtains a changes token for the specified record types.
     */
    suspend fun getChangesToken(dataTypes: Set<KClass<out Record>>): String {
        val request = ChangesTokenRequest(dataTypes)
        return healthConnectClient.getChangesToken(request)
    }

    /**
     * Creates a [Flow] of change messages, using a changes token as a start point. The flow will
     * terminate when no more changes are available, and the final message will contain the next
     * changes token to use.
     */
    suspend fun getChanges(token: String): Flow<ChangesMessage> = flow {
        var nextChangesToken = token
        do {
            val response = healthConnectClient.getChanges(nextChangesToken)
            if (response.changesTokenExpired) {
                // As described here: https://developer.android.com/guide/health-and-fitness/health-connect/data-and-data-types/differential-changes-api
                // tokens are only valid for 30 days. It is important to check whether the token has
                // expired. As well as ensuring there is a fallback to using the token (for example
                // importing data since a certain date), more importantly, the app should ensure
                // that the changes API is used sufficiently regularly that tokens do not expire.
                throw IOException("Changes token has expired")
            }
            emit(ChangesMessage.ChangeList(response.changes))
            nextChangesToken = response.nextChangesToken
        } while (response.hasMore)
        emit(ChangesMessage.NoMoreChanges(nextChangesToken))
    }

    /**
     * Convenience function to fetch a time-based record and return series data based on the record.
     * Record types compatible with this function must be declared in the
     * [com.example.healthconnectsample.presentation.screen.recordlist.RecordType] enum.
     */
    suspend fun fetchSeriesRecordsFromUid(recordType: KClass<out Record>, uid: String, seriesRecordsType: KClass<out Record>): List<Record> {
        val recordResponse = healthConnectClient.readRecord(recordType, uid)
        // Use the start time and end time from the session, for reading raw and aggregate data.
        val timeRangeFilter =
            when (recordResponse.record) {
                // Change to use series record instead
                is ExerciseSessionRecord -> {
                    val record = recordResponse.record as ExerciseSessionRecord
                    TimeRangeFilter.between(startTime = record.startTime, endTime = record.endTime)
                }
                is SleepSessionRecord -> {
                    val record = recordResponse.record as SleepSessionRecord
                    TimeRangeFilter.between(startTime = record.startTime, endTime = record.endTime)
                }
                else -> {
                    throw InvalidObjectException("Record with unregistered data type returned")
                }
            }

        // Limit the data read to just the application that wrote the session. This may or may not
        // be desirable depending on the use case: In some cases, it may be useful to combine with
        // data written by other apps.
        val dataOriginFilter = setOf(recordResponse.record.metadata.dataOrigin)
        val request =
            ReadRecordsRequest(
                recordType = seriesRecordsType,
                dataOriginFilter = dataOriginFilter,
                timeRangeFilter = timeRangeFilter)
        return healthConnectClient.readRecords(request).records
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildHeartRateSeries(
        sessionStartTime: ZonedDateTime,
        sessionEndTime: ZonedDateTime
    ): HeartRateRecord {
        val samples = mutableListOf<HeartRateRecord.Sample>()
        var time = sessionStartTime
        while (time.isBefore(sessionEndTime)) {
            samples.add(
                HeartRateRecord.Sample(
                    time = time.toInstant(), beatsPerMinute = (80 + Random.nextInt(80)).toLong()))
            time = time.plusSeconds(30)
        }
        return HeartRateRecord(
            metadata = Metadata.manualEntry(),
            startTime = sessionStartTime.toInstant(),
            startZoneOffset = sessionStartTime.offset,
            endTime = sessionEndTime.toInstant(),
            endZoneOffset = sessionEndTime.offset,
            samples = samples)
    }

    fun isFeatureAvailable(feature: Int): Boolean{
        return healthConnectClient
            .features
            .getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    // Represents the two types of messages that can be sent in a Changes flow.
    sealed class ChangesMessage {
        data class NoMoreChanges(val nextChangesToken: String) : ChangesMessage()

        data class ChangeList(val changes: List<Change>) : ChangesMessage()
    }
}
