package com.dok.editor.engine.nodes
import com.dok.editor.model.Node
import com.dok.editor.model.NodeGraph
data class NodeFrame(val rgba:FloatArray,val width:Int,val height:Int){init{require(rgba.size==width*height*4)}}
fun interface NodeOperator{fun process(inputs:List<NodeFrame>,node:Node):NodeFrame}
class NodeGraphExecutor(private val operators:Map<String,NodeOperator>){
 fun execute(graph:NodeGraph,input:NodeFrame):NodeFrame{val remaining=graph.nodes.toMutableList();val done=HashMap<String,NodeFrame>();while(remaining.isNotEmpty()){val n=remaining.firstOrNull{x->graph.connections.none{it.toNodeId==x.id&&remaining.any{y->y.id==it.fromNodeId}}}?:remaining.first();remaining.remove(n);val ins=graph.connections.filter{it.toNodeId==n.id}.mapNotNull{done[it.fromNodeId]};done[n.id]=if(ins.isEmpty())input else operators[n.type]?.process(ins,n)?:ins.first()};return done[graph.nodes.lastOrNull()?.id]?:input}
}
