package com.dok.editor.engine.nodes

import com.dok.editor.model.Node
import com.dok.editor.model.NodeGraph

data class NodeFrame(val rgba: FloatArray, val width: Int, val height: Int) {
    init { require(rgba.size == width * height * 4) }
}

fun interface NodeOperator {
    fun process(inputs: List<NodeFrame>, node: Node): NodeFrame
}

class NodeGraphExecutor(private val operators: Map<String, NodeOperator>) {
    fun execute(graph: NodeGraph, input: NodeFrame): NodeFrame {
        val remaining = graph.nodes.toMutableList()
        val done = HashMap<String, NodeFrame>()
        val pendingIds = graph.nodes.mapTo(HashSet()) { it.id }

        while (remaining.isNotEmpty()) {
            val nextNode = remaining.firstOrNull { node ->
                graph.connections.none { connection ->
                    connection.toNodeId == node.id && connection.fromNodeId in pendingIds
                }
            } ?: throw IllegalArgumentException("Node graph contains a cycle")

            remaining.remove(nextNode)
            pendingIds.remove(nextNode.id)

            val inputs = graph.connections
                .filter { it.toNodeId == nextNode.id }
                .sortedBy { it.fromNodeId }
                .mapNotNull { done[it.fromNodeId] }

            val output = if (inputs.isEmpty()) {
                input
            } else {
                val operator = operators[nextNode.type]
                    ?: throw IllegalArgumentException("No node operator registered for '" + nextNode.type + "'")
                operator.process(inputs, nextNode)
            }
            done[nextNode.id] = output
        }

        return done[graph.nodes.lastOrNull()?.id] ?: input
    }
}
