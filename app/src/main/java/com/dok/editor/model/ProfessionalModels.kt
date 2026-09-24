package com.dok.editor.model

import java.util.UUID

enum class BlendMode { NORMAL, ADD, SCREEN, MULTIPLY, OVERLAY, SOFT_LIGHT, HARD_LIGHT, DIFFERENCE }
enum class TrackCompositeMode { NORMAL, ADD, SCREEN, MULTIPLY }

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
)

data class ProfessionalProjectSettings(
    val pixelAspectRatio: Float = 1f,
    val audioSampleRate: Int = 48_000,
    val audioChannels: Int = 2,
    val colorSpace: String = "Rec.709",
    val transferFunction: String = "BT.1886",
    val bitDepth: Int = 8,
    val startTimecodeUs: Long = 0L,
    val dropFrame: Boolean = false
)

data class Marker(
    val id: String = UUID.randomUUID().toString(),
    val timeUs: Long,
    val name: String = "",
    val colorHex: String = "#FFD54F",
    val durationUs: Long = 0L,
    val note: String = ""
)

data class MediaAsset(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val durationUs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 0f,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val codec: String = "",
    val sizeBytes: Long = 0L,
    val isOffline: Boolean = false,
    val proxyUri: String? = null
)

data class ExternalEffectAsset(val id: String = UUID.randomUUID().toString(), val name: String, val uri: String, val kind: String = "effect", val mimeType: String = "", val metadataJson: String = "")

data class EffectPreset(val id: String = UUID.randomUUID().toString(), val name: String, val effectType: String, val intensity: Float = 1f, val parameters: Map<String, Float> = emptyMap(), val sourceUri: String? = null)

data class AudioEffectConfig(
    val type: String,
    val enabled: Boolean = true,
    val parameters: Map<String, Float> = emptyMap()
)

data class Node(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val parameters: Map<String, Float> = emptyMap()
)

data class NodeConnection(
    val fromNodeId: String,
    val fromPort: String,
    val toNodeId: String,
    val toPort: String
)

data class NodeGraph(
    val id: String = UUID.randomUUID().toString(),
    val nodes: List<Node> = emptyList(),
    val connections: List<NodeConnection> = emptyList()
)
