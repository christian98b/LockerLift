package com.lockerlift.core.sync

import android.content.Context
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class WearableDataLayerManager(private val context: Context) {

    private val dataClient = Wearable.getDataClient(context)
    private val channelClient = Wearable.getChannelClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun getConnectedNodes(): List<Node> {
        return runCatching {
            nodeClient.connectedNodes.await()
        }.getOrDefault(emptyList())
    }

    suspend fun syncEquipmentCatalog(catalogJson: String): Boolean {
        return runCatching {
            val request = PutDataMapRequest.create(SyncConstants.PATH_EQUIPMENT_CATALOG).apply {
                dataMap.putString("payload", catalogJson)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
            true
        }.getOrDefault(false)
    }

    suspend fun syncTemplates(templatesJson: String): Boolean {
        return runCatching {
            val request = PutDataMapRequest.create(SyncConstants.PATH_WORKOUT_TEMPLATES).apply {
                dataMap.putString("payload", templatesJson)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
            true
        }.getOrDefault(false)
    }

    suspend fun sendWorkoutPayloadViaChannel(nodeId: String, payloadJson: String): Boolean {
        return runCatching {
            val channel = channelClient.openChannel(nodeId, SyncConstants.PATH_WORKOUT_CHANNEL).await()
            val outputStream = channelClient.getOutputStream(channel).await()
            OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payloadJson)
                writer.flush()
            }
            channelClient.close(channel).await()
            true
        }.getOrDefault(false)
    }

    suspend fun sendAcknowledgment(nodeId: String, sessionId: String): Boolean {
        return runCatching {
            messageClient.sendMessage(
                nodeId,
                SyncConstants.PATH_WORKOUT_ACK,
                sessionId.toByteArray(StandardCharsets.UTF_8)
            ).await()
            true
        }.getOrDefault(false)
    }

    suspend fun sendWorkoutDelete(nodeId: String, sessionId: String): Boolean {
        return runCatching {
            messageClient.sendMessage(
                nodeId,
                SyncConstants.PATH_WORKOUT_DELETE,
                sessionId.toByteArray(StandardCharsets.UTF_8)
            ).await()
            true
        }.getOrDefault(false)
    }
}
