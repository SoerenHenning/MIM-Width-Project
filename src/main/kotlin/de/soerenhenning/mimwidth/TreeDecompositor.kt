package de.soerenhenning.mimwidth

import com.google.common.graph.EndpointPair
import com.google.common.graph.Graph
import com.google.common.graph.GraphBuilder
import com.google.common.graph.Graphs
import de.soerenhenning.mimwidth.graphs.createCut
import java.util.*

class TreeDecompositor<T>(
        private val graph: Graph<T>,
        private val reducingTieBreaker: (Graph<T>, Collection<T>) -> Iterable<T> = ReducingTieBreakers::chooseMaxDegree,
        private val finalTieBreaker: (Graph<T>, Collection<T>) -> T = FinalTieBreakers::chooseMaxNeighboursDegree,
        private val randomRepetitions: Int = 5,
        private val random: Random = Random()
) {

    fun compute(): TreeDecomposition<T> {
        val tree = GraphBuilder.undirected().build<Set<T>>()
        val cutMimValues = hashMapOf<Set<T>, Int>()
        val allVertices = graph.nodes().toMutableSet()

        if (allVertices.isEmpty()) {
            return TreeDecomposition(tree, emptyMap())
        }

        var treeParent = allVertices.toSet() // Create a read-only copy
        tree.addNode(treeParent)

        while(allVertices.size > 1) {
            val (vertex, mim) = chooseVertex(allVertices)
            allVertices.remove(vertex)
            val remainingVertices = allVertices.toSet() // Create a read-only copy

            // Add S and V/S as children
            tree.putEdge(treeParent, setOf(vertex))
            cutMimValues.put(setOf(vertex), if (graph.degree(vertex) == 0) 0 else 1)
            tree.putEdge(treeParent, remainingVertices)
            cutMimValues.put(remainingVertices, mim)

            treeParent = remainingVertices
        }

        return TreeDecomposition(tree, cutMimValues)
    }


    // Given Graph G=(V,E) and V' <= V
    // Choose S in V' s.t. max{mim(S), mim(V'-S}} is small
    // Here S is a vertex set with exact one vertex
    private fun chooseVertex(vertices: Set<T>): Pair<T, Int> {
        if (vertices.isEmpty()) {
            throw IllegalArgumentException("Graph must have at least one vertex")
        }
        var smallestMim = Int.MAX_VALUE
        var smallestMimVertices = mutableSetOf<T>() // Will be definitely overwritten since (|V| > 1)
        for(vertex in vertices) {
            // mim(S) = if isolated 0 else 1
            val mimS = if (graph.degree(vertex) == 0) 0 else 1
            // mim(V-S) = <use heuristic>
            val cut = graph.createCut(vertices.minus(vertex))
            val mimVminusS = estimateMim(cut).size
            val maxMim = maxOf(mimS, mimVminusS)
            if (maxMim < smallestMim) {
                smallestMim = maxMim
                smallestMimVertices = mutableSetOf(vertex)
            } else if (maxMim == smallestMim) {
                smallestMimVertices.add(vertex)
            }
        }
        val subgraph = Graphs.inducedSubgraph(graph, vertices)
        return Pair(breakTie(subgraph, smallestMimVertices), smallestMim)
    }

    private fun breakTie(graph: Graph<T>, vertices: Collection<T>): T {
        return if (vertices.size == 1) {
            vertices.first()
        } else {
            val remainingVertices = reducingTieBreaker(graph, vertices).toSet()
            if (remainingVertices.size == 1) {
                remainingVertices.first()
            } else {
                finalTieBreaker(graph, remainingVertices)
            }
        }
    }

    /**
     * This method is a heuristic to compute the maximum induced matching (MIM)
     * for a given graph.
     *
     * It uses a simply greedy approach by adding consequently the edge to the
     * induced matching whose endpoints have the highest degree. Thus, it only
     * finds local optima and there is no guaranty about the quality of the
     * solution.
     */
    private fun estimateMim(graph: Graph<T>) : Set<EndpointPair<T>> {
        var maximumInducedMatching = emptySet<EndpointPair<T>>()
        for (i in 1..randomRepetitions) {
            val remainingGraph = Graphs.copyOf(graph)
            val temporaryMaximumInducedMatching = HashSet<EndpointPair<T>>()
            while (remainingGraph.edges().isNotEmpty()) {
                var edgesWithLowestDegrees = mutableListOf<EndpointPair<T>>()
                var lowestDegree = Int.MAX_VALUE
                for (edge in remainingGraph.edges()) {
                    val degree = edge.asSequence().map { x -> remainingGraph.degree(x) }.sum()
                    if (degree < lowestDegree) {
                        lowestDegree = degree
                        edgesWithLowestDegrees = mutableListOf(edge)
                    } else if (degree == lowestDegree) {
                        edgesWithLowestDegrees.add(edge)
                    }
                }
                val selectedEdge = if (edgesWithLowestDegrees.size == 1) {
                    edgesWithLowestDegrees.first()
                } else {
                    breakTieRandomly(edgesWithLowestDegrees)
                }

                remainingGraph.removeEdge(selectedEdge.nodeU(), selectedEdge.nodeV())
                for (node in selectedEdge) {
                    for (adjacentNode in remainingGraph.adjacentNodes(node).toList()) {
                        remainingGraph.removeEdge(node, adjacentNode)
                        for (adjacentAdjacentNode in remainingGraph.adjacentNodes(adjacentNode).toList()) {
                            remainingGraph.removeEdge(adjacentNode, adjacentAdjacentNode)
                        }
                    }
                }

                temporaryMaximumInducedMatching.add(selectedEdge)
            }
            if (temporaryMaximumInducedMatching.size > maximumInducedMatching.size) {
                maximumInducedMatching = temporaryMaximumInducedMatching
            }
        }
        return maximumInducedMatching
    }

    private fun <S> breakTieRandomly(edges: Collection<S>) : S {
        val x = this.random.nextInt(edges.size)
        return edges.asSequence().drop(x).first()
    }

}

