package de.fuberlin.wiwiss.d2rq.find;

import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.iterator.QueryIter;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NiceIterator;

import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;

/**
 * Wraps a {@link QueryIter} over bindings with three s/p/o variables
 * (see {@link TripleRelation}) as an iterator over {@link Triple}s.
 * Also implements a {@link #cancel()} method.
 * 
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class TripleQueryIter extends NiceIterator<Triple> {
	
	public static ExtendedIterator<Triple> create(QueryIter wrapped) {
		return new TripleQueryIter(wrapped);
	}
	
	private final QueryIter wrapped;

	private TripleQueryIter(QueryIter wrapped) {
		this.wrapped = wrapped;
	}

	public boolean hasNext() {
		return wrapped.hasNext();
	}

	public Triple next() {
		Binding b = wrapped.next();
		return new Triple(
				b.get(TripleRelation.SUBJECT), 
				b.get(TripleRelation.PREDICATE), 
				b.get(TripleRelation.OBJECT));
	}

	public void close() {
		wrapped.close();
	}
	
	/**
	 * Cancels query execution. Can be called asynchronously.
	 */
	public void cancel() {
		wrapped.cancel();
	}
}
