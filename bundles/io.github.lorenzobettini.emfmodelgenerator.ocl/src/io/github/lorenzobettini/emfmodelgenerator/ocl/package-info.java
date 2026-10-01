/**
 * Public API for post-generation validation of complete EMF object graphs against configured OCL
 * constraints.
 * <p>
 * Validation reports whether selected roots are OCL-valid with respect to those constraints. It
 * uses the generic EMF Model Generator validation result and exception contracts. It does not
 * direct generation, retry generation, repair candidates, or prove satisfiability.
 */
package io.github.lorenzobettini.emfmodelgenerator.ocl;
