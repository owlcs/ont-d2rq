package de.fuberlin.wiwiss.d2rq.examples;

import org.apache.jena.assembler.Assembler;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.FileManager;

public class AssemblerExample {

    public static void main(String[] args) {
        // Load assembler specification from file
        Model assemblerSpec = FileManager.get().loadModel(TestConstants.ASSEMBLER);

        // Get the model resource
        Resource modelSpec = assemblerSpec.createResource(assemblerSpec.expandPrefix(":myModel"));

        // Assemble a model
        Model m = Assembler.general.openModel(modelSpec);

        // Write it to System.out
        // jena 3.1.0: java.lang.StackOverflowError in case no format specified.
        //m.write(System.out);
        m.write(System.out, "ttl");

        m.close();
    }
}
