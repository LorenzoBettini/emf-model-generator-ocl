/*******************************************************************************
 * Copyright (c) 2026 Lorenzo Bettini and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.github.lorenzobettini.emfmodelgenerator.ocl;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelValidator;
import io.github.lorenzobettini.emfmodelgenerator.ocl.internal.CompleteOCLModelValidator;
import io.github.lorenzobettini.emfmodelgenerator.ocl.internal.OCLinEcoreModelValidator;

/**
 * Validates complete EMF object graphs after generation against configured OCL constraints.
 * Implementations validate candidates but neither direct generation nor repair invalid candidates.
 *
 * <p>OCL validation rejects diagnostics at {@link org.eclipse.emf.common.util.Diagnostic#WARNING}
 * and above because Eclipse OCL may report a false Boolean invariant as a warning. Ordinary
 * constraint violations have
 * {@link io.github.lorenzobettini.emfmodelgenerator.EMFValidationKind#VALIDATION_FAILURE} kind;
 * setup, parser, resolution, and evaluation failures have
 * {@link io.github.lorenzobettini.emfmodelgenerator.EMFValidationKind#VALIDATOR_FAILURE} kind.
 * Results retain the original diagnostic tree.</p>
 *
 * <p>As defined by the core validator contract, validating an empty root collection produces a
 * valid aggregate result. Null collections and null roots are rejected. A Complete OCL setup
 * failure remains a validator failure even when the supplied collection is empty.</p>
 *
 * <p>Instances own OCL session resources. Direct callers must close them, normally with
 * try-with-resources. Core generator factory-based operations create and close their validator
 * automatically.</p>
 *
 * <p>Validator instances are not thread-safe, consistently with the EMF {@link ResourceSet} and
 * object graphs on which they operate.</p>
 */
public interface OCLModelValidator extends EMFModelValidator {

	/**
	 * Creates a validation session for Pivot OCL constraints embedded in Ecore annotations.
	 *
	 * @param resourceSet the caller's resource set containing metamodels and generated models
	 * @return a new headless OCLinEcore validation session
	 * @throws NullPointerException if {@code resourceSet} is {@code null}
	 */
	static OCLModelValidator forOCLinEcore(ResourceSet resourceSet) {
		return new OCLinEcoreModelValidator(resourceSet);
	}

	/**
	 * Creates a validation session for constraints in an external Complete OCL document.
	 * The document is resolved in the caller's resource set, so its relative imports use the
	 * document URI as their base. Constraints complement the imported EPackages in memory and do
	 * not modify their Ecore annotations.
	 *
	 * @param resourceSet the caller's resource set containing metamodels and generated models
	 * @param oclURI URI of the external Complete OCL document
	 * @return a new headless Complete OCL validation session
	 * @throws NullPointerException if either argument is {@code null}
	 */
	static OCLModelValidator forCompleteOCL(ResourceSet resourceSet, URI oclURI) {
		return new CompleteOCLModelValidator(resourceSet, oclURI);
	}

	/** Releases constraint-source resources owned by this validation session. */
	@Override
	void close();
}
