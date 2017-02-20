package de.fuberlin.wiwiss.d2rq.examples;

import org.apache.jena.assembler.Assembler;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.FileManager;

public class AssemblerExample {

	public static void main(String[] args) {
		// Load assembler specification from file
		Model assemblerSpec = FileManager.get().loadModel("doc/example/assembler.ttl");
		
		// Get the model resource
		Resource modelSpec = assemblerSpec.createResource(assemblerSpec.expandPrefix(":myModel"));
		
		// Assemble a model
		Model m = Assembler.general.openModel(modelSpec);
		
		// Write it to System.out
		m.write(System.out);

		m.close();
	}
}
