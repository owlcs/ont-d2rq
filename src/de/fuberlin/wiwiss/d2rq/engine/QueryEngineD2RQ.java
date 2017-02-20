package de.fuberlin.wiwiss.d2rq.engine;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jena.atlas.io.PrintUtils;

import org.apache.jena.query.Query;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.Transformer;
import org.apache.jena.sparql.algebra.optimize.TransformScopeRename;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.engine.Plan;
import org.apache.jena.sparql.engine.QueryEngineFactory;
import org.apache.jena.sparql.engine.QueryEngineRegistry;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingRoot;
import org.apache.jena.sparql.engine.main.QueryEngineMain;
import org.apache.jena.sparql.util.Context;

import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.map.Mapping;

import org.apache.jena.sparql.core.Substitute;

/**
 * An ARQ query engine for D2RQ-mapped graphs. Allows evaluation of SPARQL
 * queries (or programmatically created operator trees) over a D2RQ-mapped
 * graph.
 * 
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @author Herwig Leimer
 */
public class QueryEngineD2RQ extends QueryEngineMain {
	private static final Log log = LogFactory.getLog(QueryEngineD2RQ.class);

	private final Mapping mapping;
	private final Binding inputBinding;

	public QueryEngineD2RQ(GraphD2RQ graph, Query query) {
		this(graph, query, BindingRoot.create(), null);
	}

	public QueryEngineD2RQ(GraphD2RQ graph, Query query, Binding input, Context context) {
		super(query, DatasetGraphFactory.createOneGraph(graph), input, context);
		this.mapping = graph.getMapping();
		this.inputBinding = input;
	}

	public QueryEngineD2RQ(GraphD2RQ graph, Op op, Binding input, Context context) {
		super(op, DatasetGraphFactory.createOneGraph(graph), input, context);
		this.mapping = graph.getMapping();
		this.inputBinding = input;
	}

	@Override
	protected Op modifyOp(Op op) {
		// According to ARQ's {@link Optimize#rewrite()} source code,
		// this has to be done if no other ARQ optimizations are applied
		op = TransformScopeRename.transform(op);
		// ARQ is supposed to do this for us
		// but it happens too late -- we need it now
		op = Substitute.substitute(op, this.inputBinding);

		// TODO: Apply all or some of ARQ's standard transforms?
		// op = super.modifyOp(op);

		return translate(op);
	}

	/**
	 * Method for translating an operator-tree. Move filter conditions as far as
	 * possible down in the tree. In the optimal way, the filter conditions is
	 * the parent of an OpBGP.
	 */
	private Op translate(Op op) {
		if (log.isDebugEnabled()) {
			log.debug("Before translation:\n" + PrintUtils.toString(op));
		}
		// Shape filter expressions to maximize opportunities for pushing them
		// down
		op = Transformer.transformSkipService(new TransformFilterCNF(), op);
		// Try to move any filters as far down as possible
		op = PushDownOpFilterVisitor.transform(op);
		// Translate BGPs that have a filter immediately above them
		op = Transformer.transformSkipService(new TransformOpBGP(mapping, true), op);
		// Translate BGPs that don't have a filter
		op = Transformer.transformSkipService(new TransformOpBGP(mapping, false), op);

		if (log.isDebugEnabled()) {
			log.debug("After translation:\n" + PrintUtils.toString(op));
		}
		return op;
	}

	// Factory stuff
	private static QueryEngineFactory factory = new QueryEngineFactoryD2RQ();

	public static QueryEngineFactory getFactory() {
		return factory;
	}

	public static void register() {
		QueryEngineRegistry.addFactory(factory);
	}

	public static void unregister() {
		QueryEngineRegistry.removeFactory(factory);
	}

	private static class QueryEngineFactoryD2RQ implements QueryEngineFactory {
		public boolean accept(Query query, DatasetGraph dataset, Context context) {
			return dataset.getDefaultGraph() instanceof GraphD2RQ;
		}

		public Plan create(Query query, DatasetGraph dataset,
				Binding inputBinding, Context context) {
			return new QueryEngineD2RQ((GraphD2RQ) dataset.getDefaultGraph(),
					query, inputBinding, context).getPlan();
		}

		public boolean accept(Op op, DatasetGraph dataset, Context context) {
			return dataset.getDefaultGraph() instanceof GraphD2RQ;
		}

		public Plan create(Op op, DatasetGraph dataset, Binding inputBinding,
				Context context) {
			return new QueryEngineD2RQ((GraphD2RQ) dataset.getDefaultGraph(),
					op, inputBinding, context).getPlan();
		}
	}
}
